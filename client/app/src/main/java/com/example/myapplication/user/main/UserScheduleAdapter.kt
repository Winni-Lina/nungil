package com.example.myapplication.user.main

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.R
import com.example.myapplication.user.schedule.data.ScheduleRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class UserScheduleAdapter(
    private val items: List<ScheduleRepository.ScheduleItem>,
    private val onStartClick: (ScheduleRepository.ScheduleItem) -> Unit
) : RecyclerView.Adapter<UserScheduleAdapter.VH>() {

    private val hourFmt = SimpleDateFormat("HH", Locale.getDefault())
    private val minFmt  = SimpleDateFormat("mm분", Locale.getDefault())

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvTimeHour: TextView = view.findViewById(R.id.tvTimeHour)
        val tvTimeMin:  TextView = view.findViewById(R.id.tvTimeMin)
        val tvTaskName: TextView = view.findViewById(R.id.tvTaskName)
        val tvLocation: TextView = view.findViewById(R.id.tvLocation)
        val tvStatus:   TextView = view.findViewById(R.id.tvStatus)
        val btnStart:   Button   = view.findViewById(R.id.btnStart)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_user_schedule, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        val date = Date(item.triggerTimeMillis)
        holder.tvTimeHour.text = hourFmt.format(date)
        holder.tvTimeMin.text  = minFmt.format(date)
        holder.tvTaskName.text = item.title
        holder.tvLocation.text = item.location.ifBlank { "" }
        holder.tvLocation.visibility = if (item.location.isBlank()) View.GONE else View.VISIBLE

        val statusText: String
        val statusColor: Int

        if (item.status == "completed") {
            statusText  = "완료"
            statusColor = ContextCompat.getColor(holder.itemView.context, R.color.statusCompleted)
            holder.btnStart.visibility = View.GONE
        } else {
            statusText  = "예정"
            statusColor = ContextCompat.getColor(holder.itemView.context, R.color.statusPending)
            holder.btnStart.visibility = View.VISIBLE
            holder.btnStart.setOnClickListener { onStartClick(item) }
        }

        holder.tvStatus.text = statusText
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 20f
            setColor(statusColor)
        }
        holder.tvStatus.background = bg
    }
}
