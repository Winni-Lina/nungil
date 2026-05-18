package com.example.myapplication.core.manager

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.File

class VoiceRecorderManager(
    private val context: Context,
    private val listener: RecorderListener
) {
    interface RecorderListener {
        fun onRecordingStart()
        fun onRecordingSuccess(file: File)
        fun onRecordingCancel()
        fun onError(message: String)
    }

    private var recorder: MediaRecorder? = null
    private var voiceFile: File? = null
    private var isRecording = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private val autoStopRunnable = Runnable { stop() }

    fun start(maxDurationMs: Long = 5000) {
        if (isRecording) return

        try {
            voiceFile = File(context.getExternalFilesDir(null), "voice_input.m4a")
            voiceFile?.delete()

            recorder = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()).apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(voiceFile?.absolutePath)
                prepare()
                start()
            }

            isRecording = true
            listener.onRecordingStart()
            mainHandler.postDelayed(autoStopRunnable, maxDurationMs)

        } catch (e: Exception) {
            Log.e("RecorderManager", "Start Fail: ${e.message}")
            listener.onError("마이크를 사용할 수 없습니다.")
        }
    }

    fun stop() {
        if (!isRecording) return
        mainHandler.removeCallbacks(autoStopRunnable)
        isRecording = false

        try {
            recorder?.stop()
            voiceFile?.let { listener.onRecordingSuccess(it) }
        } catch (e: Exception) {
            listener.onError("녹음 저장 중 에러가 발생했습니다.")
        } finally {
            release()
        }
    }

    fun cancel() {
        mainHandler.removeCallbacks(autoStopRunnable)
        isRecording = false
        release()
        listener.onRecordingCancel()
    }

    fun release() {
        recorder?.release()
        recorder = null
    }
}