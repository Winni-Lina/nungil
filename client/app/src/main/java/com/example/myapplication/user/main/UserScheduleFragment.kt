package com.example.myapplication.user.main

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.R
import com.example.myapplication.user.chat.UserChatActivity
import com.example.myapplication.user.schedule.data.ScheduleRepository
import com.example.myapplication.user.schedule.service.ScheduleManager
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class UserScheduleFragment : Fragment() {

    private lateinit var tvDate: TextView
    private lateinit var tvEmpty: TextView
    private lateinit var pbLoading: ProgressBar
    private lateinit var rvSchedule: RecyclerView
    private lateinit var btnPrevDay: View

    private val calendar = Calendar.getInstance()
    private val dateFormat = SimpleDateFormat("yyyy년 M월 d일 (E)", Locale.KOREAN)

    // 서버가 오늘 이전 일정을 내려주지 않으므로, 오늘보다 과거로는 못 가게 막는다
    private fun isToday(cal: Calendar): Boolean {
        val now = Calendar.getInstance()
        return cal.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
               cal.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_user_schedule, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvDate     = view.findViewById(R.id.tvDate)
        tvEmpty    = view.findViewById(R.id.tvEmpty)
        pbLoading  = view.findViewById(R.id.pbLoading)
        rvSchedule = view.findViewById(R.id.rvSchedule)

        rvSchedule.layoutManager = LinearLayoutManager(requireContext())
        btnPrevDay = view.findViewById(R.id.btnPrevDay)

        btnPrevDay.setOnClickListener {
            if (!isToday(calendar)) {
                calendar.add(Calendar.DAY_OF_MONTH, -1)
                updateDateAndLoad()
            }
        }
        view.findViewById<View>(R.id.btnNextDay).setOnClickListener {
            calendar.add(Calendar.DAY_OF_MONTH, 1)
            updateDateAndLoad()
        }

        view.findViewById<FloatingActionButton>(R.id.fabNungil).setOnClickListener {
            startActivity(Intent(requireContext(), UserChatActivity::class.java))
        }

        updateDateLabel()
    }

    override fun onResume() {
        super.onResume()
        loadSchedules()
    }

    private fun updateDateLabel() {
        tvDate.text = dateFormat.format(calendar.time)
        btnPrevDay.alpha = if (isToday(calendar)) 0.3f else 1f
    }

    private fun updateDateAndLoad() {
        updateDateLabel()
        loadSchedules()
    }

    private fun loadSchedules() {
        val prefs   = requireContext().getSharedPreferences("nungil_prefs", 0)
        val userId  = prefs.getString("user_id", null) ?: return
        val userIdx = prefs.getInt("user_idx", -1)
        if (userIdx == -1) return

        pbLoading.visibility  = View.VISIBLE
        rvSchedule.visibility = View.GONE
        tvEmpty.visibility    = View.GONE

        Thread {
            val items = ScheduleRepository.fetchTodaySchedules(userId, userIdx)
            ScheduleManager(requireContext()).registerAlarms(items)

            // 서버 쿼리는 오늘 이후 미래 일정도 같이 내려주므로(알람 등록용), 화면 표시는 선택된 날짜만 필터링
            val dayStart = (calendar.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val dayEnd = dayStart + 24 * 60 * 60 * 1000L
            val dayItems = items.filter { it.triggerTimeMillis in dayStart until dayEnd }

            if (!isAdded) return@Thread
            requireActivity().runOnUiThread {
                if (!isAdded) return@runOnUiThread
                pbLoading.visibility = View.GONE
                if (dayItems.isEmpty()) {
                    tvEmpty.visibility = View.VISIBLE
                } else {
                    rvSchedule.visibility = View.VISIBLE
                    rvSchedule.adapter = UserScheduleAdapter(dayItems) { item ->
                        val intent = Intent(requireContext(), UserChatActivity::class.java).apply {
                            putExtra("schedule_id", item.scheduleId.toString())
                            putExtra("schedule_title", item.title)
                            putExtra("schedule_auto_execute", true)
                        }
                        startActivity(intent)
                    }
                }
            }
        }.start()
    }
}
