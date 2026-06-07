package com.example.myapplication.user.chat

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.user.UserStartActivity
import com.example.myapplication.R
import com.example.myapplication.common.model.UserChatLog
import com.example.myapplication.common.model.UserChatMessage
import com.example.myapplication.config.AppConfig
import com.example.myapplication.core.manager.TTSManager
import com.example.myapplication.core.manager.VoskWakeWordManager
import com.example.myapplication.user.schedule.data.ScheduleRepository
import com.example.myapplication.user.schedule.service.ScheduleAlarmReceiver
import com.example.myapplication.user.schedule.service.ScheduleManager
import com.google.gson.Gson
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class UserChatActivity : AppCompatActivity(), ChatAdapter.OnSuggestionClickListener {

    private val SERVER_URL = AppConfig.BASE_URL + "api/v1/question/analyze"
    private lateinit var USER_ID: String
    private var USER_IDX: Int = 1

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val chatList: MutableList<UserChatMessage> = mutableListOf()
    private val conversationHistory: MutableList<UserChatLog> = mutableListOf()
    private val gson = Gson()

    private lateinit var adapter: ChatAdapter
    private lateinit var rvChat: RecyclerView
    private lateinit var loadingBar: ProgressBar
    private lateinit var btnMic: View
    private lateinit var ivBear: ImageView

    private enum class BearMood { BASIC, WORRY, PRAISE }

    private lateinit var voskManager: VoskWakeWordManager
    private lateinit var ttsManager: TTSManager
    private lateinit var scheduleManager: ScheduleManager

    private var isRecording = false
    private var voiceMsgIndex = -1
    private var shouldLaunchCamera = false
    private var speechRecognizer: SpeechRecognizer? = null
    private var sttFailCount = 0
    private var awaitingFallbackButton = false
    private val mainHandler = Handler(Looper.getMainLooper())

    private val refreshHandler = Handler(Looper.getMainLooper())
    private val refreshRunnable = object : Runnable {
        override fun run() {
            if (::USER_ID.isInitialized) {
                scheduleManager.syncSchedulesFromDB(USER_ID, USER_IDX)
                Log.d("ScheduleRefresh", "30분 주기 자동 일정 갱신 완료")
            }
            refreshHandler.postDelayed(this, 30 * 60 * 1000L) // 30분마다 반복
        }
    }

    private var userName = ""
    private var userSpecialNote = ""
    private var whitelistTaskNames = listOf<String>()

    private var scheduleTitle = ""
    private var scheduleSteps = listOf<String>()
    private var scheduleNote = ""
    private var currentScheduleId = -1
    private var currentStepIndex = -1
    private val isScheduleMode get() = currentStepIndex >= 0

    private val scheduleReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val scheduleId = intent.getStringExtra("schedule_id") ?: return
            val title = intent.getStringExtra("schedule_title") ?: "일정"
            showScheduleDialog(scheduleId, title)
        }
    }

    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val bitmap = result.data?.extras?.get("data") as? Bitmap
            bitmap?.let {
                addMsg(null, UserChatMessage.TYPE_MINE, true, it, null)
                uploadToServer(it, null, null)
            }
        }
        resetToIdleState()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_chat)

        val prefs = getSharedPreferences("nungil_prefs", Context.MODE_PRIVATE)
        val userId = prefs.getString("user_id", null)
        if (userId == null) {
            startActivity(Intent(this, UserStartActivity::class.java))
            finish()
            return
        }
        USER_ID = userId
        USER_IDX = prefs.getInt("user_idx", 1)

        scheduleManager = ScheduleManager(this)
        ScheduleManager.createNotificationChannel(this)
        scheduleManager.startPeriodicCheck()

        setupUI()
        setupManagers()
        checkPermissions()

        val welcomeMsg = "안녕! 나는 똘똘이야. 뭐든 물어봐!"
        addMsg(welcomeMsg, UserChatMessage.TYPE_OTHER, false, null, mutableListOf("오늘 날씨 어때?", "넌 누구니?"))
        conversationHistory.add(UserChatLog("AI", welcomeMsg))
        mainHandler.postDelayed({ ttsManager.speak(welcomeMsg) }, 1000)

        loadUserDataFromDB()
        handleScheduleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleScheduleIntent(intent)
    }

    private fun loadUserDataFromDB() {
        Thread {
            val userInfo = ScheduleRepository.fetchUserInfo(USER_ID, USER_IDX)
            runOnUiThread {
                userName = userInfo.userName
                userSpecialNote = userInfo.specialNote
                whitelistTaskNames = userInfo.whitelistTaskNames
                // 이름 받은 후 환영 메시지 업데이트
                val greet = if (userName.isNotBlank()) "${userName}야, 반가워! 나는 똘똘이야. 뭐든 물어봐!" else "반가워! 나는 똘똘이야. 뭐든 물어봐!"
                if (chatList.isNotEmpty()) {
                    chatList[0].content = greet
                    adapter.notifyItemChanged(0)
                    ttsManager.speak(greet)
                }
            }
        }.start()
        // 최초 1회 즉시 실행
        scheduleManager.syncSchedulesFromDB(userId = USER_ID, userIdx = USER_IDX)
    }

    private fun setupUI() {
        rvChat = findViewById(R.id.rvChat)
        loadingBar = findViewById(R.id.loadingBar)
        btnMic = findViewById(R.id.btnNext)
        ivBear = findViewById(R.id.ivBear)

        adapter = ChatAdapter(chatList, this)
        rvChat.layoutManager = LinearLayoutManager(this)
        rvChat.adapter = adapter

        btnMic.setOnClickListener {
            if (loadingBar.visibility == View.VISIBLE) return@setOnClickListener

            when {
                ttsManager.isSpeaking() -> {
                    ttsManager.stop()
                    handleTtsEndFlow()
                }
                isRecording -> {
                    speechRecognizer?.cancel()
                    resetToIdleState()
                }
                else -> {
                    startRecordingFlow(manual = true)
                }
            }
        }
    }

    private fun handleTtsEndFlow() {
        runOnUiThread {
            updateMicButtonUI(forceMic = true)
            if (shouldLaunchCamera) {
                shouldLaunchCamera = false
                mainHandler.postDelayed({ openBackCamera() }, 300)
            } else if (isScheduleMode && !awaitingFallbackButton) {
                // 일정 모드: TTS 끝나면 자동으로 음성 인식 시작 (fallback 버튼 대기 중엔 안함)
                if (!isRecording && loadingBar.visibility != View.VISIBLE) {
                    mainHandler.postDelayed({ startAutoScheduleListening() }, 600)
                }
            } else {
                if (!isRecording && loadingBar.visibility != View.VISIBLE) voskManager.startListening()
            }
        }
    }

    private fun startAutoScheduleListening() {
        if (!isScheduleMode || isRecording || loadingBar.visibility == View.VISIBLE) return
        voskManager.stopListening()
        isRecording = true
        updateMicButtonUI()
        // 기존 메시지 재사용, 없으면 새로 추가
        if (voiceMsgIndex == -1 || voiceMsgIndex >= chatList.size) {
            addMsg("🎤 듣고 있습니다...", UserChatMessage.TYPE_MINE, false, null, null)
            voiceMsgIndex = chatList.size - 1
        } else {
            updateVoiceStatus("🎤 듣고 있습니다...")
        }
        startSpeechRecognition(scheduleMode = true)
    }

    private fun updateMicButtonUI(forceMic: Boolean = false) {
        runOnUiThread {
            val mic = btnMic as? ImageView ?: return@runOnUiThread
            val (iconRes, bgColor) = when {
                ttsManager.isSpeaking() && !forceMic -> android.R.drawable.ic_media_next to Color.TRANSPARENT
                isRecording && !forceMic -> android.R.drawable.ic_menu_close_clear_cancel to Color.parseColor("#FF5252")
                else -> android.R.drawable.ic_btn_speak_now to Color.TRANSPARENT
            }
            mic.setImageResource(iconRes)
            btnMic.setBackgroundColor(bgColor)
        }
    }

    private fun setupManagers() {
        ttsManager = TTSManager(this)
        ttsManager.onStatusListener = { isSpeaking ->
            if (!isSpeaking) handleTtsEndFlow() else updateMicButtonUI(forceMic = false)
        }

        voskManager = VoskWakeWordManager(this, "[\"똘똘\", \"똘똘아\"]", object : VoskWakeWordManager.WakeWordListener {
            override fun onKeywordDetected() {
                if (loadingBar.visibility != View.VISIBLE && !isRecording && !ttsManager.isSpeaking())
                    startRecordingFlow(manual = false)
            }
            override fun onModelLoaded() { voskManager.startListening() }
            override fun onModelLoadFail() { Log.e("VOSK", "Load Fail") }
        })
    }

    private fun uploadToServer(newBitmap: Bitmap?, voice: File?, text: String?) {
        runOnUiThread {
            loadingBar.visibility = View.VISIBLE
            setBearMood(BearMood.WORRY)
            voskManager.stopListening()
        }

        // 히스토리에 현재 질문 추가 (순수 대화 내용만)
        if (text != null) conversationHistory.add(UserChatLog("user", text))

        val builder = MultipartBody.Builder().setType(MultipartBody.FORM)
        val textMt = "text/plain; charset=utf-8".toMediaTypeOrNull()

        // historyJson: 순수 대화 히스토리 JSON만 전송 (시스템 정보 없이)
        builder.addFormDataPart("userId",      null, USER_ID.toRequestBody(textMt))
        builder.addFormDataPart("historyJson", null, gson.toJson(conversationHistory).toRequestBody(textMt))
        builder.addFormDataPart("userContext", null, buildUserContext().toRequestBody(textMt))
        // textPrompt: 질문 텍스트만 전송 (컨텍스트 중복 없이)
        text?.let { builder.addFormDataPart("textPrompt", null, it.toRequestBody(textMt)) }

        // mode 전송
        val mode = if (isScheduleMode) "schedule" else "chat"
        val urlBuilder = SERVER_URL.toHttpUrl().newBuilder().apply {
            addQueryParameter("mode", mode)
            if (isScheduleMode) {
                addQueryParameter("scheduleTitle", scheduleTitle)
                addQueryParameter("currentStep", scheduleSteps.getOrElse(currentStepIndex) { scheduleTitle })
                addQueryParameter("stepIndex", currentStepIndex.toString())
                addQueryParameter("totalSteps", scheduleSteps.size.toString())
                addQueryParameter("specialNote", "$userSpecialNote ${scheduleNote}".trim())
                addQueryParameter("stepsJson", gson.toJson(scheduleSteps))
            }
        }

        newBitmap?.let { bitmap ->
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
            builder.addFormDataPart("imageFile", "capture.jpg", stream.toByteArray().toRequestBody("image/jpeg".toMediaTypeOrNull()))
        }
        voice?.let { if (it.exists()) builder.addFormDataPart("voiceFile", it.name, it.asRequestBody("audio/m4a".toMediaTypeOrNull())) }

        val request = Request.Builder().url(urlBuilder.build()).post(builder.build()).build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("ServerCheck", "네트워크 오류 발생: ${e.message}")
                runOnUiThread { loadingBar.visibility = View.GONE; setBearMood(BearMood.BASIC); resetToIdleState() }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: ""
                Log.d("ServerCheck", "── 서버 응답 수신 ──\n코드: ${response.code}\n본문: $body\n──────────────────")

                runOnUiThread {
                    loadingBar.visibility = View.GONE
                    setBearMood(BearMood.BASIC)
                    if (response.isSuccessful) {
                        parseServerResponse(body)
                    } else {
                        Log.e("ServerCheck", "서버 응답 실패: ${response.code}")
                        resetToIdleState()
                    }
                }
            }
        })
    }

    private fun parseServerResponse(json: String) {
        try {
            val root = JSONObject(json)
            if (root.optString("status") == "SUCCESS") {
                val result = root.getJSONObject("result")
                shouldLaunchCamera = result.optBoolean("photoRequest", false)

                val transcribed = result.optString("transcribedText", "")
                if (transcribed.isNotEmpty()) {
                    updateVoiceStatus(transcribed)
                    conversationHistory.add(UserChatLog("user", transcribed))
                }

                val answer = result.optString("answer", "")
                conversationHistory.add(UserChatLog("AI", answer))

                val suggestArray = result.optJSONArray("suggestedQuestions")
                val suggests = mutableListOf<String?>()
                if (suggestArray != null) {
                    for (i in 0 until suggestArray.length()) suggests.add(suggestArray.getString(i))
                }

                val stepComplete = result.optBoolean("stepComplete", false)
                if (isScheduleMode) {
                    if (stepComplete) {
                        // AI가 완료 감지 → 자동으로 다음 단계
                        mainHandler.postDelayed({ proceedToNextStep() }, 1500)
                    } else {
                        val nextLabel = if (scheduleSteps.isNotEmpty() && currentStepIndex + 1 < scheduleSteps.size) "다음 단계로" else "완료"
                        suggests.add(0, nextLabel)
                    }
                }

                if (currentScheduleId > 0) ScheduleRepository.logQuestion(currentScheduleId.toLong())

                addMsg(answer, UserChatMessage.TYPE_OTHER, false, null, suggests)
                ttsManager.speak(answer)
                Log.d("ServerCheck", "파싱 및 화면 업데이트 성공")
            } else {
                Log.e("ServerCheck", "서버 status가 SUCCESS가 아님: ${root.optString("status")}")
                resetToIdleState()
            }
        } catch (e: Exception) {
            Log.e("ServerCheck", "JSON 파싱 중 에러 발생: ${e.message}")
            e.printStackTrace()
            resetToIdleState()
        }
    }

    private fun startRecordingFlow(manual: Boolean) {
        voskManager.stopListening()
        if (!manual) {
            ttsManager.speak("네, 말씀하세요.")
            mainHandler.postDelayed({
                if (!isRecording && loadingBar.visibility != View.VISIBLE) {
                    isRecording = true
                    updateMicButtonUI()
                    addMsg("🎤 듣고 있습니다...", UserChatMessage.TYPE_MINE, false, null, null)
                    voiceMsgIndex = chatList.size - 1
                    startSpeechRecognition(scheduleMode = isScheduleMode)
                }
            }, 1200)
        } else {
            isRecording = true
            updateMicButtonUI()
            addMsg("🎤 듣고 있습니다...", UserChatMessage.TYPE_MINE, false, null, null)
            voiceMsgIndex = chatList.size - 1
            startSpeechRecognition(scheduleMode = isScheduleMode)
        }
    }

    private fun startSpeechRecognition(scheduleMode: Boolean = false) {
        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: android.os.Bundle) {
                isRecording = false
                val matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()
                if (!text.isNullOrBlank()) {
                    sttFailCount = 0
                    updateVoiceStatus(text)
                    if (scheduleMode) handleScheduleVoiceResult(text)
                    else uploadToServer(null, null, text)
                } else {
                    sttFailCount++
                    updateVoiceStatus("다시 말해줄래요?")
                    if (scheduleMode) {
                        if (sttFailCount >= 3) showScheduleFallbackButtons()
                        else mainHandler.postDelayed({ startAutoScheduleListening() }, 1500)
                    } else {
                        resetToIdleState()
                    }
                }
            }
            override fun onError(error: Int) {
                isRecording = false
                Log.e("STT", "SpeechRecognizer 오류: $error")
                sttFailCount++
                updateVoiceStatus("잘 못 들었어요")
                if (scheduleMode) {
                    if (sttFailCount >= 3) showScheduleFallbackButtons()
                    else mainHandler.postDelayed({ startAutoScheduleListening() }, 1500)
                } else {
                    resetToIdleState()
                }
            }
            override fun onReadyForSpeech(params: android.os.Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: android.os.Bundle?) {}
            override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
        })
        speechRecognizer?.startListening(intent)
    }

    private fun showScheduleFallbackButtons() {
        sttFailCount = 0
        awaitingFallbackButton = true
        isRecording = false
        updateMicButtonUI(forceMic = true)
        val msg = "버튼을 눌러줘!"
        addMsg(msg, UserChatMessage.TYPE_OTHER, false, null, mutableListOf("했어요", "모르겠어요"))
        ttsManager.speak(msg)
    }

    private fun handleScheduleVoiceResult(text: String) {
        val completionKeywords = listOf("응", "어", "네", "했어", "다 했어", "완료", "끝", "됐어", "했어요", "다했어", "했습니다", "다 했어요")
        val retryKeywords = listOf("아니", "안 했어", "못 했어", "안했어", "못했어", "아직")

        when {
            completionKeywords.any { text.contains(it) } -> {
                mainHandler.postDelayed({ proceedToNextStep() }, 500)
            }
            retryKeywords.any { text.contains(it) } -> {
                // 안 했다 → 같은 단계 다시 안내
                sendSchedulePromptToAI()
            }
            else -> {
                // 질문이나 벗어난 말 → AI에게 전달, stepComplete: false로 돌아와 자동 재녹음
                uploadToServer(null, null, text)
            }
        }
    }

    private fun openBackCamera() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra("android.intent.extras.CAMERA_FACING", 0)
        }
        try { takePictureLauncher.launch(intent) } catch (e: Exception) { resetToIdleState() }
    }

    private fun resetToIdleState() {
        isRecording = false
        shouldLaunchCamera = false
        updateMicButtonUI(forceMic = true)
        runOnUiThread { if (::voskManager.isInitialized) voskManager.startListening() }
    }

    private fun setBearMood(mood: BearMood) {
        runOnUiThread {
            val res = when (mood) {
                BearMood.BASIC -> R.drawable.ddolddol_basic
                BearMood.WORRY -> R.drawable.ddolddol_worry
                BearMood.PRAISE -> R.drawable.ddolddol_praise
            }
            ivBear.setImageResource(res)
            ivBear.alpha = if (mood == BearMood.PRAISE) 0.25f else 0.13f
        }
    }

    private fun buildUserContext(): String {
        val sb = StringBuilder("[사용자 정보]\n")
        if (userName.isNotBlank()) sb.append("이름: $userName\n")
        if (userSpecialNote.isNotBlank()) sb.append("특이사항: $userSpecialNote\n")
        if (whitelistTaskNames.isNotEmpty()) {
            sb.append("보호자가 허용한 활동: ${whitelistTaskNames.joinToString(", ")}\n")
        }
        sb.append("사용자는 중등도 지적장애가 있습니다. 짧고 쉬운 말로, 한 번에 한 가지만 말해주세요.")
        return sb.toString()
    }

    private fun handleScheduleIntent(intent: Intent) {
        if (intent.getBooleanExtra("schedule_auto_execute", false)) {
            val scheduleId = intent.getStringExtra("schedule_id") ?: return
            val title = intent.getStringExtra("schedule_title") ?: return
            mainHandler.postDelayed({ startScheduleExecution(scheduleId, title) }, 1500)
        }
    }

    private fun showScheduleDialog(scheduleId: String, title: String) {
        runOnUiThread {
            AlertDialog.Builder(this)
                .setTitle("📅 일정 시간이 됐어요!")
                .setMessage(title)
                .setPositiveButton("하기") { _, _ -> startScheduleExecution(scheduleId, title) }
                .setNegativeButton("나중에", null)
                .show()
        }
    }

    private fun startScheduleExecution(scheduleId: String, title: String) {
        val id = scheduleId.toIntOrNull() ?: -1
        scheduleTitle = title
        currentScheduleId = id
        currentStepIndex = 0
        Thread {
            val (steps, note) = ScheduleRepository.fetchStepsByScheduleId(currentScheduleId, USER_ID, USER_IDX)
            runOnUiThread {
                scheduleSteps = steps
                scheduleNote = note
                android.util.Log.d("ScheduleDebug", "단계 로드: ${steps.size}개 → $steps")
                sendSchedulePromptToAI()
            }
        }.start()
    }

    private fun sendSchedulePromptToAI() {
        if (loadingBar.visibility == View.VISIBLE || isRecording) return
        voskManager.stopListening()
        val prompt = buildSchedulePrompt()
        addMsg("📅 $scheduleTitle ${stepProgressLabel()}", UserChatMessage.TYPE_MINE, false, null, null)
        uploadToServer(null, null, prompt)
    }

    private fun buildSchedulePrompt(): String {
        // 서버가 시스템 프롬프트 처리하므로 사용자 발화(시작 신호)만 전달
        return if (currentStepIndex == 0) "일정 시작" else "계속해주세요"
    }

    private fun stepProgressLabel() = if (scheduleSteps.isEmpty()) "시작" else "(${currentStepIndex + 1}/${scheduleSteps.size}단계)"

    private fun proceedToNextStep() {
        if (!isScheduleMode) return
        sttFailCount = 0
        voiceMsgIndex = -1
        currentStepIndex++
        if (scheduleSteps.isEmpty() || currentStepIndex >= scheduleSteps.size) {
            finishSchedule()
            return
        }
        sendSchedulePromptToAI()
    }

    private fun finishSchedule() {
        ScheduleRepository.completeSchedule(currentScheduleId)
        currentStepIndex = -1; currentScheduleId = -1
        setBearMood(BearMood.PRAISE)
        val doneMsg = "수고했어요! 일정을 완료했어요."
        addMsg(doneMsg, UserChatMessage.TYPE_OTHER, false, null, null)
        ttsManager.speak(doneMsg)
    }

    override fun onStart() {
        super.onStart()
        if (::voskManager.isInitialized) voskManager.startListening()

        // 알림 리시버 등록 (안드로이드 버전 대응)
        val filter = IntentFilter(ScheduleAlarmReceiver.ACTION_SCHEDULE_ALERT)
        ContextCompat.registerReceiver(this, scheduleReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        // 앱 진입/복귀 시 즉시 갱신 및 30분 주기 예약 시작
        if (::USER_ID.isInitialized) {
            scheduleManager.syncSchedulesFromDB(USER_ID, USER_IDX)
            refreshHandler.removeCallbacks(refreshRunnable) // 중복 방지
            refreshHandler.postDelayed(refreshRunnable, 30 * 60 * 1000L)
        }
    }

    override fun onStop() {
        super.onStop()
        if (::voskManager.isInitialized) voskManager.stopListening()

        // 리시버 해제
        try { unregisterReceiver(scheduleReceiver) } catch (e: Exception) { }

        // 자동 갱신 중단 (배터리 보호)
        refreshHandler.removeCallbacks(refreshRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacksAndMessages(null)
        refreshHandler.removeCallbacksAndMessages(null) // 핸들러 완전 정리
        if (::voskManager.isInitialized) voskManager.stopListening()
        if (::ttsManager.isInitialized) ttsManager.release()
        speechRecognizer?.destroy()
    }

    private fun updateVoiceStatus(newText: String?) {
        if (voiceMsgIndex != -1 && voiceMsgIndex < chatList.size) {
            chatList[voiceMsgIndex].content = newText
            adapter.notifyItemChanged(voiceMsgIndex)
        }
    }

    private fun addMsg(c: String?, t: Int, i: Boolean, b: Bitmap?, s: MutableList<String?>?) {
        chatList.add(UserChatMessage(c, t, i, b, s))
        adapter.notifyItemInserted(chatList.size - 1)
        rvChat.smoothScrollToPosition(chatList.size - 1)
    }

    private fun checkPermissions() {
        val perms = mutableListOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) perms.add(Manifest.permission.POST_NOTIFICATIONS)
        val needed = perms.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (needed.isNotEmpty()) ActivityCompat.requestPermissions(this, needed.toTypedArray(), 100)
        else voskManager.initModel()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) voskManager.initModel()
    }

    override fun onSuggestionClick(text: String?) {
        if (loadingBar.visibility == View.VISIBLE) return
        // 일정 모드 버튼은 녹음/TTS 중에도 클릭 허용
        if (!isScheduleMode && (isRecording || ttsManager.isSpeaking())) return
        if (isScheduleMode && (text == "다음 단계로" || text == "완료" || text == "했어요")) {
            awaitingFallbackButton = false
            speechRecognizer?.cancel()
            isRecording = false
            proceedToNextStep()
            return
        }
        if (isScheduleMode && text == "모르겠어요") {
            awaitingFallbackButton = false
            speechRecognizer?.cancel()
            isRecording = false
            startRecordingFlow(manual = true)
            return
        }
        voskManager.stopListening()
        voiceMsgIndex = -1
        addMsg(text, UserChatMessage.TYPE_MINE, false, null, null)
        uploadToServer(null, null, text)
    }
}