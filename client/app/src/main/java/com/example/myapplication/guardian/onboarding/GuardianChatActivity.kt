package com.example.myapplication.guardian.onboarding

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.R
import com.example.myapplication.common.model.ChatMessage
import com.example.myapplication.core.network.ApiClient
import com.example.myapplication.guardian.main.GuardianMainActivity
import com.example.myapplication.core.network.ApiResult
import com.example.myapplication.core.network.Session
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale

// ChatAdapter.OnSuggestionClickListener 인터페이스를 상속받아 오류를 해결합니다.
class GuardianChatActivity : AppCompatActivity(), ChatAdapter.OnSuggestionClickListener {

    private var tts: TextToSpeech? = null
    private val chatList = mutableListOf<ChatMessage>()
    private lateinit var adapter: ChatAdapter
    private lateinit var rvChat: RecyclerView
    private lateinit var etInput: EditText
    private lateinit var btnNext: Button
    private lateinit var layoutInput: View
    private lateinit var scrollChips: View
    private lateinit var chipGroup: ChipGroup

    private val registeredTaskIds   = mutableListOf<Int>()
    private val registeredTaskNames = mutableListOf<String>()
    private val allTasks            = mutableListOf<Pair<Int, String>>()

    private var isBotTyping = false
    private val handler = Handler(Looper.getMainLooper())

    companion object {
        private const val MIN_TASKS = 2
        private const val MAX_TASKS = 4
    }

    private val fallbackTasks = listOf(
        Pair(0, "빨래하기"), Pair(0, "설거지하기"), Pair(0, "청소기 돌리기"),
        Pair(0, "밥 먹기"), Pair(0, "약 먹기")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_guardian_chat)

        // UI 초기화
        rvChat      = findViewById(R.id.rvChat)
        etInput     = findViewById(R.id.etInput)
        btnNext     = findViewById(R.id.btnNext)
        layoutInput = findViewById(R.id.layoutInput)
        scrollChips = findViewById(R.id.scrollChips)
        chipGroup   = findViewById(R.id.chipGroup)

        rvChat.layoutManager = LinearLayoutManager(this).also { it.stackFromEnd = true }

        // [수정] 인자 2개를 정확히 전달: (리스트, 리스너)
        adapter = ChatAdapter(chatList, this)
        rvChat.adapter = adapter

