package com.example.myapplication.guardian.report

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import com.example.myapplication.R
import com.example.myapplication.core.network.ApiClient
import com.example.myapplication.core.network.ApiResult
import com.example.myapplication.core.network.Session
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class ReportFragment : Fragment() {

    private val displayFmt = SimpleDateFormat("M월 d일 (EEE)", Locale.KOREAN)
    private val apiFmt     = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val currentCal = Calendar.getInstance()

    private lateinit var tvDate:           TextView
    private lateinit var tvCompletionRate: TextView
    private lateinit var tvTotalQuestions: TextView
    private lateinit var tvSelfCompleted:  TextView
    private lateinit var tvAlertTriggered: TextView
    private lateinit var llTaskStats:      LinearLayout
    private lateinit var llTrends:         LinearLayout
    private lateinit var pbReport:         ProgressBar
    private lateinit var tvEmpty:          TextView
    private lateinit var scrollContent:    View

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_report, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        tvDate           = view.findViewById(R.id.tvDate)
        tvCompletionRate = view.findViewById(R.id.tvCompletionRate)
        tvTotalQuestions = view.findViewById(R.id.tvTotalQuestions)
        tvSelfCompleted  = view.findViewById(R.id.tvSelfCompleted)
        tvAlertTriggered = view.findViewById(R.id.tvAlertTriggered)
        llTaskStats      = view.findViewById(R.id.llTaskStats)
        llTrends         = view.findViewById(R.id.llTrends)
        pbReport         = view.findViewById(R.id.pbReport)
        tvEmpty          = view.findViewById(R.id.tvEmpty)
        scrollContent    = view.findViewById(R.id.scrollContent)

        view.findViewById<ImageButton>(R.id.btnPrevDay).setOnClickListener {
            currentCal.add(Calendar.DAY_OF_MONTH, -1); loadReport()
        }
        view.findViewById<ImageButton>(R.id.btnNextDay).setOnClickListener {
            currentCal.add(Calendar.DAY_OF_MONTH, 1); loadReport()
        }

        loadReport()
        loadTrends()
    }

    private fun loadTrends() {
        val id  = Session.guardianId
        val idx = Session.userIdx
        ApiClient.get("/v1/guardian/report/task-trends?id=$id&idx=$idx&days=14") { result ->
            activity?.runOnUiThread {
                if (result is ApiResult.Success) {
                    try {
                        val arr = JSONObject(result.data).getJSONArray("result")
                        llTrends.removeAllViews()
                        for (i in 0 until arr.length()) {
                            val item     = arr.getJSONObject(i)
                            val taskName = item.optString("taskName", "과업")
                            val label    = item.optString("label", "진행 중")
                            val trend    = item.optString("trend", "ongoing")
                            val rate     = item.optInt("completionRate", 0)
                            val avgQ     = item.optDouble("avgQuestions", 0.0)
                            llTrends.addView(buildTrendRow(taskName, label, trend, rate, avgQ))
                        }
                    } catch (_: Exception) {}
                }
            }
        }
    }

    private fun buildTrendRow(taskName: String, label: String, trend: String, rate: Int, avgQ: Double): View {
        val (bgColor, emoji) = when (trend) {
            "good"       -> Pair("#E8F5E9", "잘해요")
            "improving"  -> Pair("#E3F2FD", "나아지는중")
            "hard"       -> Pair("#FFF3E0", "어려워요")
            "struggling" -> Pair("#FCE4EC", "못하겠어요")
            else         -> Pair("#F5F5F5", "진행중")
        }

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setBackgroundColor(Color.parseColor(bgColor))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.setMargins(0, 0, 0, dp(2)) }
        }

        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val tvName = TextView(context).apply {
            text = taskName
            textSize = 15f
            setTextColor(Color.parseColor("#333333"))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val labelColor = when (trend) {
            "good"       -> "#2E7D32"
            "improving"  -> "#1565C0"
            "hard"       -> "#E65100"
            "struggling" -> "#C62828"
            else         -> "#616161"
        }
        val tvLabel = TextView(context).apply {
            text = emoji
            textSize = 11f
            setTextColor(Color.WHITE)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(dp(8), dp(3), dp(8), dp(3))
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(10).toFloat()
                setColor(Color.parseColor(labelColor))
            }
        }
        header.addView(tvName)
        header.addView(tvLabel)
        card.addView(header)

        val tvSub = TextView(context).apply {
            text = "완료율 $rate%  ·  평균 질문 ${String.format("%.1f", avgQ)}회"
            textSize = 11f
            setTextColor(Color.parseColor("#888888"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = dp(4) }
        }
        card.addView(tvSub)
        return card
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun loadReport() {
        val dateStr = apiFmt.format(currentCal.time)
        val today   = apiFmt.format(Calendar.getInstance().time)
        tvDate.text = if (dateStr == today) "오늘 · ${displayFmt.format(currentCal.time)}"
                      else displayFmt.format(currentCal.time)

        pbReport.visibility      = View.VISIBLE
        scrollContent.visibility = View.GONE
        tvEmpty.visibility       = View.GONE

        val id  = Session.guardianId
        val idx = Session.userIdx
        ApiClient.get("/v1/guardian/report/daily?id=$id&idx=$idx&date=$dateStr") { result ->
            activity?.runOnUiThread {
                pbReport.visibility = View.GONE
                when (result) {
                    is ApiResult.Success -> renderReport(result.data)
                    is ApiResult.Error   -> {
                        tvEmpty.text = "데이터를 불러오지 못했어요."
                        tvEmpty.visibility = View.VISIBLE
                    }
                }
            }
        }
    }

    private fun renderReport(data: String) {
        try {
            val json = JSONObject(data)
            if (json.optString("status") != "SUCCESS") {
                tvEmpty.text = "보고서 데이터가 없어요."
                tvEmpty.visibility = View.VISIBLE
                return
            }

            val r        = json.getJSONObject("result")
            val total    = r.optInt("totalSchedules", 0)
            val rate     = r.optInt("completionRate", 0)
            val questions = r.optInt("totalQuestions", 0)
            val selfDone  = r.optInt("selfCompletedCount", 0)
            val alerts    = r.optInt("alertTriggeredCount", 0)
            val taskArr  = r.optJSONArray("taskStats")

            if (total == 0) {
                tvEmpty.visibility = View.VISIBLE
                return
            }

            tvCompletionRate.text = "$rate%"
            tvTotalQuestions.text = "$questions"
            tvSelfCompleted.text  = "$selfDone"
            tvAlertTriggered.text = "$alerts"

            llTaskStats.removeAllViews()
            if (taskArr != null) {
                val maxQ = (0 until taskArr.length())
                    .maxOfOrNull { taskArr.getJSONObject(it).optInt("questionCount", 0) }
                    ?.coerceAtLeast(1) ?: 1

                for (i in 0 until taskArr.length()) {
                    val stat       = taskArr.getJSONObject(i)
                    val taskName   = stat.optString("taskName", "과업")
                    val taskStatus = stat.optString("status", "pending")
                    val qCount     = stat.optInt("questionCount", 0)
                    val selfDoneRow = stat.optBoolean("selfCompleted", false)
                    val alertRow   = stat.optBoolean("alertTriggered", false)

                    llTaskStats.addView(buildTaskRow(taskName, taskStatus, qCount, maxQ, selfDoneRow, alertRow))

                    if (i < taskArr.length() - 1) {
                        val divider = View(context).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT, 1
                            ).also { it.setMargins(0, 8, 0, 8) }
                            setBackgroundColor(Color.parseColor("#F0F0F0"))
                        }
                        llTaskStats.addView(divider)
                    }
                }
            }

            scrollContent.visibility = View.VISIBLE

        } catch (e: Exception) {
            tvEmpty.text = "응답 처리 오류"
            tvEmpty.visibility = View.VISIBLE
        }
    }

    private fun buildTaskRow(
        taskName: String, status: String, qCount: Int,
        maxQ: Int, selfDone: Boolean, alert: Boolean
    ): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.setMargins(0, 4, 0, 4) }
        }

        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        val tvName = TextView(context).apply {
            text = taskName; textSize = 14f
            setTextColor(Color.parseColor("#333333"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val icon = when {
            alert    -> "🔔"
            selfDone -> "✅"
            status == "completed"   -> "✔"
            status == "in_progress" -> "▶"
            else -> "○"
        }
        val tvStatus = TextView(context).apply { text = icon; textSize = 16f }
        header.addView(tvName); header.addView(tvStatus)
        row.addView(header)

        val barRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = 4 }
        }
        val pb = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = maxQ; progress = qCount
            layoutParams = LinearLayout.LayoutParams(0, 16, 1f)
            progressDrawable.setColorFilter(
                if (alert) Color.parseColor("#FF5252") else Color.parseColor("#6366F1"),
                android.graphics.PorterDuff.Mode.SRC_IN
            )
        }
        val tvQ = TextView(context).apply {
            text = "질문 ${qCount}회"; textSize = 11f
            setTextColor(Color.parseColor("#888888"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.marginStart = 8 }
        }
        barRow.addView(pb); barRow.addView(tvQ)
        row.addView(barRow)
        return row
    }

    override fun onResume() {
        super.onResume()
        if (::tvDate.isInitialized) {
            loadReport()
            loadTrends()
        }
    }
}
