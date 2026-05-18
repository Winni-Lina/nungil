//package com.example.myapplication
//
//import android.annotation.SuppressLint
//import android.content.Intent
//import android.os.Bundle
//import android.os.Handler
//import android.os.Looper
//import androidx.appcompat.app.AppCompatActivity
//import com.example.myapplication.core.network.Session
//import com.example.myapplication.guardian.main.GuardianMainActivity
//import com.example.myapplication.guardian.onboarding.LoginActivity
//import com.example.myapplication.guardian.onboarding.WelcomeActivity
//
//@SuppressLint("CustomSplashScreen")
//class SplashActivity : AppCompatActivity() {
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        setContentView(R.layout.activity_splash)
//
//        Handler(Looper.getMainLooper()).postDelayed({
//            val next = when {
//                Session.isLoggedIn() && Session.isOnboarded ->
//                    Intent(this, GuardianMainActivity::class.java)
//                Session.isLoggedIn() && !Session.isOnboarded ->
//                    Intent(this, WelcomeActivity::class.java)
//                else ->
//                    Intent(this, LoginActivity::class.java)
//            }
//            startActivity(next)
//            finish()
//        }, 1500)
//    }
//}

package com.example.myapplication

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // 1.5초 후 무조건 선택 화면(UserOrGuardianActivity)으로 이동
        Handler(Looper.getMainLooper()).postDelayed({
            val intent = Intent(this, RoleSelectActivity::class.java)
            startActivity(intent)
            finish()
        }, 1500)
    }
}