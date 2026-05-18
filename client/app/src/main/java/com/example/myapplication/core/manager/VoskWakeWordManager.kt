package com.example.myapplication.core.manager

import android.content.Context
import android.os.PowerManager
import android.util.Log
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService

class VoskWakeWordManager(
    private val context: Context,
    private val keywords: String, // 기존 호출어 리스트
    private val listener: WakeWordListener
) : RecognitionListener {

    interface WakeWordListener {
        fun onKeywordDetected()
        fun onModelLoaded()
        fun onModelLoadFail()
    }

    private var speechService: SpeechService? = null
    private var model: Model? = null
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

    fun initModel() {
        StorageService.unpack(context, "model-ko", "model", { m ->
            model = m
            listener.onModelLoaded()
        }, {
            Log.e("VoskManager", "Unpack Fail")
            listener.onModelLoadFail()
        })
    }

    fun startListening() {
        if (speechService != null || model == null) return
        try {
            // [오인식 수정] 미끼 단어들을 추가하여 억지 매칭을 방지합니다.
            val grammar = "[\"똘똘\", \"똘똘아\", \"똑똑\", \"돌돌\", \"아니야\", \"그래요\", \"[unknown]\"]"
            val rec = Recognizer(model, 16000.0f, grammar)
            speechService = SpeechService(rec, 16000.0f)
            speechService?.startListening(this)
        } catch (e: Exception) {
            Log.e("VoskManager", "Start Fail: ${e.message}")
        }
    }

    fun stopListening() {
        speechService?.stop()
        speechService?.shutdown()
        speechService = null
    }

    override fun onPartialResult(hypothesis: String) {
        // [화면 체크] 화면이 꺼져 있으면 반응하지 않음
        if (!powerManager.isInteractive) return

        val partial = JSONObject(hypothesis).optString("partial", "").trim()
        // 미끼 단어에 걸리지 않고 정확히 호출어일 때만 감지
        if (partial == "똘똘" || partial == "똘똘아") {
            listener.onKeywordDetected()
        }
    }

    override fun onResult(hypothesis: String) {
        if (!powerManager.isInteractive) return
        val text = JSONObject(hypothesis).optString("text", "").trim()
        if (text == "똘똘" || text == "똘똘아") {
            listener.onKeywordDetected()
        }
    }

    override fun onFinalResult(p0: String?) {}
    override fun onError(e: Exception?) { stopListening() }
    override fun onTimeout() { stopListening() }
}