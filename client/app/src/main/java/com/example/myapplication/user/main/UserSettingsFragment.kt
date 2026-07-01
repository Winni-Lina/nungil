package com.example.myapplication.user.main

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.myapplication.R
import com.example.myapplication.RoleSelectActivity
import com.example.myapplication.user.schedule.service.AlarmEventLog
import com.example.myapplication.user.schedule.service.ScheduleManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class UserSettingsFragment : Fragment() {

    private val dateFmt = SimpleDateFormat("M월 d일 HH:mm", Locale.KOREAN)

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_user_settings, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val prefs = requireContext().getSharedPreferences("nungil_prefs", 0)
        val tvLastSync = view.findViewById<TextView>(R.id.tvLastSyncSettings)

        // 마지막 동기화 시각 표시
        refreshLastSync(tvLastSync)

        // 지금 동기화 버튼
        view.findViewById<View>(R.id.layoutSync).setOnClickListener {
            val userId  = prefs.getString("user_id", null) ?: return@setOnClickListener
            val userIdx = prefs.getInt("user_idx", -1).takeIf { it >= 0 } ?: return@setOnClickListener
            Toast.makeText(requireContext(), "확인하고 있어요...", Toast.LENGTH_SHORT).show()
            Thread {
                ScheduleManager(requireContext()).syncSchedulesFromDB(userId, userIdx)
                AlarmEventLog.saveLastSync(requireContext())
                requireActivity().runOnUiThread {
                    refreshLastSync(tvLastSync)
                    Toast.makeText(requireContext(), "알람을 다시 맞췄어요!", Toast.LENGTH_SHORT).show()
                }
            }.start()
        }

        // 로그아웃
        view.findViewById<View>(R.id.layoutLogout).setOnClickListener {
            prefs.edit()
                .remove("user_id")
                .remove("user_idx")
                .apply()
            startActivity(Intent(requireContext(), RoleSelectActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
        }
    }

    override fun onResume() {
        super.onResume()
        view?.findViewById<TextView>(R.id.tvLastSyncSettings)?.let { refreshLastSync(it) }
    }

    private fun refreshLastSync(tv: TextView) {
        val last = AlarmEventLog.getLastSyncMillis(requireContext())
        tv.text = if (last == 0L) "마지막 확인: 없음"
                  else "마지막 확인: ${dateFmt.format(Date(last))}"
    }
}
