package com.example.myapplication.guardian.notification

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.core.network.ApiClient
import org.json.JSONObject

class NotificationDialogActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TYPE        = "type"
        const val EXTRA_USER_NAME   = "userName"
        const val EXTRA_USER_PHONE  = "userPhone"
        const val EXTRA_SCHEDULE_ID = "scheduleId"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val type       = intent.getStringExtra(EXTRA_TYPE) ?: return finish()
        val userName   = intent.getStringExtra(EXTRA_USER_NAME) ?: "사용자"
        val userPhone  = intent.getStringExtra(EXTRA_USER_PHONE) ?: ""
        val scheduleId = intent.getStringExtra(EXTRA_SCHEDULE_ID) ?: ""

        when (type) {
            "SCHEDULE_OVERDUE" -> showOverdueDialog(userName, userPhone, scheduleId)
            "REPEAT_QUESTION"  -> showRepeatQuestionDialog(userName, userPhone)
            else               -> finish()
        }
    }

    private fun showOverdueDialog(userName: String, userPhone: String, scheduleId: String) {
        val dialog = AlertDialog.Builder(this)
            .setTitle("일정 미수행")
            .setMessage("${userName}님이 일정을 시작하지 않았습니다.\n전화로 확인하시겠어요?")
            .setPositiveButton("전화하기") { _, _ ->
                if (scheduleId.isNotEmpty()) markAbandoned(scheduleId)
                dialPhone(userPhone)
            }
            .setNegativeButton("닫기") { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .create()

        dialog.show()

        if (userPhone.isEmpty()) {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
        }
    }

    private fun showRepeatQuestionDialog(userName: String, userPhone: String) {
        val dialog = AlertDialog.Builder(this)
            .setTitle("도움 요청")
            .setMessage("${userName}님이 어려워하고 있습니다.\n전화로 도와주시겠어요?")
            .setPositiveButton("전화하기") { _, _ -> dialPhone(userPhone) }
            .setNegativeButton("나중에") { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .create()

        dialog.show()

        if (userPhone.isEmpty()) {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
        }
    }

    private fun dialPhone(phone: String) {
        if (phone.isEmpty()) {
            Toast.makeText(this, "등록된 전화번호가 없어요.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))
        finish()
    }

    private fun markAbandoned(scheduleId: String) {
        val body = JSONObject().apply { put("status", "abandoned") }.toString()
        ApiClient.patch("/v1/guardian/schedules/$scheduleId/status", body) { /* fire-and-forget */ }
    }
}
