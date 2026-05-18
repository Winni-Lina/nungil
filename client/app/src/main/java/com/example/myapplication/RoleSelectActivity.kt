package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.core.network.ApiClient
import com.example.myapplication.core.network.ApiResult
import com.example.myapplication.core.network.Session
import com.example.myapplication.guardian.main.GuardianMainActivity
import com.example.myapplication.guardian.onboarding.GuardianChatActivity
import com.example.myapplication.guardian.onboarding.LoginActivity
import com.example.myapplication.guardian.onboarding.WelcomeActivity
import com.example.myapplication.user.UserStartActivity
import org.json.JSONObject

class RoleSelectActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 이미 역할이 선택된 사용자라면 선택 과정을 건너뛰고 즉시 분기
        if (Session.hasRole()) {
            routeBySessionState(Session.role)
            return
        }

        // 3. 처음 방문한 사용자에게만 선택 레이아웃 표시
        setContentView(R.layout.activity_role_select)

        // 보호자 버튼/카드 클릭
        findViewById<View>(R.id.btnGuardian).setOnClickListener {
            confirmRole("보호자", Session.ROLE_GUARDIAN)
        }

        // 사용자(피보호자) 버튼/카드 클릭
        findViewById<View>(R.id.btnUser).setOnClickListener {
            confirmRole("사용자 (피보호자)", Session.ROLE_USER)
        }
    }

    /**
     * 역할 확정 다이얼로그
     */
    private fun confirmRole(roleName: String, roleValue: String) {
        AlertDialog.Builder(this)
            .setTitle("역할 확정")
            .setMessage("'$roleName'으로 설정하시겠어요?\n한 번 선택하면 앱을 삭제하기 전까지 변경할 수 없어요.")
            .setPositiveButton("확정") { _, _ ->
                Session.role = roleValue // 세션에 역할 저장
                routeBySessionState(roleValue)
            }
            .setNegativeButton("취소", null)
            .show()
    }

    /**
     * 역할 및 세션 상태(로그인, 온보딩)에 따른 지능형 라우팅
     */
    private fun routeBySessionState(role: String) {
        if (role == Session.ROLE_USER) {
            startActivity(Intent(this, UserStartActivity::class.java))
            finish()
            return
        }

        // 보호자 모드
        when {
            Session.isLoggedIn() && Session.isOnboarded -> {
                startActivity(Intent(this, GuardianMainActivity::class.java))
                finish()
            }
            Session.isLoggedIn() && !Session.isOnboarded -> {
                // 서버에서 연동 유저 확인 — 온보딩 중간에 앱 종료된 경우 대비
                checkLinkedUserAndRoute()
            }
            else -> {
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            }
        }
    }

    private fun checkLinkedUserAndRoute() {
        ApiClient.get("/v1/user/link/${Session.guardianId}") { result ->
            runOnUiThread {
                val next = if (result is ApiResult.Success) {
                    try {
                        val arr = JSONObject(result.data).getJSONArray("result")
                        if (arr.length() > 0) {
                            Session.userIdx = arr.getJSONObject(0).getInt("userIdx")
                            Session.isOnboarded = true
                            Intent(this, GuardianMainActivity::class.java)
                        } else {
                            Intent(this, WelcomeActivity::class.java)
                        }
                    } catch (e: Exception) {
                        Intent(this, WelcomeActivity::class.java)
                    }
                } else {
                    Intent(this, WelcomeActivity::class.java)
                }
                startActivity(next)
                finish()
            }
        }
    }
}