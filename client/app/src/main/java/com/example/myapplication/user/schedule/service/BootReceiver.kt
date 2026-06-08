package com.example.myapplication.user.schedule.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val prefs   = context.getSharedPreferences("nungil_prefs", Context.MODE_PRIVATE)
        val userId  = prefs.getString("user_id", null)  ?: return
        val userIdx = prefs.getInt("user_idx", -1).takeIf { it >= 0 } ?: return

        val sm = ScheduleManager(context)

        // 1차 시도: 즉시 (네트워크가 이미 연결된 경우 커버)
        sm.syncSchedulesFromDB(userId, userIdx)

        // 2차 시도: 네트워크 연결 보장 후 WorkManager로 재시도
        //   → 재부팅 직후 Wi-Fi 연결 전 1차 실패 시 자동 재실행됨
        val request = OneTimeWorkRequestBuilder<BootSyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setInitialDelay(15, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork("boot_sync", ExistingWorkPolicy.REPLACE, request)

        // 15분 주기 늦은 알림 워커도 재등록 (재부팅 시 WorkManager 초기화됨)
        sm.startPeriodicCheck()
    }
}
