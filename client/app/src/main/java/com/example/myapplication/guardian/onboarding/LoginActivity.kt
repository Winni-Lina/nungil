package com.example.myapplication.guardian.onboarding

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.R
import com.example.myapplication.core.fcm.NungilFirebaseMessagingService
import com.example.myapplication.core.network.ApiClient
import com.example.myapplication.core.network.ApiResult
import com.example.myapplication.core.network.Session
import com.example.myapplication.guardian.main.GuardianMainActivity
import com.google.firebase.messaging.FirebaseMessaging
import org.json.JSONObject

class LoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val etId       = findViewById<EditText>(R.id.etId)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnLogin   = findViewById<Button>(R.id.btnLogin)
        val pbLogin    = findViewById<ProgressBar>(R.id.pbLogin)
        val tvError    = findViewById<TextView>(R.id.tvError)

        findViewById<TextView>(R.id.tvSignup).setOnClickListener {
            startActivity(Intent(this, SignupActivity::class.java))
        }
        findViewById<TextView>(R.id.tvForgotPassword).setOnClickListener {
            startActivity(Intent(this, ForgotPasswordActivity::class.java))
        }

        btnLogin.setOnClickListener {
            val id  = etId.text.toString().trim()
            val pw  = etPassword.text.toString().trim()

            if (id.isEmpty() || pw.isEmpty()) {
                tvError.text = "아이디와 비밀번호를 입력해주세요."
                tvError.visibility = View.VISIBLE
                return@setOnClickListener
            }

            pbLogin.visibility = View.VISIBLE
            tvError.visibility = View.GONE
            btnLogin.isEnabled = false

            val body = JSONObject().apply {
                put("id", id)
                put("pw", pw)
            }.toString()

            // 서버 팀에 로그인 API 경로 확인 필요 → 현재 추정 경로 사용
            ApiClient.post("/v1/guardian/auth/login", body) { result ->
                runOnUiThread {
                    pbLogin.visibility = View.GONE
                    btnLogin.isEnabled = true

                    when (result) {
                        is ApiResult.Success -> {
                            try {
                                val json = JSONObject(result.data)
                                val status = json.optString("status", "")
                                if (status == "SUCCESS") {
                                    val resultObj = json.getJSONObject("result")
                                    Session.guardianId   = resultObj.getString("id")
                                    Session.guardianName = resultObj.optString("name", "")
                                    navigateAfterLogin()
                                } else {
                                    tvError.text = "아이디 또는 비밀번호가 틀렸어요."
                                    tvError.visibility = View.VISIBLE
                                }
                            } catch (e: Exception) {
                                Log.e("MyProjectTag", "오류가 발생했습니다: ${e.message}", e)
                                tvError.text = "로그인 응답 처리 오류"
                                tvError.visibility = View.VISIBLE
                            }
                        }
                        is ApiResult.Error -> {
                            tvError.text = "서버 연결 실패: ${result.message}"
                            tvError.visibility = View.VISIBLE
                        }
                    }
                }
            }
        }
    }

    private fun navigateAfterLogin() {
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            NungilFirebaseMessagingService.registerTokenWithServer(token)
        }
        // 서버에서 연동된 유저 목록 조회 → DB 기준으로 온보딩 여부 판단
        ApiClient.get("/v1/user/link/${Session.guardianId}") { result ->
            runOnUiThread {
                val next = when {
                    result is ApiResult.Success -> {
                        try {
                            val json = JSONObject(result.data)
                            val arr = json.getJSONArray("result")
                            if (arr.length() > 0) {
                                val first = arr.getJSONObject(0)
                                Session.userIdx = first.getInt("userIdx")
                                Session.isOnboarded = true
                                Intent(this, GuardianMainActivity::class.java)
                            } else {
                                Intent(this, WelcomeActivity::class.java)
                            }
                        } catch (e: Exception) {
                            Intent(this, WelcomeActivity::class.java)
                        }
                    }
                    else -> Intent(this, WelcomeActivity::class.java)
                }
                startActivity(next)
                finish()
            }
        }
    }
}
