package com.example.myapplication.user.schedule.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ScheduleAlarmReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_SCHEDULE_ALERT = "com.example.myapplication.SCHEDULE_ALERT"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val scheduleId = intent.getStringExtra("schedule_id") ?: return
        val title = intent.getStringExtra("schedule_title") ?: "일정"

        // 알림 표시 (앱 백그라운드 대비)
        ScheduleManager(context).showNotification(scheduleId, title)

        // 앱 포그라운드면 다이얼로그용 브로드캐스트 전송
        val alertIntent = Intent(ACTION_SCHEDULE_ALERT).apply {
            setPackage(context.packageName)
            putExtra("schedule_id", scheduleId)
            putExtra("schedule_title", title)
        }
        context.sendBroadcast(alertIntent)
    }
}