        btnNext.setOnClickListener { saveWhitelistAndNext() }
        findViewById<Button>(R.id.btnSend).setOnClickListener { sendInput() }
        etInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) { sendInput(); true } else false
        }

        checkWhitelistAndProceed()
    }

    private fun checkWhitelistAndProceed() {
        val path = "/v1/guardian/settings/user/${Session.guardianId}/${Session.userIdx}/whitelist"
        ApiClient.get(path) { result ->
            runOnUiThread {
                val hasItems = when (result) {
                    is ApiResult.Success -> {
                        try {
                            val root = JSONObject(result.data)
                            val arr  = root.getJSONObject("result").getJSONArray("allowedItems")
                            arr.length() > 0
                        } catch (e: Exception) { false }
                    }
                    is ApiResult.Error -> false
                }
                if (hasItems) {
                    Session.isOnboarded = true
                    startActivity(Intent(this, GuardianMainActivity::class.java))
                    finish()
                } else {
                    tts = TextToSpeech(this) { status ->
                        if (status == TextToSpeech.SUCCESS) {
                            tts?.language = Locale.KOREAN
                            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                                override fun onStart(id: String) {}
                                override fun onError(id: String) {}
                                override fun onDone(id: String) {
                                    if (id == "msg1") {
                                        val guide = "사용자가 해볼 수 있는 활동을 ${MIN_TASKS}~${MAX_TASKS}개 골라주세요."
                                        botMessage(guide, delay = 600)
                                        tts?.speak(guide, TextToSpeech.QUEUE_FLUSH, null, "msg2")
                                    } else if (id == "msg2") {
                                        runOnUiThread {
                                            layoutInput.visibility = View.VISIBLE
                                            showSuggestionChips()
                                        }
                                    }
                                }
                            })
                            loadTasksAndStartChat()
                        }
                    }
                }
            }
        }
    }

    // ChatAdapter 인터페이스 구현 (추천 칩 클릭 시 동작)
    override fun onSuggestionClick(text: String?) {
        if (isBotTyping || text == null) return
        addUserMessage(text)
        setBotTyping(true)
        searchAndRegister(text)
    }

    private fun loadTasksAndStartChat() {
        ApiClient.get("/v1/guardian/tasks") { result ->
            when (result) {
                is ApiResult.Success -> {
                    try {
                        val arr = JSONObject(result.data).getJSONArray("tasks")
                        for (i in 0 until arr.length()) {
                            val obj = arr.getJSONObject(i)
                            allTasks.add(Pair(obj.getInt("taskId"), obj.getString("taskName")))
                        }
                    } catch (_: Exception) {
                        allTasks.addAll(fallbackTasks)
                    }
                }
                is ApiResult.Error -> allTasks.addAll(fallbackTasks)
            }
            startChat()
        }
    }

    private fun startChat() {
        val greeting = "안녕하세요! 저는 눈길 도우미 똘똘이예요 😊"
        botMessage(greeting)
        tts?.speak(greeting, TextToSpeech.QUEUE_FLUSH, null, "msg1")
    }

    private fun showSuggestionChips() {
        chipGroup.removeAllViews()
        val remaining = allTasks.filter { (id, _) -> !registeredTaskIds.contains(id) }

        if (remaining.isEmpty() || registeredTaskIds.size >= MAX_TASKS) {
            scrollChips.visibility = View.GONE
            return
        }

        remaining.forEach { (taskId, taskName) ->
            val chip = Chip(this).apply {
                text = taskName
                isClickable = true
                setChipBackgroundColorResource(R.color.colorPrimary)
                setTextColor(resources.getColor(android.R.color.white, null))
                setOnClickListener {
                    if (isBotTyping) return@setOnClickListener
                    addUserMessage(taskName)
                    setBotTyping(true)
                    searchAndRegister(taskName)
                }
            }
            chipGroup.addView(chip)
        }
        scrollChips.visibility = View.VISIBLE
    }

    private fun sendInput() {
        if (isBotTyping) return
        val input = etInput.text.toString().trim()
        if (input.isEmpty()) return
        etInput.setText("")
        addUserMessage(input)
        setBotTyping(true)
        searchAndRegister(input)
    }

    private fun searchAndRegister(input: String) {
        if (registeredTaskIds.size >= MAX_TASKS) {
            botMessage("이미 최대 ${MAX_TASKS}개를 등록하셨어요!", delay = 400)
            handler.postDelayed({ setBotTyping(false) }, 600)
            return
        }

        botMessage("잠깐만요, 확인해볼게요! 🔍", delay = 400)

        handler.postDelayed({
            try {
                val encodedInput = URLEncoder.encode(input, "UTF-8")
                ApiClient.get("/v1/guardian/tasks/search?item=$encodedInput") { result ->
                    when (result) {
                        is ApiResult.Success -> {
                            val json = JSONObject(result.data)
                            if (json.optBoolean("found", false)) {
                                val taskId = json.getInt("taskId")
                                val taskName = json.getString("taskName")
                                registerToWhitelist(taskId, taskName)
                            } else {
                                botMessage("'$input'을 찾지 못했어요 😅", delay = 500)
                                handler.postDelayed({ setBotTyping(false) }, 800)
                            }
                        }
                        is ApiResult.Error -> {
                            botMessage("서버 연결 실패", delay = 500)
                            handler.postDelayed({ setBotTyping(false) }, 800)
                        }
                    }
                }
            } catch (e: Exception) { setBotTyping(false) }
        }, 800)
    }

    private fun registerToWhitelist(taskId: Int, taskName: String) {
        val body = JSONObject().apply { put("taskId", taskId) }.toString()
        val path = "/v1/guardian/settings/user/${Session.guardianId}/${Session.userIdx}/whitelist"

        ApiClient.post(path, body) { result ->
            when (result) {
                is ApiResult.Success -> {
                    try {
                        val json = JSONObject(result.data)
                        val status = json.optString("status", "")
                        if (status == "SUCCESS") {
                            registeredTaskIds.add(taskId)
                            registeredTaskNames.add(taskName)
                            val msg = "'$taskName' 등록! (${registeredTaskIds.size}/$MAX_TASKS)"
                            botMessage(msg, delay = 400)
                            if (registeredTaskIds.size >= MIN_TASKS) {
                                runOnUiThread { btnNext.visibility = View.VISIBLE }
                            }
                            handler.postDelayed({
                                setBotTyping(false)
                                showSuggestionChips()
                            }, 700)
                        } else {
                            val errorCode = json.optString("errorCode", "")
                            val msg = when (errorCode) {
                                "ITEM_EXISTS"       -> "'$taskName'은 이미 등록된 과업이에요."
                                "MAX_ITEM_EXCEEDED" -> "과업은 최대 ${MAX_TASKS}개까지 등록할 수 있어요."
                                else                -> "등록에 실패했어요."
                            }
                            botMessage(msg, delay = 400)
                            handler.postDelayed({ setBotTyping(false) }, 600)
                        }
                    } catch (e: Exception) {
                        botMessage("응답 처리 오류가 발생했어요.", delay = 400)
                        handler.postDelayed({ setBotTyping(false) }, 600)
                    }
                }
                is ApiResult.Error -> {
                    botMessage("서버 연결 오류가 발생했어요.", delay = 400)
                    handler.postDelayed({ setBotTyping(false) }, 600)
                }
            }
        }
    }

    private fun saveWhitelistAndNext() {
        startActivity(Intent(this, SpecialNotesActivity::class.java))
        finish()
    }

    // [핵심 수정] ChatMessage 생성자의 파라미터 5개를 모두 채워줍니다.
    private fun botMessage(text: String, delay: Long = 0) {
        handler.postDelayed({
            runOnUiThread {
                chatList.add(ChatMessage(text, ChatMessage.TYPE_OTHER, false, null, null))
                adapter.notifyItemInserted(chatList.size - 1)
                rvChat.smoothScrollToPosition(chatList.size - 1)
            }
        }, delay)
    }

    // [핵심 수정] ChatMessage 생성자의 파라미터 5개를 모두 채워줍니다.
    private fun addUserMessage(text: String) {
        runOnUiThread {
            chatList.add(ChatMessage(text, ChatMessage.TYPE_MINE, false, null, null))
            adapter.notifyItemInserted(chatList.size - 1)
            rvChat.smoothScrollToPosition(chatList.size - 1)
        }
    }

    private fun setBotTyping(typing: Boolean) {
        isBotTyping = typing
        runOnUiThread {
            etInput.isEnabled = !typing
            findViewById<Button>(R.id.btnSend).isEnabled = !typing
            chipGroup.alpha = if (typing) 0.5f else 1.0f
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}