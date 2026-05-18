package com.example.myapplication.guardian.schedule.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.R
import com.example.myapplication.guardian.schedule.viewmodel.ScheduleViewModel
import com.example.myapplication.model.Schedule
import com.google.android.material.chip.ChipGroup
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.text.SimpleDateFormat
import java.util.*

class ScheduleFragment : Fragment() {

    private val viewModel: ScheduleViewModel by viewModels()
    private lateinit var adapter: ScheduleAdapter
    private lateinit var timelineView: CircularTimelineView
    private lateinit var tvDate: TextView
    private lateinit var tvSelectedTime: TextView
    private lateinit var tvScheduleHeader: TextView
    private lateinit var tvEmpty: TextView

    private val displayDateFmt = SimpleDateFormat("M월 d일 (EEE)", Locale.KOREAN)
    private val filterDateFmt  = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    private val currentCal = Calendar.getInstance()
    private var allSchedules = listOf<Schedule>()

    // 다이얼에서 선택된 시간 (FAB 누를 때 ScheduleAddActivity로 전달)
    private var selectedHour   = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    private var selectedMinute = 0

    // 필터 칩: "all" | "pending" | "completed"
    private var currentFilter = "all"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_schedule, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        timelineView      = view.findViewById(R.id.timelineView)
        tvDate            = view.findViewById(R.id.tvDate)
        tvSelectedTime    = view.findViewById(R.id.tvSelectedTime)
        tvScheduleHeader  = view.findViewById(R.id.tvScheduleHeader)
        tvEmpty           = view.findViewById(R.id.tvEmpty)

        val rv           = view.findViewById<RecyclerView>(R.id.rvSchedule)
        val fab          = view.findViewById<FloatingActionButton>(R.id.fabAdd)
        val btnPrev      = view.findViewById<ImageButton>(R.id.btnPrevDay)
        val btnNext      = view.findViewById<ImageButton>(R.id.btnNextDay)
        val chipGroup    = view.findViewById<ChipGroup>(R.id.chipGroupFilter)

        // 다이얼 설정
        timelineView.displayMode = true
        timelineView.setTime(selectedHour, 0)

        // 다이얼 드래그 → 선택 시간 업데이트
        timelineView.onTimeChanged = { h, m ->
            selectedHour   = h
            selectedMinute = m
            tvSelectedTime.text = "%02d:%02d".format(h, m)
            // 메인 다이얼은 displayMode이므로 중앙 텍스트 유지
        }

        // RecyclerView
        adapter = ScheduleAdapter(
            mutableListOf(),
            onDelete = { schedule -> confirmDelete(schedule) },
            onEdit   = { schedule -> showEditDialog(schedule) }
        )
        rv.layoutManager = LinearLayoutManager(context)
        rv.adapter = adapter

