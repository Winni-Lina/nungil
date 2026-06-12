package com.example.myapplication.user.schedule.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ScheduleAlarmReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_SCHEDULE_ALERT = "com.example.myapplication.SCHEDULE_ALERT"
    }

    override fun onReceive(context: Context, intent: Intent) {
        android.util.Log.d("AlarmDebug", "ScheduleAlarmReceiver.onReceive 호출!")
        val scheduleId = intent.getStringExtra("schedule_id") ?: return
        val title = intent.getStringExtra("schedule_title") ?: "일정"
        android.util.Log.d("AlarmDebug", "알람 수신: scheduleId=$scheduleId title=$title")

        // 알람 내역 기록 (알림 센터용)
        AlarmEventLog.record(context, scheduleId, title, AlarmEventLog.Status.FIRED)

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
