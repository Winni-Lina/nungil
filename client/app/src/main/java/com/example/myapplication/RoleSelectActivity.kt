package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.core.network.Session
import com.example.myapplication.guardian.main.GuardianMainActivity
import com.example.myapplication.guardian.onboarding.LoginActivity
import com.example.myapplication.guardian.onboarding.WelcomeActivity
import com.example.myapplication.user.UserStartActivity

class RoleSelectActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. 세션 초기화
        Session.init(applicationContext)

        // 2. 이미 역할이 선택된 사용자라면 선택 과정을 건너뛰고 즉시 분기
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
        val nextIntent = if (role == Session.ROLE_USER) {
            // [사용자 모드]
            Intent(this, UserStartActivity::class.java)
        } else {
            // [보호자 모드] 세션 상태에 따른 세부 분기
            when {
                // 로그인 완료 + 초기 설정(온보딩) 완료 -> 메인 화면
                Session.isLoggedIn() && Session.isOnboarded ->
                    Intent(this, GuardianMainActivity::class.java)

                // 로그인 완료 + 초기 설정 미완료 -> 환영/설정 화면
                Session.isLoggedIn() && !Session.isOnboarded ->
                    Intent(this, WelcomeActivity::class.java)

                // 로그인 안 됨 -> 로그인 화면
                else ->
                    Intent(this, LoginActivity::class.java)
            }
        }

        startActivity(nextIntent)
        finish() // 현재 RoleSelectActivity는 스택에서 제거
    }
}