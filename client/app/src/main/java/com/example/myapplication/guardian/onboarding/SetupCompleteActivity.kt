package com.example.myapplication.guardian.onboarding

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.R
import com.example.myapplication.core.network.Session
import com.example.myapplication.guardian.main.GuardianMainActivity

class SetupCompleteActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup_complete)

        // 온보딩 완료 → 다음 앱 실행 시 바로 메인으로
        Session.isOnboarded = true

        findViewById<Button>(R.id.btnStart).setOnClickListener {
            startActivity(Intent(this, GuardianMainActivity::class.java))
            finish()
        }
    }
}
