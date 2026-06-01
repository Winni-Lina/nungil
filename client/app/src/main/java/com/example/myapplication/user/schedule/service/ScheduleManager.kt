package com.example.myapplication.user.schedule.service

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.myapplication.user.chat.UserChatActivity
import com.example.myapplication.user.schedule.data.ScheduleRepository
import java.util.concurrent.TimeUnit

class ScheduleManager(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    companion object {
        const val CHANNEL_ID = "schedule_channel"
        const val NOTIFICATION_ID_BASE = 2000

        fun createNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID, "일정 알림", NotificationManager.IMPORTANCE_HIGH
                ).apply { description = "보호자 등록 일정 알림" }
                (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                    .createNotificationChannel(channel)
            }
        }
    }

    /**
     * DB에서 오늘 일정을 가져와 알람을 등록한다.
     * DB 연동 완료 후 ScheduleRepository.fetchTodaySchedules() 결과를 사용.
     */
    fun syncSchedulesFromDB(userId: String, userIdx: Int) {
        Thread {
            val schedules = ScheduleRepository.fetchTodaySchedules(userId, userIdx)
            registerAlarms(schedules)
        }.start()
    }

    fun registerAlarms(schedules: List<ScheduleRepository.ScheduleItem>) {
        android.util.Log.d("AlarmDebug", "registerAlarms 호출 - 일정 ${schedules.size}개")
        val now = System.currentTimeMillis()
        schedules.forEach { item ->
            val diff = item.triggerTimeMillis - now
            android.util.Log.d("AlarmDebug", "scheduleId=${item.scheduleId} title=${item.title} triggerTime=${item.triggerTimeMillis} diff=${diff}ms")
            if (diff > 0) {
                android.util.Log.d("AlarmDebug", "→ 알람 등록: ${item.title} (${diff / 1000}초 후)")
                setAlarm(item.scheduleId.toString(), item.title, item.triggerTimeMillis)
            } else {
                android.util.Log.d("AlarmDebug", "→ 이미 지난 시간, 스킵")
            }
        }
    }

    private fun setAlarm(scheduleId: String, title: String, triggerTimeMillis: Long) {
        val pi = makePendingIntent(scheduleId, title)
        // 기존 알람 취소 후 재등록 (시간 변경 시 이전 알람 잔류 방지)
        alarmManager.cancel(pi)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTimeMillis, pi)
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTimeMillis, pi)
            }
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTimeMillis, pi)
        }
        android.util.Log.d("AlarmDebug", "알람 설정 완료: scheduleId=$scheduleId time=$triggerTimeMillis")
    }

    private fun makePendingIntent(scheduleId: String, title: String): PendingIntent {
        val intent = Intent(context, ScheduleAlarmReceiver::class.java).apply {
            putExtra("schedule_id", scheduleId)
            putExtra("schedule_title", title)
        }
        return PendingIntent.getBroadcast(
            context, scheduleId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun startPeriodicCheck() {
        val request = PeriodicWorkRequestBuilder<ScheduleCheckWorker>(15, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "schedule_check",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun showLateNotification(scheduleId: String, title: String) {
        val doIntent = Intent(context, UserChatActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("schedule_id", scheduleId)
            putExtra("schedule_title", title)
            putExtra("schedule_auto_execute", true)
        }
        val doPi = PendingIntent.getActivity(
            context, scheduleId.hashCode() + 2, doIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("⏰ 일정 시간이 지났어요")
            .setContentText("조금 늦었지만 지금 해볼까요? - $title")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(doPi)
            .addAction(0, "지금 하기", doPi)
            .build()
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIFICATION_ID_BASE + scheduleId.hashCode() + 100, notification)
    }

    fun showNotification(scheduleId: String, title: String) {
        val doIntent = Intent(context, UserChatActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("schedule_id", scheduleId)
            putExtra("schedule_title", title)
            putExtra("schedule_auto_execute", true)
        }
        val doPi = PendingIntent.getActivity(
            context, scheduleId.hashCode() + 1, doIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = androidx.core.app.NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("📅 일정 시간이 됐어요!")
            .setContentText(title)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(doPi)
            .addAction(0, "하기", doPi)
            .build()

        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIFICATION_ID_BASE + scheduleId.hashCode(), notification)
    }
}
