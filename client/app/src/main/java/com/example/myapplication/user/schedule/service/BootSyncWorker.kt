package com.example.myapplication.user.schedule.service

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

/**
 * 재부팅 직후 네트워크가 아직 연결 안 됐을 때를 대비한 알람 복구 워커.
 * BootReceiver가 WorkManager에 등록하며, 네트워크 연결 후 자동 실행됨.
 */
class BootSyncWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        val prefs   = applicationContext.getSharedPreferences("nungil_prefs", Context.MODE_PRIVATE)
        val userId  = prefs.getString("user_id", null)  ?: return Result.success()
        val userIdx = prefs.getInt("user_idx", -1).takeIf { it >= 0 } ?: return Result.success()

        return try {
            ScheduleManager(applicationContext).syncSchedulesFromDB(userId, userIdx)
            Result.success()
        } catch (e: Exception) {
            android.util.Log.e("BootSyncWorker", "알람 복구 실패: ${e.message}")
            Result.retry() // WorkManager가 자동 재시도
        }
    }
}
