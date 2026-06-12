package com.example.myapplication.core.fcm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.myapplication.R
import com.example.myapplication.core.network.ApiClient
import com.example.myapplication.core.network.Session
import com.example.myapplication.guardian.notification.NotificationDialogActivity
import com.example.myapplication.user.schedule.service.AlarmEventLog
import com.example.myapplication.user.schedule.service.ScheduleManager
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicInteger

class NungilFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // 사용자 앱 토큰 등록 (FCM SCHEDULE_UPDATED를 받으려면 서버에 반드시 등록되어야 함)
        val prefs   = getSharedPreferences("nungil_prefs", MODE_PRIVATE)
        val userId  = prefs.getString("user_id", null)
        val userIdx = prefs.getInt("user_idx", -1)
        if (userId != null && userIdx >= 0) {
            registerUserTokenWithServer(token, userId, userIdx)
        }
        // 보호자 앱 토큰 등록
        if (Session.isLoggedIn()) {
            registerTokenWithServer(token)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val data = message.data
        val type = data["type"] ?: return

        if (Session.isInDnd()) return

        // 사용자 앱 일정 업데이트 처리
        if (type == "SCHEDULE_UPDATED") {
            val prefs   = getSharedPreferences("nungil_prefs", MODE_PRIVATE)
            val userId  = prefs.getString("user_id", null) ?: return
            val userIdx = prefs.getInt("user_idx", -1).takeIf { it >= 0 } ?: return
            ScheduleManager(this).syncSchedulesFromDB(userId, userIdx)
            AlarmEventLog.saveLastSync(this)
            return
        }

        val enabled = when (type) {
            "SCHEDULE_OVERDUE" -> Session.notifScheduleEnabled
            "REPEAT_QUESTION"  -> Session.notifRepeatEnabled
            else               -> false
        }
        if (!enabled) return

        val (title, body) = when (type) {
            "SCHEDULE_OVERDUE"  -> "일정 미수행" to "${data["userName"] ?: "사용자"}님이 일정을 시작하지 않았어요"
            "REPEAT_QUESTION"   -> "도움 요청"   to "${data["userName"] ?: "사용자"}님이 어려워하고 있어요"
            else                -> return
        }

        showNotification(title, body, data)
    }

    private fun showNotification(title: String, body: String, data: Map<String, String>) {
        val channelId = "nungil_schedule"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "일정 알림", NotificationManager.IMPORTANCE_HIGH)
            manager.createNotificationChannel(channel)
        }

        val intent = Intent(this, NotificationDialogActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(NotificationDialogActivity.EXTRA_TYPE,       data["type"] ?: "")
            putExtra(NotificationDialogActivity.EXTRA_USER_NAME,  data["userName"] ?: "사용자")
            putExtra(NotificationDialogActivity.EXTRA_USER_PHONE, data["userPhone"] ?: "")
            putExtra(NotificationDialogActivity.EXTRA_SCHEDULE_ID, data["scheduleId"] ?: "")
        }
        val notifId = notifIdCounter.getAndIncrement()
        val pendingIntent = PendingIntent.getActivity(
            this, notifId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        manager.notify(notifId, notification)
    }

    companion object {
        private val notifIdCounter = AtomicInteger(1)

        /** 보호자 FCM 토큰 서버 등록 */
        fun registerTokenWithServer(token: String) {
            if (!Session.isLoggedIn()) return
            val body = JSONObject().apply {
                put("guardianId", Session.guardianId)
                put("fcmToken", token)
            }.toString()
            ApiClient.put("/v1/guardian/auth/fcm-token", body) { /* fire-and-forget */ }
        }

        /**
         * 사용자 FCM 토큰 서버 등록 — QR 연동 완료 후 & 토큰 갱신 시 호출
         * BASE_URL 이 이미 "~api" 로 끝나므로 path 는 /v1/ 부터 시작
         */
        fun registerUserTokenWithServer(token: String, userId: String, userIdx: Int) {
            val body = JSONObject().apply {
                put("userId", userId)
                put("userIdx", userIdx)
                put("fcmToken", token)
            }.toString()
            ApiClient.put("/v1/user/fcm-token", body) { /* fire-and-forget */ }
        }
    }
}
