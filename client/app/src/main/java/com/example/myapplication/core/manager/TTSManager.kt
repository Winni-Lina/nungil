package com.example.myapplication.core.manager

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.*

class TTSManager(context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = TextToSpeech(context, this)
    private var isReady = false

    // 외부에 상태를 알리기 위한 콜백 (true: 재생 시작, false: 종료)
    var onStatusListener: ((Boolean) -> Unit)? = null

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.KOREAN)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TTS_DEBUG", "지원되지 않는 언어입니다.")
            } else {
                isReady = true

                // 음성 진행 상태 모니터링 등록
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        onStatusListener?.invoke(true) // 시작 알림
                    }

                    override fun onDone(utteranceId: String?) {
                        onStatusListener?.invoke(false) // 종료 알림
                    }

                    override fun onError(utteranceId: String?) {
                        onStatusListener?.invoke(false) // 에러 시 종료 취급
                    }
                })
            }
        }
    }

    fun speak(text: String?) {
        if (isReady && text != null) {
            val params = Bundle()
            val utteranceId = UUID.randomUUID().toString()
            // QUEUE_FLUSH를 사용하여 기존 음성을 끊고 새로 말합니다.
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        }
    }

    fun isSpeaking(): Boolean {
        return tts?.isSpeaking ?: false
    }

    fun stop() {
        tts?.stop()
    }

    fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}