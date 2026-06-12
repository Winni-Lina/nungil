package com.example.myapplication.user.qr

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.R
import com.example.myapplication.user.chat.UserChatActivity
import com.example.myapplication.core.network.ApiClient
import com.google.firebase.messaging.FirebaseMessaging
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
                    setOrientationLocked(true)
                }
            )
        }

        findViewById<Button>(R.id.btnTestInput).setOnClickListener {
            val layout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(60, 40, 60, 20)
            }
            val etUserId  = EditText(this).apply { hint = "userId (예: guardian02)" }
            val etUserIdx = EditText(this).apply { hint = "userIdx (예: 1)"; inputType = android.text.InputType.TYPE_CLASS_NUMBER }
            layout.addView(etUserId)
            layout.addView(etUserIdx)

            AlertDialog.Builder(this)
                .setTitle("테스트 직접 입력")
                .setView(layout)
                .setPositiveButton("확인") { _, _ ->
                    val userId  = etUserId.text.toString().trim()
                    val userIdx = etUserIdx.text.toString().toIntOrNull()
                    if (userId.isEmpty() || userIdx == null) {
                        Toast.makeText(this, "값을 올바르게 입력해주세요.", Toast.LENGTH_SHORT).show()
                    } else {
                        handleQrResult(JSONObject().put("userId", userId).put("userIdx", userIdx).toString())
                    }
                }
                .setNegativeButton("취소", null)
                .show()
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

            // FCM 토큰 서버에 등록
            FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                val body = org.json.JSONObject().apply {
                    put("userId", userId)
                    put("userIdx", userIdx)
                    put("fcmToken", token)
                }.toString()
                ApiClient.put("/v1/user/fcm-token", body) {}
            }

            startActivity(Intent(this, UserChatActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            finish()
        } catch (e: Exception) {
            Toast.makeText(this, "올바르지 않은 QR코드예요. 다시 시도해주세요.", Toast.LENGTH_SHORT).show()
        }
    }
}
