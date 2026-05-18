package com.example.myapplication.guardian.report

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
import com.example.myapplication.core.network.ApiResult
import com.example.myapplication.guardian.schedule.data.ScheduleRepository
import com.example.myapplication.guardian.schedule.ui.ScheduleAdapter
import com.example.myapplication.model.Schedule

class ReportFragment : Fragment() {

    private val repository = ScheduleRepository()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_report, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val tvTotal      = view.findViewById<TextView>(R.id.tvTotalCount)
        val tvCompleted  = view.findViewById<TextView>(R.id.tvCompletedCount)
        val tvRate       = view.findViewById<TextView>(R.id.tvCompletionRate)
        val pbCompleted  = view.findViewById<ProgressBar>(R.id.pbCompleted)
        val pbInProgress = view.findViewById<ProgressBar>(R.id.pbInProgress)
        val pbPending    = view.findViewById<ProgressBar>(R.id.pbPending)
        val tvCompLabel  = view.findViewById<TextView>(R.id.tvCompletedLabel)
        val tvProgLabel  = view.findViewById<TextView>(R.id.tvInProgressLabel)
        val tvPendLabel  = view.findViewById<TextView>(R.id.tvPendingLabel)
        val rvCompleted  = view.findViewById<RecyclerView>(R.id.rvCompleted)
        val pbReport     = view.findViewById<ProgressBar>(R.id.pbReport)
        val tvEmpty      = view.findViewById<TextView>(R.id.tvEmpty)

        val completedAdapter = ScheduleAdapter(mutableListOf())
        rvCompleted.layoutManager = LinearLayoutManager(context)
        rvCompleted.adapter = completedAdapter

        pbReport.visibility = View.VISIBLE
        tvEmpty.visibility = View.GONE

        repository.getSchedules { result ->
            activity?.runOnUiThread {
                pbReport.visibility = View.GONE
                when (result) {
                    is ApiResult.Success -> {
                        val all = result.data
                        if (all.isEmpty()) {
                            tvEmpty.visibility = View.VISIBLE
                            return@runOnUiThread
                        }

                        val total       = all.size
                        val completedCnt  = all.count { it.status == "completed" }
                        val inProgCnt     = all.count { it.status == "in_progress" }
                        val pendingCnt    = all.count { it.status == "pending" }
                        val rate = if (total > 0) (completedCnt * 100 / total) else 0

                        tvTotal.text     = "$total"
                        tvCompleted.text = "$completedCnt"
                        tvRate.text      = "$rate%"

                        // 진행률 바 (전체 대비 %)
                        pbCompleted.progress  = if (total > 0) completedCnt * 100 / total else 0
                        pbInProgress.progress = if (total > 0) inProgCnt * 100 / total else 0
                        pbPending.progress    = if (total > 0) pendingCnt * 100 / total else 0
                        tvCompLabel.text  = "$completedCnt"
                        tvProgLabel.text  = "$inProgCnt"
                        tvPendLabel.text  = "$pendingCnt"

                        // 완료 일정만 최신순으로
                        val completedList = all.filter { it.status == "completed" }
                            .sortedByDescending { it.scheduledAt }
                        if (completedList.isEmpty()) {
                            tvEmpty.visibility = View.VISIBLE
                        } else {
                            completedAdapter.updateList(completedList)
                        }
                    }
                    is ApiResult.Error -> {
                        tvEmpty.text = "데이터를 불러올 수 없어요"
                        tvEmpty.visibility = View.VISIBLE
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 탭 전환 시 갱신
        view?.let { onViewCreated(it, null) }
    }
}
