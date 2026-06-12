package com.example.myapplication.user.main

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.R
import com.example.myapplication.user.schedule.service.AlarmEventLog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class UserNotificationFragment : Fragment() {

    private lateinit var rvNotifications: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var tvLastSync: TextView
    private val dateFmt = SimpleDateFormat("M월 d일 HH:mm", Locale.KOREAN)

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_user_notification, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        rvNotifications = view.findViewById(R.id.rvNotifications)
        tvEmpty         = view.findViewById(R.id.tvEmpty)
        tvLastSync      = view.findViewById(R.id.tvLastSync)

        rvNotifications.layoutManager = LinearLayoutManager(requireContext())
        rvNotifications.addItemDecoration(
            DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL)
        )

        view.findViewById<TextView>(R.id.tvClear).setOnClickListener {
            AlarmEventLog.clear(requireContext())
            refresh()
        }

        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val events = AlarmEventLog.readAll(requireContext())

        // 마지막 동기화
        val lastSync = AlarmEventLog.getLastSyncMillis(requireContext())
        tvLastSync.text = if (lastSync == 0L) "마지막 동기화: 없음"
                          else "마지막 동기화: ${dateFmt.format(Date(lastSync))}"

        if (events.isEmpty()) {
            tvEmpty.visibility    = View.VISIBLE
            rvNotifications.visibility = View.GONE
            return
        }
        tvEmpty.visibility    = View.GONE
        rvNotifications.visibility = View.VISIBLE
        rvNotifications.adapter = NotificationAdapter(events)
    }

    // ── Adapter ──────────────────────────────────────────────────────────────

    inner class NotificationAdapter(
        private val items: List<AlarmEventLog.AlarmEvent>
    ) : RecyclerView.Adapter<NotificationAdapter.VH>() {

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val tvIcon:   TextView = v.findViewById(R.id.tvIcon)
            val tvTitle:  TextView = v.findViewById(R.id.tvTitle)
            val tvTime:   TextView = v.findViewById(R.id.tvTime)
            val tvStatus: TextView = v.findViewById(R.id.tvStatus)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_notification, parent, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.tvTitle.text = item.title
            holder.tvTime.text  = dateFmt.format(Date(item.firedAtMillis))

            val (icon, label, colorRes) = when (item.status) {
                AlarmEventLog.Status.FIRED     -> Triple("🔔", "울림",   R.color.colorPrimary)
                AlarmEventLog.Status.COMPLETED -> Triple("✅", "완료",   R.color.statusCompleted)
                AlarmEventLog.Status.MISSED    -> Triple("⚠️", "놓침",   R.color.statusPending)
            }
            holder.tvIcon.text   = icon
            holder.tvStatus.text = label

            val bg = GradientDrawable().apply {
                shape        = GradientDrawable.RECTANGLE
                cornerRadius = 20f
                setColor(ContextCompat.getColor(holder.itemView.context, colorRes))
            }
            holder.tvStatus.background = bg
        }
    }
}
