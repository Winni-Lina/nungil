package com.example.myapplication

import android.app.Application
import com.example.myapplication.core.network.Session
import com.example.myapplication.user.schedule.service.ScheduleManager

class NungilApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Session.init(this)
        ScheduleManager.createNotificationChannel(this)

        // 사용자 앱으로 이미 로그인된 상태면 앱 시작 시 WorkManager 등록
        // (UserChatActivity를 열기 전에도 주기적 체크 워커가 살아있도록)
        val prefs = getSharedPreferences("nungil_prefs", MODE_PRIVATE)
        if (prefs.getString("user_id", null) != null) {
            ScheduleManager(this).startPeriodicCheck()
        }
    }
}
