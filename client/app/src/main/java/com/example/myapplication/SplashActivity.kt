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
import com.example.myapplication.core.network.ApiClient
import com.example.myapplication.core.network.ApiResult
import com.example.myapplication.core.network.Session
import com.example.myapplication.guardian.main.GuardianMainActivity
import com.example.myapplication.guardian.onboarding.LoginActivity
import com.example.myapplication.guardian.onboarding.WelcomeActivity
import org.json.JSONObject

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        Handler(Looper.getMainLooper()).postDelayed({
            if (Session.isLoggedIn() && Session.isOnboarded) {
                restoreSessionAndNavigate()
            } else if (Session.isLoggedIn()) {
                startActivity(Intent(this, WelcomeActivity::class.java))
                finish()
            } else {
                startActivity(Intent(this, RoleSelectActivity::class.java))
                finish()
            }
        }, 1500)
    }

    private fun restoreSessionAndNavigate() {
        ApiClient.get("/v1/user/link/${Session.guardianId}") { result ->
            runOnUiThread {
                if (result is ApiResult.Success) {
                    try {
                        val json = JSONObject(result.data)
                        val arr = json.getJSONArray("result")
                        if (arr.length() > 0) {
                            Session.userIdx = arr.getJSONObject(0).getInt("userIdx")
                            startActivity(Intent(this, GuardianMainActivity::class.java))
                            finish()
                            return@runOnUiThread
                        }
                    } catch (_: Exception) {}
                }
                // userIdx 복원 실패 → 로그인 화면으로
                Session.logout()
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            }
        }
    }
}