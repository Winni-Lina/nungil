package com.example.myapplication.user.schedule.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val prefs = context.getSharedPreferences("nungil_prefs", Context.MODE_PRIVATE)
        val userId  = prefs.getString("user_id", null) ?: return
        val userIdx = prefs.getInt("user_idx", -1).takeIf { it >= 0 } ?: return
        ScheduleManager(context).syncSchedulesFromDB(userId, userIdx)
    }
}
