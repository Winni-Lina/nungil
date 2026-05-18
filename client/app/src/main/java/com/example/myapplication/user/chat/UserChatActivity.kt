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
import com.example.myapplication.core.manager.VoiceRecorderManager
import com.example.myapplication.core.manager.VoskWakeWordManager
import com.example.myapplication.user.schedule.data.ScheduleRepository
import com.example.myapplication.user.schedule.service.ScheduleAlarmReceiver
import com.example.myapplication.user.schedule.service.ScheduleManager
import com.google.gson.Gson
import okhttp3.*
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

    private lateinit var voiceRecorder: VoiceRecorderManager
    private lateinit var voskManager: VoskWakeWordManager
    private lateinit var ttsManager: TTSManager
    private lateinit var scheduleManager: ScheduleManager

    private var isRecording = false
    private var voiceMsgIndex = -1
    private var shouldLaunchCamera = false
    private val mainHandler = Handler(Looper.getMainLooper())

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

        setupUI()
        setupManagers()
        checkPermissions()

        val welcomeMsg = "반가워요! '똘똘아'라고 불러보세요."
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
                userSpecialNote = userInfo.specialNote
                whitelistTaskNames = userInfo.whitelistTaskNames
            }
        }.start()
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
                    voiceRecorder.cancel()
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
            } else {
                if (!isRecording && loadingBar.visibility != View.VISIBLE) voskManager.startListening()
            }
        }
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

        voiceRecorder = VoiceRecorderManager(this, object : VoiceRecorderManager.RecorderListener {
            override fun onRecordingStart() {
                isRecording = true
                runOnUiThread {
                    updateMicButtonUI()
                    addMsg("🎤 듣고 있습니다...", UserChatMessage.TYPE_MINE, false, null, null)
                    voiceMsgIndex = chatList.size - 1
                }
            }
            override fun onRecordingSuccess(file: File) {
                isRecording = false
                updateMicButtonUI()
                updateVoiceStatus("🔍 분석 중...")
                uploadToServer(null, file, null)
            }
            override fun onRecordingCancel() { resetToIdleState() }
            override fun onError(message: String) { resetToIdleState() }
        })

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

        val finalText = if (text != null && !isScheduleMode) "${buildUserContext()}\n\n사용자 질문: $text" else text
        if (finalText != null) conversationHistory.add(UserChatLog("user", finalText))

        val builder = MultipartBody.Builder().setType(MultipartBody.FORM)
        val textMt = "text/plain; charset=utf-8".toMediaTypeOrNull()

        builder.addFormDataPart("userId", null, USER_ID.toRequestBody(textMt))
        builder.addFormDataPart("historyJson", null, gson.toJson(conversationHistory).toRequestBody(textMt))
        finalText?.let { builder.addFormDataPart("textPrompt", null, it.toRequestBody(textMt)) }

        newBitmap?.let { bitmap ->
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
            builder.addFormDataPart("imageFile", "capture.jpg", stream.toByteArray().toRequestBody("image/jpeg".toMediaTypeOrNull()))
        }
        voice?.let { if (it.exists()) builder.addFormDataPart("voiceFile", it.name, it.asRequestBody("audio/m4a".toMediaTypeOrNull())) }

        val request = Request.Builder().url(SERVER_URL).post(builder.build()).build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("ServerCheck", "네트워크 오류 발생: ${e.message}")
                runOnUiThread { loadingBar.visibility = View.GONE; setBearMood(BearMood.BASIC); resetToIdleState() }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: ""

                // [로그 추가] 서버 응답 원본 확인
                Log.d("ServerCheck", "── 서버 응답 수신 ──")
                Log.d("ServerCheck", "코드: ${response.code}")
                Log.d("ServerCheck", "본문: $body")
                Log.d("ServerCheck", "──────────────────")

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

                if (isScheduleMode && scheduleSteps.isNotEmpty()) {
                    val nextLabel = if (currentStepIndex + 1 < scheduleSteps.size) "다음 단계로" else "완료"
                    suggests.add(0, nextLabel)
                }

                val userContent = conversationHistory.lastOrNull { it.role == "user" }?.message ?: ""
                Thread { ScheduleRepository.logQuestionSuccess(ScheduleRepository.logQuestionStart(USER_ID, USER_IDX, userContent, answer)) }.start()

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
                if (!isRecording && loadingBar.visibility != View.VISIBLE) voiceRecorder.start(5000)
            }, 1200)
        } else {
            voiceRecorder.start(5000)
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
        val userContext = buildUserContext()
        val noteStr = if (scheduleNote.isNotBlank()) "참고사항: $scheduleNote\n" else ""
        return if (scheduleSteps.isEmpty()) {
            "$userContext\n[일정 수행 모드]\n일정: $scheduleTitle\n${noteStr}일정을 시작합니다."
        } else {
            val stepDesc = scheduleSteps[currentStepIndex]
            "$userContext\n[일정 수행 모드]\n일정: $scheduleTitle\n단계: $stepDesc"
        }
    }

    private fun stepProgressLabel() = if (scheduleSteps.isEmpty()) "시작" else "(${currentStepIndex + 1}/${scheduleSteps.size}단계)"

    private fun proceedToNextStep() {
        if (!isScheduleMode) return
        currentStepIndex++
        if (scheduleSteps.isNotEmpty() && currentStepIndex >= scheduleSteps.size) {
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
        val filter = IntentFilter(ScheduleAlarmReceiver.ACTION_SCHEDULE_ALERT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(scheduleReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(scheduleReceiver, filter)
        }
    }

    override fun onStop() {
        super.onStop()
        if (::voskManager.isInitialized) voskManager.stopListening()
        try { unregisterReceiver(scheduleReceiver) } catch (e: Exception) { }
    }

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacksAndMessages(null)
        if (::voskManager.isInitialized) voskManager.stopListening()
        if (::voiceRecorder.isInitialized) voiceRecorder.release()
        if (::ttsManager.isInitialized) ttsManager.release()
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
        if (loadingBar.visibility == View.VISIBLE || isRecording || ttsManager.isSpeaking()) return
        if (isScheduleMode && (text == "다음 단계로" || text == "완료")) { proceedToNextStep(); return }
        voskManager.stopListening()
        voiceMsgIndex = -1
        addMsg(text, UserChatMessage.TYPE_MINE, false, null, null)
        uploadToServer(null, null, text)
    }
}