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

    private val calendar = Calendar.getInstance()
    private val dateFormat = SimpleDateFormat("yyyy년 M월 d일 (E)", Locale.KOREAN)

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

        view.findViewById<View>(R.id.btnPrevDay).setOnClickListener {
            calendar.add(Calendar.DAY_OF_MONTH, -1)
            updateDateAndLoad()
        }
        view.findViewById<View>(R.id.btnNextDay).setOnClickListener {
            calendar.add(Calendar.DAY_OF_MONTH, 1)
            updateDateAndLoad()
        }

        view.findViewById<FloatingActionButton>(R.id.fabNungil).setOnClickListener {
            startActivity(Intent(requireContext(), UserChatActivity::class.java))
        }

        updateDateAndLoad()
    }

    override fun onResume() {
        super.onResume()
        loadSchedules()
    }

    private fun updateDateAndLoad() {
        tvDate.text = dateFormat.format(calendar.time)
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
            requireActivity().runOnUiThread {
                pbLoading.visibility = View.GONE
                if (items.isEmpty()) {
                    tvEmpty.visibility = View.VISIBLE
                } else {
                    rvSchedule.visibility = View.VISIBLE
                    rvSchedule.adapter = UserScheduleAdapter(items) { item ->
                        val intent = Intent(requireContext(), UserChatActivity::class.java).apply {
                            putExtra("schedule_id", item.scheduleId)
                            putExtra("schedule_title", item.title)
                        }
                        startActivity(intent)
                    }
                }
            }
        }.start()
    }
}
