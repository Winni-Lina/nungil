package com.example.myapplication.guardian.onboarding

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.example.myapplication.R
import com.example.myapplication.core.network.ApiClient
import com.example.myapplication.core.network.ApiResult
import org.json.JSONObject

class ForgotPasswordActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forgot_password)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        val etId       = findViewById<EditText>(R.id.etId)
        val etEmail    = findViewById<EditText>(R.id.etEmail)
        val btnConfirm = findViewById<Button>(R.id.btnConfirm)
        val pbLoading  = findViewById<ProgressBar>(R.id.pbLoading)
        val tvError    = findViewById<TextView>(R.id.tvError)
        val cardResult = findViewById<CardView>(R.id.cardResult)
        val tvTempPw   = findViewById<TextView>(R.id.tvTempPw)

        btnConfirm.setOnClickListener {
            val id    = etId.text.toString().trim()
            val email = etEmail.text.toString().trim()

            if (id.isEmpty() || email.isEmpty()) {
                tvError.text       = "아이디와 이메일을 입력해주세요."
                tvError.visibility = View.VISIBLE
                return@setOnClickListener
            }

            tvError.visibility  = View.GONE
            cardResult.visibility = View.GONE
            pbLoading.visibility = View.VISIBLE
            btnConfirm.isEnabled = false

            val body = JSONObject().apply {
                put("id",    id)
                put("email", email)
            }.toString()

            ApiClient.post("/v1/guardian/auth/reset-password", body) { result ->
                runOnUiThread {
                    pbLoading.visibility = View.GONE
                    btnConfirm.isEnabled = true
                    when (result) {
                        is ApiResult.Success -> {
                            try {
                                val json   = JSONObject(result.data)
                                val status = json.optString("status", "")
                                if (status == "SUCCESS") {
                                    val tempPw = json.getJSONObject("result").getString("tempPassword")
                                    tvTempPw.text      = tempPw
                                    cardResult.visibility = View.VISIBLE
                                } else {
                                    tvError.text       = json.optString("message", "오류가 발생했어요.")
                                    tvError.visibility = View.VISIBLE
                                }
                            } catch (e: Exception) {
                                tvError.text       = "응답 처리 오류"
                                tvError.visibility = View.VISIBLE
                            }
                        }
                        is ApiResult.Error -> {
                            tvError.text       = "서버 연결 실패: ${result.message}"
                            tvError.visibility = View.VISIBLE
                        }
                    }
                }
            }
        }
    }
}
