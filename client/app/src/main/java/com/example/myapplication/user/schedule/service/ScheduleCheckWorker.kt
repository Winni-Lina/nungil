package com.example.myapplication.user.schedule.service

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.example.myapplication.user.schedule.data.ScheduleRepository

class ScheduleCheckWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("nungil_prefs", Context.MODE_PRIVATE)
        val userId = prefs.getString("user_id", null) ?: return Result.success()
        val userIdx = prefs.getInt("user_idx", -1).takeIf { it >= 0 } ?: return Result.success()

        val now = System.currentTimeMillis()
        val manager = ScheduleManager(applicationContext)
        val schedules = ScheduleRepository.fetchTodaySchedules(userId, userIdx)

        val notifiedPrefs = applicationContext.getSharedPreferences("notified_schedules", Context.MODE_PRIVATE)

        schedules.forEach { item ->
            if (item.triggerTimeMillis == 0L) return@forEach  // 파싱 실패 스킵
            val lateMs = now - item.triggerTimeMillis
            val notifyKey = "late_${item.scheduleId}"
            when {
                // AlarmManager가 이미 처리했을 구간(5분 이내) 제외, 60분 이내만 지연 알림
                lateMs in (5 * 60 * 1000L)..(60 * 60 * 1000L) -> {
                    if (!notifiedPrefs.getBoolean(notifyKey, false)) {
                        manager.showLateNotification(item.scheduleId.toString(), item.title)
                        notifiedPrefs.edit().putBoolean(notifyKey, true).apply()
                    }
                }
                lateMs > 60 * 60 * 1000L -> {
                    // 오래된 알림 기록 정리
                    notifiedPrefs.edit().remove(notifyKey).apply()
                }
            }
        }
        return Result.success()
    }
}