        // 필터 칩
        chipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            currentFilter = when {
                checkedIds.contains(R.id.chipPending)  -> "pending"
                checkedIds.contains(R.id.chipDone)     -> "completed"
                else                                   -> "all"
            }
            refreshDateDisplay()
        }

        // 날짜 이동 버튼
        btnPrev.setOnClickListener {
            currentCal.add(Calendar.DAY_OF_MONTH, -1)
            refreshDateDisplay()
        }
        btnNext.setOnClickListener {
            currentCal.add(Calendar.DAY_OF_MONTH, 1)
            refreshDateDisplay()
        }

        // FAB → 선택된 날짜 + 시간을 넘겨서 일정 추가 화면 열기
        fab.setOnClickListener {
            val intent = Intent(context, ScheduleAddActivity::class.java).apply {
                putExtra(ScheduleAddActivity.EXTRA_DATE,   filterDateFmt.format(currentCal.time))
                putExtra(ScheduleAddActivity.EXTRA_HOUR,   selectedHour)
                putExtra(ScheduleAddActivity.EXTRA_MINUTE, selectedMinute)
            }
            startActivity(intent)
        }

        // LiveData 관찰
        viewModel.schedules.observe(viewLifecycleOwner) { list ->
            allSchedules = list
            refreshDateDisplay()
        }
        viewModel.error.observe(viewLifecycleOwner) { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
        viewModel.deleteSuccess.observe(viewLifecycleOwner) { ok ->
            if (ok) Toast.makeText(context, "일정이 삭제됐어요.", Toast.LENGTH_SHORT).show()
        }
        viewModel.updateSuccess.observe(viewLifecycleOwner) { ok ->
            if (ok) Toast.makeText(context, "일정 시간이 변경됐어요.", Toast.LENGTH_SHORT).show()
        }

        // 초기 날짜 표시
        refreshDateDisplay()
    }

    private fun refreshDateDisplay() {
        val dateKey = filterDateFmt.format(currentCal.time)

        // 날짜 텍스트
        val cal = Calendar.getInstance()
        val todayKey = filterDateFmt.format(cal.time)
        tvDate.text = when (dateKey) {
            todayKey -> "오늘 · ${displayDateFmt.format(currentCal.time)}"
            else     -> displayDateFmt.format(currentCal.time)
        }

        // 해당 날짜 전체 일정 (다이얼용)
        val daySchedules = allSchedules.filter { it.scheduledAt.startsWith(dateKey) }

        // 다이얼에 표시 (필터 무관하게 항상 전체 표시)
        timelineView.setSchedules(daySchedules)
        val count = daySchedules.size
        val label = if (dateKey == todayKey) "오늘 ${count}개" else "${count}개"
        timelineView.centerLabel = label

        // 목록용 필터 적용
        val filtered = when (currentFilter) {
            "pending"   -> daySchedules.filter { it.status == "pending" }
            "completed" -> daySchedules.filter { it.status == "completed" || it.status == "in_progress" }
            else        -> daySchedules
        }

        // 헤더 텍스트
        val filterLabel = when (currentFilter) {
            "pending"   -> "예정 ${filtered.size}개"
            "completed" -> "완료 ${filtered.size}개"
            else        -> "일정 ${count}개"
        }
        tvScheduleHeader.text = "${displayDateFmt.format(currentCal.time)} $filterLabel"

        if (filtered.isEmpty()) {
            tvEmpty.visibility = View.VISIBLE
            adapter.updateList(emptyList())
        } else {
            tvEmpty.visibility = View.GONE
            adapter.updateList(filtered.sortedBy { it.scheduledAt })
        }
    }

    private fun confirmDelete(schedule: Schedule) {
        AlertDialog.Builder(requireContext())
            .setTitle("일정 삭제")
            .setMessage("'${schedule.taskName}' 일정을 삭제할까요?")
            .setPositiveButton("삭제") { _, _ -> viewModel.deleteSchedule(schedule.scheduleId) }
            .setNegativeButton("취소", null)
            .show()
    }

    // SM-002: 일정 시간 수정 다이얼로그
    private fun showEditDialog(schedule: Schedule) {
        val parts = schedule.scheduledAt.split("T")
        val currentDate = parts.getOrElse(0) { filterDateFmt.format(Calendar.getInstance().time) }
        val timeParts   = parts.getOrElse(1) { "09:00:00" }.split(":")
        var editHour    = timeParts.getOrElse(0) { "9" }.toIntOrNull() ?: 9
        var editMinute  = timeParts.getOrElse(1) { "0" }.toIntOrNull() ?: 0
        var editDate    = currentDate

        AlertDialog.Builder(requireContext())
            .setTitle("'${schedule.taskName}' 일정 수정")
            .setMessage("날짜: $editDate\n시간: %02d:%02d\n\n날짜 또는 시간을 변경할 수 있어요.".format(editHour, editMinute))
            .setNeutralButton("날짜 변경") { _, _ ->
                val cal = Calendar.getInstance()
                DatePickerDialog(requireContext(), { _, y, m, d ->
                    editDate = "%04d-%02d-%02d".format(y, m + 1, d)
                    TimePickerDialog(requireContext(), { _, h, min ->
                        viewModel.updateScheduleTime(schedule.scheduleId, editDate, "%02d:%02d".format(h, min))
                    }, editHour, editMinute, true).show()
                }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
            }
            .setPositiveButton("시간만 변경") { _, _ ->
                TimePickerDialog(requireContext(), { _, h, min ->
                    viewModel.updateScheduleTime(schedule.scheduleId, editDate, "%02d:%02d".format(h, min))
                }, editHour, editMinute, true).show()
            }
            .setNegativeButton("취소", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadSchedules()
    }
}
