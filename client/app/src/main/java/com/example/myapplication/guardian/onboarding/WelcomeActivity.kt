package com.example.myapplication.guardian.onboarding

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.R
import com.example.myapplication.guardian.qr.QrScanActivity

// IS-001 : 똘똘이 풀스크린 영상
class WelcomeActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private var moved = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_welcome)

        val videoView = findViewById<VideoView>(R.id.videoView)
        val uri = Uri.parse("android.resource://$packageName/${R.raw.ttoli_welcome}")
        videoView.setVideoURI(uri)
        videoView.start()

        // 탭하면 바로 넘어감
        videoView.setOnClickListener { goNext() }
        // 영상 끝나면 넘어감
        videoView.setOnCompletionListener { goNext() }
        // 최대 3초
        handler.postDelayed({ goNext() }, 3000)
    }

    private fun goNext() {
        if (moved) return
        moved = true
        handler.removeCallbacksAndMessages(null)
        startActivity(Intent(this, QrScanActivity::class.java).apply {
            putExtra(QrScanActivity.EXTRA_FROM_ONBOARDING, true)
        })
        finish()
    }
}
