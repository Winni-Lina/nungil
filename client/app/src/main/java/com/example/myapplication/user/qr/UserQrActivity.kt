package com.example.myapplication.user.qr

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.R
import com.example.myapplication.user.chat.UserChatActivity
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import org.json.JSONObject

class UserQrActivity : AppCompatActivity() {

    private val scanLauncher = registerForActivityResult(ScanContract()) { result ->
        result.contents?.let { content -> handleQrResult(content) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_qr)

        findViewById<Button>(R.id.btnUserScan).setOnClickListener {
            scanLauncher.launch(
                ScanOptions().apply {
                    setPrompt("보호자 기기의 QR코드를 스캔해주세요")
                    setBeepEnabled(false)
                    setOrientationLocked(false)
                }
            )
        }
    }

    private fun handleQrResult(content: String) {
        try {
            val json    = JSONObject(content)
            val userId  = json.getString("userId")
            val userIdx = json.getInt("userIdx")

            getSharedPreferences("nungil_prefs", MODE_PRIVATE).edit()
                .putString("user_id", userId)
                .putInt("user_idx", userIdx)
                .apply()

            startActivity(Intent(this, UserChatActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            finish()
        } catch (e: Exception) {
            Toast.makeText(this, "올바르지 않은 QR코드예요. 다시 시도해주세요.", Toast.LENGTH_SHORT).show()
        }
    }
}
