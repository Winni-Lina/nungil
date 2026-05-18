package com.example.myapplication.user

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.example.myapplication.R
import com.example.myapplication.user.chat.UserChatActivity
import com.example.myapplication.user.qr.UserQrActivity

class UserStartActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("nungil_prefs", MODE_PRIVATE)
        val userId = prefs.getString("user_id", null)
        val userIdx = prefs.getInt("user_idx", -1)
        if (userId != null && userIdx != -1) {
            startActivity(Intent(this, UserChatActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_main)

        findViewById<CardView>(R.id.cardUser).setOnClickListener {
            startActivity(Intent(this, UserQrActivity::class.java))
        }
    }
}