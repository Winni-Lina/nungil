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
import android.widget.Toast
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
import org.json.JSONArray
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
    private var pendingCameraLaunch = false
    private var speechRecognizer: SpeechRecognizer? = null
    private var sttFailCount = 0
    private var awaitingFallbackButton = false
    private val mainHandler = Handler(Looper.getMainLooper())

    // 완료 확인창: 중복 표시 방지 + 열려 있는 동안 자동 음성 인식 차단
    private var stepConfirmDialog: AlertDialog? = null
    private var awaitingStepConfirm = false

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
        conversationHistory.add(UserChatLog("model", welcomeMsg))
        saveChatMessage("ASSISTANT", welcomeMsg)
        mainHandler.postDelayed({ ttsManager.speak(welcomeMsg) }, 1000)

        // 앱 재실행 후에도 Gemini가 이전 자유대화 맥락을 참고하도록 DB에서 최근 대화 복원
        fetchChatHistory("GENERAL", null) { restored ->
            if (restored.isNotEmpty()) {
                runOnUiThread { conversationHistory.addAll(0, restored) }
            }
        }

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

        findViewById<View>(R.id.btnTodaySchedule).setOnClickListener {
            startActivity(Intent(this, com.example.myapplication.user.main.UserMainActivity::class.java))
        }

        adapter = ChatAdapter(chatList, this)
        rvChat.layoutManager = LinearLayoutManager(this)
        rvChat.adapter = adapter

        btnMic.setOnClickListener {
            if (loadingBar.visibility == View.VISIBLE) return@setOnClickListener
            // 완료 확인창 대기 중엔 버튼만 사용해야 하므로 마이크 탭 무시
            if (awaitingStepConfirm) return@setOnClickListener

            when {
                ttsManager.isSpeaking() -> {
                    ttsManager.stop()
                    handleTtsEndFlow()
                }
                isRecording -> {
                    speechRecognizer?.cancel()
                    resetToIdleState()
                }
                // fallback 버튼 대기 중엔 마이크 탭 무시 (버튼만 사용해야 함)
                awaitingFallbackButton -> { /* 버튼을 눌러줘! 대기 중 - 무시 */ }
                else -> {
                    startRecordingFlow(manual = true)
                }
            }
        }
    }

    private fun handleTtsEndFlow() {
        runOnUiThread {
            updateMicButtonUI(forceMic = true)
            // 완료 확인창이 떠 있거나 뜰 예정이면 어떤 음성 인식도 시작하지 않는다
            if (awaitingStepConfirm) return@runOnUiThread
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
        if (awaitingStepConfirm) return   // 완료 확인창 대기 중에는 마이크를 열지 않는다
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
        if (text != null) {
            conversationHistory.add(UserChatLog("user", text))
            saveChatMessage("USER", text)
        }

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
                runOnUiThread {
                    loadingBar.visibility = View.GONE
                    setBearMood(BearMood.BASIC)
                    showFallbackGuide()   // 현재 단계 유지 + 기본 안내
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: ""
                Log.d("ServerCheck", "서버 응답 수신 | 코드: ${response.code}, 길이: ${body.length}자")

                runOnUiThread {
                    loadingBar.visibility = View.GONE
                    setBearMood(BearMood.BASIC)
                    if (response.isSuccessful) {
                        parseServerResponse(body)
                    } else {
                        Log.e("ServerCheck", "서버 응답 실패: ${response.code}")
                        showFallbackGuide()
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
                val photoRequest = result.optBoolean("photoRequest", false)
                shouldLaunchCamera = photoRequest

                val transcribed = result.optString("transcribedText", "")
                if (transcribed.isNotEmpty()) updateVoiceStatus(transcribed)

                var answer = result.optString("answer", "")
                    .replace("```json", "").replace("```", "").trim()
                if (answer.startsWith("{") && answer.contains("\"answer\"")) {
                    try { answer = JSONObject(answer).optString("answer", answer).trim() } catch (_: Exception) {}
                }
                val intent = IntentPolicy.normalize(result.optString("intent", ""))
                conversationHistory.add(UserChatLog("model", answer))
                saveChatMessage("ASSISTANT", answer, intent)

                val suggestArray = result.optJSONArray("suggestedQuestions")
                val suggests = mutableListOf<String?>()
                if (suggestArray != null) {
                    for (i in 0 until suggestArray.length()) suggests.add(suggestArray.getString(i))
                }

                val stepComplete = result.optBoolean("stepComplete", false)
                val isDone = IntentPolicy.shouldConfirmStep(intent)
                if (IntentPolicy.isInconsistent(intent, stepComplete)) {
                    Log.w("ServerCheck", "intent/stepComplete 불일치 → intent 기준 처리 (intent=$intent, stepComplete=$stepComplete)")
                }

                // 질문 횟수: 질문·도움 요청·사진 요청만, 한 발화당 한 번만 증가
                val shouldLogQuestion = isScheduleMode && currentScheduleId > 0 &&
                    IntentPolicy.shouldCountQuestion(intent, photoRequest)
                if (shouldLogQuestion) ScheduleRepository.logQuestion(currentScheduleId.toLong())

                addMsg(answer, UserChatMessage.TYPE_OTHER, false, null, suggests)

                // 완료 확인창은 intent=STEP_DONE 일 때만. stepComplete는 참고값으로만 쓴다.
                // STEP_DONE을 받은 시점부터 확인창을 닫을 때까지 자동 음성 인식을 막는다.
                if (isScheduleMode && isDone) {
                    awaitingStepConfirm = true
                    speechRecognizer?.cancel()
                    isRecording = false
                    voskManager.stopListening()
                }
                ttsManager.speak(answer)
                if (isScheduleMode && isDone) {
                    mainHandler.postDelayed({ showStepConfirmDialog() }, 1400)
                }
                Log.d("ServerCheck", "파싱 완료 | intent=$intent stepComplete=$stepComplete")
            } else {
                Log.e("ServerCheck", "서버 status 비정상: ${root.optString("status")}")
                showFallbackGuide()
            }
        } catch (e: Exception) {
            Log.e("ServerCheck", "JSON 파싱 에러: ${e.message}")
            showFallbackGuide()
        }
    }

    /** 서버·JSON·네트워크 오류 공통 처리 — 단계를 바꾸지 않고 기본 안내만 보여준다.
     *  질문 횟수도 증가시키지 않는다. */
    private fun showFallbackGuide() {
        runOnUiThread {
            val msg = "잠깐, 다시 한 번 말해줄래?"
            addMsg(msg, UserChatMessage.TYPE_OTHER, false, null, null)
            ttsManager.speak(msg)
            resetToIdleState()
        }
    }

    /** 단계 완료 재확인 다이얼로그 — AI 판단과 실제 단계 이동을 분리한다.
     *  이미 떠 있으면 다시 띄우지 않고, 각 버튼은 한 번만 동작한다. */
    private fun showStepConfirmDialog() {
        if (!isScheduleMode || isFinishing || isDestroyed) return
        if (stepConfirmDialog?.isShowing == true) return   // 중복 표시 방지

        runOnUiThread {
            if (!isScheduleMode || isFinishing || isDestroyed) return@runOnUiThread
            if (stepConfirmDialog?.isShowing == true) return@runOnUiThread

            awaitingStepConfirm = true
            awaitingFallbackButton = false
            speechRecognizer?.cancel()
            isRecording = false
            voskManager.stopListening()
            updateMicButtonUI(forceMic = true)

            val step = scheduleSteps.getOrElse(currentStepIndex) { scheduleTitle }
            stepConfirmDialog = AlertDialog.Builder(this)
                .setTitle("현재 단계를 모두 했나요?")
                .setMessage(step)
                .setPositiveButton("했어요") { _, _ ->
                    dismissStepConfirm()
                    proceedToNextStep()
                }
                .setNeutralButton("아직이에요") { _, _ ->
                    dismissStepConfirm()
                    val msg = "괜찮아요. 천천히 해요. 다 하면 알려 주세요."
                    addMsg(msg, UserChatMessage.TYPE_OTHER, false, null, null)
                    saveChatMessage("ASSISTANT", msg)
                    ttsManager.speak(msg)   // TTS 종료 후 handleTtsEndFlow가 음성 인식 재개
                }
                .setNegativeButton("도와주세요") { _, _ ->
                    dismissStepConfirm()
                    // 서버가 HELP_REQUEST로 분류 → 질문 횟수는 여기서 1회만 증가
                    val helpText = "도와주세요"
                    voiceMsgIndex = -1
                    addMsg(helpText, UserChatMessage.TYPE_MINE, false, null, null)
                    uploadToServer(null, null, helpText)
                }
                .setCancelable(false)
                .create()
            stepConfirmDialog?.show()
        }
    }

    /** 확인창을 닫고 예약된 표시·차단 상태를 함께 정리한다 */
    private fun dismissStepConfirm() {
        awaitingStepConfirm = false
        try { stepConfirmDialog?.dismiss() } catch (_: Exception) {}
        stepConfirmDialog = null
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
        // destroy 후 바로 시작하면 ERROR_CLIENT(11) 발생 → 100ms 딜레이
        speechRecognizer?.destroy()
        speechRecognizer = null
        mainHandler.postDelayed({
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            doStartListening(scheduleMode)
        }, 100)
    }

    private fun doStartListening(scheduleMode: Boolean) {
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
                        if (sttFailCount >= 2) showScheduleFallbackButtons()
                        else mainHandler.postDelayed({ startAutoScheduleListening() }, 1500)
                    } else {
                        resetToIdleState()
                    }
                }
            }
            override fun onError(error: Int) {
                isRecording = false
                Log.e("STT", "SpeechRecognizer 오류: $error")
                // ERROR_RECOGNIZER_BUSY(7): 이전 세션 충돌 → 카운트 없이 재시도
                if (error == 7) {
                    mainHandler.postDelayed({ startAutoScheduleListening() }, 500)
                    return
                }
                sttFailCount++
                if (scheduleMode) {
                    if (sttFailCount >= 2) showScheduleFallbackButtons()
                    else mainHandler.postDelayed({ startAutoScheduleListening() }, 1500)
                } else {
                    updateVoiceStatus("잘 못 들었어요")
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
        // 완료 판단은 서버(AI)가 단일 소유 — 클라 키워드 자동완료 제거.
        // 모든 발화를 서버로 보내고, 응답 stepComplete=true면 자동으로 다음 단계 진행한다.
        // (기존 키워드 매칭은 "다 했는데 이거 맞아?" 같은 질문을 완료로 오인하는 문제가 있었음)
        uploadToServer(null, null, text.trim())
    }

    private fun openBackCamera() {
        // 매니페스트에 CAMERA 권한이 선언돼 있으면 ACTION_IMAGE_CAPTURE도 런타임 허용이 있어야 동작한다.
        // 허용이 없으면 launch가 SecurityException을 던지므로, 먼저 확인하고 없으면 요청 후 재시도한다.
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            Log.w("Camera", "CAMERA 권한 미허용 → 권한 요청")
            Toast.makeText(this, "사진을 찍으려면 카메라 권한을 허용해주세요.", Toast.LENGTH_SHORT).show()
            pendingCameraLaunch = true
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 101)
            return
        }
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra("android.intent.extras.CAMERA_FACING", 0)
        }
        try {
            takePictureLauncher.launch(intent)
        } catch (e: Exception) {
            // 더 이상 조용히 삼키지 않고 원인을 노출한다 (카메라 앱 없음/보안 예외 등)
            Log.e("Camera", "카메라 실행 실패: ${e.message}", e)
            Toast.makeText(this, "카메라를 열 수 없어요: ${e.message}", Toast.LENGTH_LONG).show()
            resetToIdleState()
        }
    }

    private fun resetToIdleState() {
        isRecording = false
        shouldLaunchCamera = false
        updateMicButtonUI(forceMic = true)
        if (awaitingStepConfirm) return   // 확인창 대기 중엔 웨이크워드도 재개하지 않는다
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
            // 이 일정으로 이전에 나눈 대화가 있으면 복원해 Gemini가 맥락을 참고하도록 한다
            fetchChatHistory("SCHEDULE", currentScheduleId) { restored ->
                runOnUiThread {
                    scheduleSteps = steps
                    scheduleNote = note
                    if (restored.isNotEmpty()) conversationHistory.addAll(0, restored)
                    android.util.Log.d("ScheduleDebug", "단계 로드: ${steps.size}개 → $steps")
                    sendSchedulePromptToAI()
                }
            }
        }.start()
    }

    /** guardianId/userIdx + chatMode(+scheduleId) 기준으로 서버 CHAT_HISTORY에서 과거 대화를 조회한다. */
    private fun fetchChatHistory(chatMode: String, scheduleId: Int?, onResult: (List<UserChatLog>) -> Unit) {
        val urlBuilder = (AppConfig.BASE_URL + "api/v1/chat-history").toHttpUrl().newBuilder()
            .addQueryParameter("guardianId", USER_ID)
            .addQueryParameter("userIdx", USER_IDX.toString())
            .addQueryParameter("chatMode", chatMode)
        if (scheduleId != null) urlBuilder.addQueryParameter("scheduleId", scheduleId.toString())
        val request = Request.Builder().url(urlBuilder.build()).get().build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("ChatHistory", "조회 실패: ${e.message}")
                onResult(emptyList())
            }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val root = JSONObject(response.body?.string() ?: "")
                    if (root.optString("status") != "SUCCESS") {
                        onResult(emptyList())
                        return
                    }
                    val arr = root.optJSONArray("messages") ?: JSONArray()
                    val list = (0 until arr.length()).map { i ->
                        val obj = arr.getJSONObject(i)
                        val role = if (obj.optString("sender") == "ASSISTANT") "model" else "user"
                        UserChatLog(role, obj.optString("message", ""))
                    }
                    onResult(list)
                } catch (e: Exception) {
                    Log.e("ChatHistory", "조회 응답 파싱 실패: ${e.message}")
                    onResult(emptyList())
                }
            }
        })
    }

    private fun sendSchedulePromptToAI() {
        if (loadingBar.visibility == View.VISIBLE || isRecording) return
        voskManager.stopListening()
        // 새 단계 시작 시 히스토리에서 이전 단계 대화 제거 (최근 2개만 유지)
        if (currentStepIndex > 0 && conversationHistory.size > 4) {
            val recent = conversationHistory.takeLast(2).toMutableList()
            conversationHistory.clear()
            conversationHistory.addAll(recent)
        }
        val prompt = buildSchedulePrompt()
        addMsg("📅 $scheduleTitle ${stepProgressLabel()}", UserChatMessage.TYPE_MINE, false, null, null)
        uploadToServer(null, null, prompt)
    }

    private fun buildSchedulePrompt(): String {
        if (currentStepIndex == 0) return "일정 시작"
        // 새 단계 시작 시 단계 이름을 명확하게 전달해서 AI 혼란 방지
        val stepName = scheduleSteps.getOrElse(currentStepIndex) { "" }
        return if (stepName.isNotBlank()) "다음 단계 시작: $stepName" else "다음 단계로 넘어가줘"
    }

    private fun stepProgressLabel() = if (scheduleSteps.isEmpty()) "시작" else "(${currentStepIndex + 1}/${scheduleSteps.size}단계)"

    private fun proceedToNextStep() {
        if (!isScheduleMode) return
        sttFailCount = 0
        awaitingFallbackButton = false
        awaitingStepConfirm = false
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
        val finishedTitle = scheduleTitle
        val finishedScheduleId = currentScheduleId
        val finishedStepIndex = currentStepIndex
        val historyJson = gson.toJson(conversationHistory)
        currentStepIndex = -1; currentScheduleId = -1
        setBearMood(BearMood.PRAISE)

        // 기본 완료 메시지 먼저 표시, 서버 요약 도착하면 교체
        val defaultMsg = "수고했어요! 일정을 완료했어요."
        addMsg(defaultMsg, UserChatMessage.TYPE_OTHER, false, null, null)
        val summaryMsgIndex = chatList.size - 1
        ttsManager.speak(defaultMsg)

        requestScheduleSummary(finishedTitle, historyJson) { summary ->
            runOnUiThread {
                if (summary.isNotBlank() && summaryMsgIndex < chatList.size) {
                    chatList[summaryMsgIndex].content = summary
                    adapter.notifyItemChanged(summaryMsgIndex)
                    conversationHistory.add(UserChatLog("model", summary))
                    saveChatMessage(
                        "ASSISTANT", summary,
                        chatMode = "SCHEDULE",
                        scheduleId = finishedScheduleId,
                        stepIndex = finishedStepIndex
                    )
                    ttsManager.speak(summary)
                }
            }
        }
    }

    /** 대화 1건을 서버 CHAT_HISTORY 테이블에 비동기로 저장한다 (실패해도 화면 흐름은 막지 않음). */
    private fun saveChatMessage(
        sender: String,
        message: String,
        intent: String? = null,
        chatMode: String = if (isScheduleMode) "SCHEDULE" else "GENERAL",
        scheduleId: Int = currentScheduleId,
        stepIndex: Int = currentStepIndex
    ) {
        if (message.isBlank()) return
        val bodyJson = JSONObject().apply {
            put("guardianId", USER_ID)
            put("userIdx", USER_IDX)
            if (chatMode == "SCHEDULE") {
                put("scheduleId", scheduleId)
                put("stepIndex", stepIndex)
            }
            put("chatMode", chatMode)
            put("sender", sender)
            put("message", message)
            if (intent != null) put("intent", intent)
        }.toString()
        val request = Request.Builder()
            .url(AppConfig.BASE_URL + "api/v1/chat-history")
            .post(bodyJson.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull()))
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("ChatHistory", "저장 실패: ${e.message}")
            }
            override fun onResponse(call: Call, response: Response) {
                response.close()
            }
        })
    }

    /** 서버에 완료 요약 요청 */
    private fun requestScheduleSummary(title: String, historyJson: String, onResult: (String) -> Unit) {
        val url = AppConfig.BASE_URL + "api/v1/question/summarize"
        val bodyJson = JSONObject().apply {
            put("userId", USER_ID)
            put("scheduleTitle", title)
            put("historyJson", historyJson)
        }.toString()
        val request = Request.Builder()
            .url(url)
            .post(bodyJson.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull()))
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("ScheduleSummary", "요약 요청 실패: ${e.message}")
                onResult("")
            }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val body = response.body?.string() ?: ""
                    val root = JSONObject(body)
                    if (root.optString("status") == "SUCCESS") {
                        onResult(root.optString("message", ""))
                    } else onResult("")
                } catch (e: Exception) {
                    Log.e("ScheduleSummary", "요약 파싱 실패: ${e.message}")
                    onResult("")
                }
            }
        })
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
        // 화면을 벗어나면 예약된 확인창이 나중에 다시 뜨지 않도록 정리
        dismissStepConfirm()
        if (::voskManager.isInitialized) voskManager.stopListening()

        // 리시버 해제
        try { unregisterReceiver(scheduleReceiver) } catch (e: Exception) { }

        // 자동 갱신 중단 (배터리 보호)
        refreshHandler.removeCallbacks(refreshRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        dismissStepConfirm()
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
        // 카메라 권한 요청 결과 처리 → 허용됐으면 대기 중이던 촬영을 이어서 실행
        if (requestCode == 101) {
            val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            if (granted && pendingCameraLaunch) {
                pendingCameraLaunch = false
                openBackCamera()
            } else {
                pendingCameraLaunch = false
                Toast.makeText(this, "카메라 권한이 없어 사진을 찍을 수 없어요.", Toast.LENGTH_SHORT).show()
                resetToIdleState()
            }
        }
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
            // STT 재시작이 아니라 AI에게 "모르겠어요" 전달 → 도움 안내 받기
            voiceMsgIndex = -1
            addMsg(text, UserChatMessage.TYPE_MINE, false, null, null)
            uploadToServer(null, null, text)
            return
        }
        voskManager.stopListening()
        voiceMsgIndex = -1
        addMsg(text, UserChatMessage.TYPE_MINE, false, null, null)
        uploadToServer(null, null, text)
    }
}