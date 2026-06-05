package com.example.myapplication.guardian.schedule.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.R
import com.example.myapplication.model.Schedule

class ScheduleAdapter(
    private val onDelete: (Schedule) -> Unit = {},
    private val onEdit:   (Schedule) -> Unit = {}
) : ListAdapter<Schedule, ScheduleAdapter.ViewHolder>(DIFF_CALLBACK) {

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<Schedule>() {
            override fun areItemsTheSame(oldItem: Schedule, newItem: Schedule) =
                oldItem.scheduleId == newItem.scheduleId
            override fun areContentsTheSame(oldItem: Schedule, newItem: Schedule) =
                oldItem == newItem
        }
    }

    /** 기존 호출부(ScheduleFragment.adapter.updateList)와 인터페이스 유지 */
    fun updateList(newList: List<Schedule>) = submitList(newList)

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTaskName:    TextView    = view.findViewById(R.id.tvTaskName)
        val tvTimeHour:    TextView    = view.findViewById(R.id.tvTimeHour)
        val tvTimeDateOnly:TextView    = view.findViewById(R.id.tvTimeDateOnly)
        val tvStatus:      TextView    = view.findViewById(R.id.tvStatus)
        val btnDelete:     ImageButton = view.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_schedule, parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val s = getItem(position)
        holder.tvTaskName.text = s.taskName

        // scheduledAt 파싱: "2025-01-15T14:30:00" 형태 (T 없는 포맷 방어 처리)
        if (s.scheduledAt.contains("T")) {
            val parts = s.scheduledAt.split("T")
            holder.tvTimeDateOnly.text = parts.getOrNull(0) ?: ""
            holder.tvTimeHour.text     = parts.getOrNull(1)?.take(5) ?: ""
        } else {
            holder.tvTimeHour.text     = s.scheduledAt.take(5)
            holder.tvTimeDateOnly.text = ""
        }

        // 상태 뱃지
        val statusText = when (s.status) {
            "completed"   -> "완료"
            "in_progress" -> "진행중"
            "pending"     -> "예정"
            else          -> s.status
        }
        holder.tvStatus.text = statusText
        holder.tvStatus.setTextColor(0xFFFFFFFF.toInt())
        holder.tvStatus.setBackgroundResource(
            when (s.status) {
                "completed"   -> R.drawable.bg_status_completed
                "in_progress" -> R.drawable.bg_status_in_progress
                else          -> R.drawable.bg_status_pending
            }
        )

        // completed 일정은 흐리게
        holder.itemView.alpha = if (s.status == "completed") 0.65f else 1.0f

        // 삭제·수정: pending만 가능
        if (s.status == "pending") {
            holder.btnDelete.visibility = View.VISIBLE
            holder.btnDelete.setOnClickListener { onDelete(s) }
            holder.itemView.setOnClickListener { onEdit(s) }
        } else {
            holder.btnDelete.visibility = View.INVISIBLE
            holder.itemView.setOnClickListener(null)
        }
    }
}
