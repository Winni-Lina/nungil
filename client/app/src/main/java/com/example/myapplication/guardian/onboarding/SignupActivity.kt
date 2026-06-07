package com.example.myapplication.guardian.onboarding

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.R
import com.example.myapplication.core.network.ApiClient
import com.example.myapplication.core.network.ApiResult
import org.json.JSONObject

class SignupActivity : AppCompatActivity() {

    private var idChecked = false   // 중복확인 통과 여부

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        val etId          = findViewById<EditText>(R.id.etId)
        val etPw          = findViewById<EditText>(R.id.etPw)
        val etPwConfirm   = findViewById<EditText>(R.id.etPwConfirm)
        val etName        = findViewById<EditText>(R.id.etName)
        val etEmail       = findViewById<EditText>(R.id.etEmail)
        val etPhone       = findViewById<EditText>(R.id.etPhone)
        val btnSignup     = findViewById<Button>(R.id.btnSignup)
        val btnCheckId    = findViewById<Button>(R.id.btnCheckId)
        val pbSignup      = findViewById<ProgressBar>(R.id.pbSignup)
        val tvError       = findViewById<TextView>(R.id.tvError)
        val tvIdResult    = findViewById<TextView>(R.id.tvIdCheckResult)

        // 아이디 입력 변경 시 중복확인 초기화
        etId.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                idChecked = false
                tvIdResult.visibility = View.GONE
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        // 중복확인 버튼
        btnCheckId.setOnClickListener {
            val id = etId.text.toString().trim()
            if (id.isEmpty()) {
                tvIdResult.text = "아이디를 입력해주세요."
                tvIdResult.setTextColor(Color.parseColor("#D32F2F"))
                tvIdResult.visibility = View.VISIBLE
                return@setOnClickListener
            }
            btnCheckId.isEnabled = false
            ApiClient.get("/v1/guardian/auth/check-id?id=$id") { result ->
                runOnUiThread {
                    btnCheckId.isEnabled = true
                    when (result) {
                        is ApiResult.Success -> {
                            try {
                                val json = JSONObject(result.data)
                                val available = json.optBoolean("available", false)
                                if (available) {
                                    idChecked = true
                                    tvIdResult.text = "사용 가능한 아이디예요."
                                    tvIdResult.setTextColor(Color.parseColor("#388E3C"))
                                } else {
                                    idChecked = false
                                    tvIdResult.text = "이미 사용 중인 아이디예요."
                                    tvIdResult.setTextColor(Color.parseColor("#D32F2F"))
                                }
                                tvIdResult.visibility = View.VISIBLE
                            } catch (e: Exception) {
                                showError(tvError, "중복확인 응답 오류")
                            }
                        }
                        is ApiResult.Error -> showError(tvError, "중복확인 실패: ${result.message}")
                    }
                }
            }
        }

        // 가입 버튼
        btnSignup.setOnClickListener {
            val id    = etId.text.toString().trim()
            val pw    = etPw.text.toString()
            val pwCfm = etPwConfirm.text.toString()
            val name  = etName.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val phone = etPhone.text.toString().trim()

            if (id.isEmpty() || pw.isEmpty() || name.isEmpty() || email.isEmpty()) {
                showError(tvError, "아이디, 비밀번호, 이름, 이메일은 필수예요.")
                return@setOnClickListener
            }
            if (!idChecked) {
                showError(tvError, "아이디 중복확인을 해주세요.")
                return@setOnClickListener
            }
            if (pw != pwCfm) {
                showError(tvError, "비밀번호가 일치하지 않아요.")
                return@setOnClickListener
            }
            if (pw.length < 4) {
                showError(tvError, "비밀번호는 4자 이상이어야 해요.")
                return@setOnClickListener
            }

            tvError.visibility  = View.GONE
            pbSignup.visibility = View.VISIBLE
            btnSignup.isEnabled = false

            val body = JSONObject().apply {
                put("id",    id)
                put("pw",    pw)
                put("name",  name)
                put("email", email)
                put("phone", phone)
            }.toString()

            ApiClient.post("/v1/guardian/auth/signup", body) { result ->
                runOnUiThread {
                    pbSignup.visibility = View.GONE
                    btnSignup.isEnabled = true
                    when (result) {
                        is ApiResult.Success -> {
                            try {
                                val json   = JSONObject(result.data)
                                val status = json.optString("status", "")
                                if (status == "SUCCESS") {
                                    Toast.makeText(this, "가입 완료! 로그인해주세요.", Toast.LENGTH_SHORT).show()
                                    finish()
                                } else {
                                    val code = json.optString("errorCode", "")
                                    val msg = when (code) {
                                        "ID_EXISTS"    -> "이미 사용 중인 아이디예요."
                                        "EMAIL_EXISTS" -> "이미 사용 중인 이메일이에요."
                                        else           -> json.optString("message", "가입에 실패했어요.")
                                    }
                                    showError(tvError, msg)
                                }
                            } catch (e: Exception) {
                                showError(tvError, "응답 처리 오류")
                            }
                        }
                        is ApiResult.Error -> showError(tvError, "서버 연결 실패: ${result.message}")
                    }
                }
            }
        }
    }

    private fun showError(tv: TextView, msg: String) {
        tv.text       = msg
        tv.visibility = View.VISIBLE
    }
}
