package com.example.myapplication.guardian.schedule.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import com.example.myapplication.model.Schedule
import java.util.Calendar
import kotlin.math.*

class CircularTimelineView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var displayMode: Boolean = false
        set(v) { field = v; invalidate() }

    var centerLabel: String? = null
        set(v) { field = v; invalidate() }

    private var selectedHour   = 9
    private var selectedMinute = 0
    private var schedules      = listOf<Schedule>()

    var onTimeChanged: ((hour: Int, minute: Int) -> Unit)? = null

    private fun dp(v: Float) = v * resources.displayMetrics.density
    private fun sp(v: Float) = v * resources.displayMetrics.scaledDensity

    private var cx = 0f; private var cy = 0f
    private var outerR = 0f
    private var ringW  = 0f
    private val arcOval    = RectF()
    private val shadowOval = RectF()

    private val bgCirclePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#F6F8FF")
    }
    private val ringBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#DDE4F5")
    }
    private val blockPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style     = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val tickSmallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color       = Color.parseColor("#9EA9C8")
        style       = Paint.Style.STROKE
        strokeWidth = 1.5f
    }
    private val tickBigPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color       = Color.parseColor("#7D8AAD")
        style       = Paint.Style.STROKE
        strokeWidth = 2.5f
    }
    private val hourLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color     = Color.parseColor("#6B789B")
        textAlign = Paint.Align.CENTER
        typeface  = Typeface.DEFAULT_BOLD
    }
    private val centerMainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color     = Color.parseColor("#5B688A")
        textAlign = Paint.Align.CENTER
        typeface  = Typeface.DEFAULT_BOLD
    }
    private val centerSubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color     = Color.parseColor("#92A0C1")
        textAlign = Paint.Align.CENTER
    }
    private val selDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#90A4E8")
        style = Paint.Style.FILL
    }
    private val selDotRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color       = Color.parseColor("#90A4E8")
        style       = Paint.Style.STROKE
        strokeWidth = 2f
        alpha       = 100
    }
    private val nowDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D95F74")
        style = Paint.Style.FILL
    }
    private val nowHandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color       = Color.parseColor("#D95F74")
        style       = Paint.Style.STROKE
        alpha       = 140
    }
    private val nowLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color     = Color.parseColor("#D95F74")
        textAlign = Paint.Align.CENTER
        typeface  = Typeface.DEFAULT_BOLD
    }
    private val blockLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color     = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface  = Typeface.DEFAULT_BOLD
    }
    private val blockIconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#EEF2FF")
    }

    // 과업별 비비드 컬러 팔레트
    private val vividPalette = listOf(
        Color.parseColor("#1FA2C9"), Color.parseColor("#0E6FB8"), Color.parseColor("#2BB8D6"),
        Color.parseColor("#E94E5C"), Color.parseColor("#15497A"), Color.parseColor("#3DC1D3"),
    )
    private fun colorForTask(taskId: Int) = vividPalette[abs(taskId) % vividPalette.size]

    private fun iconForTask(taskName: String): String = when {
        taskName.contains("양치") || taskName.contains("세면") -> "🪥"
        taskName.contains("밥") || taskName.contains("식사") || taskName.contains("끓이") -> "🍚"
        taskName.contains("빨래") || taskName.contains("세탁") -> "🧺"
        taskName.contains("청소") -> "🧹"
        taskName.contains("설거지") -> "🍽"
        taskName.contains("씻기") || taskName.contains("손") -> "🧼"
        taskName.contains("옷") -> "👕"
        taskName.contains("신발") -> "👟"
        taskName.contains("약") -> "💊"
        taskName.contains("운동") || taskName.contains("헬스") -> "🏋"
        taskName.contains("공부") || taskName.contains("학습") -> "📚"
        taskName.contains("만나") || taskName.contains("약속") -> "🤝"
        else -> "📋"
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        cx = w / 2f; cy = h / 2f
        val size = minOf(w, h).toFloat()
        ringW  = size * 0.082f          // 바깥 라벨이 카드 경계에 잘리지 않도록 여백 확보
        outerR = size * 0.31f

        ringBgPaint.strokeWidth  = ringW
        blockPaint.strokeWidth   = ringW * 0.85f

        val outerOvalR = outerR + ringW / 2
        arcOval.set(cx - outerR, cy - outerR, cx + outerR, cy + outerR)
        shadowOval.set(cx - outerOvalR - dp(2f), cy - outerOvalR - dp(2f),
            cx + outerOvalR + dp(2f), cy + outerOvalR + dp(2f))

        nowHandPaint.strokeWidth = dp(1.5f)
    }

    override fun onDraw(canvas: Canvas) {
        val size = minOf(width, height).toFloat()

        canvas.drawCircle(cx, cy, size / 2f - dp(4f), bgCirclePaint)
        canvas.drawArc(arcOval, -90f, 360f, false, ringBgPaint)
        drawTicks(canvas)
        schedules.forEach { drawBlock(canvas, it) }

        if (displayMode) drawNowHand(canvas)
        else             drawSelectionDot(canvas)

        drawCenter(canvas)
        drawHourLabels(canvas)
    }

    private fun drawTicks(canvas: Canvas) {
        // 6시간 단위 큰 눈금만 표시 (작은 눈금은 시각적으로 복잡해서 제거)
        for (h in 0 until 24 step 6) {
            val angle   = Math.toRadians(timeToAngle(h, 0).toDouble())
            val tickLen = ringW * 0.55f
            val r1 = outerR - tickLen / 2
            val r2 = outerR + tickLen / 2
            canvas.drawLine(
                (cx + r1 * cos(angle)).toFloat(), (cy + r1 * sin(angle)).toFloat(),
                (cx + r2 * cos(angle)).toFloat(), (cy + r2 * sin(angle)).toFloat(),
                tickBigPaint
            )
        }
    }

    private fun drawBlock(canvas: Canvas, s: Schedule) {
        try {
            val t = s.scheduledAt.split("T").getOrNull(1) ?: return
            val p = t.split(":")
            val h = p[0].toInt(); val m = p[1].toInt()

            // 완료 상태는 옅게, 그 외엔 과업별 비비드 컬러
            val baseColor = colorForTask(s.taskId)
            val color = if (s.status == "completed") {
                Color.argb(140, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor))
            } else baseColor
            blockPaint.color = color

            val startAngle = timeToAngle(h, m)
            val sweepAngle = max(360f / 24f * 1.4f, 16f)  // 비비드 웨지가 잘 보이도록 확대

            canvas.drawArc(arcOval, startAngle, sweepAngle, false, blockPaint)

            // 웨지 중앙에 아이콘만 표시 (바깥 텍스트 라벨은 카드 경계에 잘려서 제거)
            val midAngle = Math.toRadians((startAngle + sweepAngle / 2).toDouble())
            blockIconPaint.textSize = sp(15f)
            val ix = (cx + outerR * cos(midAngle)).toFloat()
            val iy = (cy + outerR * sin(midAngle)).toFloat()
            canvas.drawText(iconForTask(s.taskName), ix, iy + blockIconPaint.textSize / 3, blockIconPaint)

        } catch (e: Exception) {
            Log.e("CircularTimelineView", "drawBlock error: scheduleId=${s.scheduleId}", e)
        }
    }

    private fun drawNowHand(canvas: Canvas) {
        val cal = Calendar.getInstance()
        val h   = cal.get(Calendar.HOUR_OF_DAY)
        val m   = cal.get(Calendar.MINUTE)
        val rad = Math.toRadians(timeToAngle(h, m).toDouble())

        val handEndX = (cx + (outerR + ringW * 0.3f) * cos(rad)).toFloat()
        val handEndY = (cy + (outerR + ringW * 0.3f) * sin(rad)).toFloat()
        val dotX = (cx + outerR * cos(rad)).toFloat()
        val dotY = (cy + outerR * sin(rad)).toFloat()

        canvas.drawLine(cx, cy, handEndX, handEndY, nowHandPaint)
        canvas.drawCircle(dotX, dotY, dp(7f), nowDotPaint)
        val glowPaint = Paint(nowDotPaint).apply { alpha = 60; style = Paint.Style.FILL }
        canvas.drawCircle(dotX, dotY, dp(12f), glowPaint)

        // "지금" 라벨 (도트가 뭔지 알 수 있도록)
        val labelR = outerR + ringW * 0.9f
        val lx = (cx + labelR * cos(rad)).toFloat()
        val ly = (cy + labelR * sin(rad)).toFloat()
        nowLabelPaint.textSize = sp(8f)
        canvas.drawText("지금", lx, ly + nowLabelPaint.textSize / 3, nowLabelPaint)
    }

    private fun drawSelectionDot(canvas: Canvas) {
        val rad  = Math.toRadians(timeToAngle(selectedHour, selectedMinute).toDouble())
        val dotX = (cx + outerR * cos(rad)).toFloat()
        val dotY = (cy + outerR * sin(rad)).toFloat()

        selDotRingPaint.strokeWidth = dp(6f)
        canvas.drawCircle(dotX, dotY, dp(18f), selDotRingPaint)
        canvas.drawCircle(dotX, dotY, dp(10f), selDotPaint)
    }

    private fun drawCenter(canvas: Canvas) {
        val badgeR = outerR - ringW / 2 - dp(8f)
        canvas.drawCircle(cx, cy, badgeR, badgePaint)

        if (displayMode) {
            val label = centerLabel ?: "오늘"
            centerMainPaint.textSize = sp(18f)
            centerSubPaint.textSize  = sp(10f)
            canvas.drawText(label, cx, cy + sp(7f), centerMainPaint)
        } else {
            centerMainPaint.textSize = sp(24f)
            centerSubPaint.textSize  = sp(9f)
            canvas.drawText("%02d:%02d".format(selectedHour, selectedMinute),
                cx, cy + sp(8f), centerMainPaint)
            canvas.drawText("드래그로 시간 선택", cx, cy + sp(22f), centerSubPaint)
        }
    }

    private fun drawHourLabels(canvas: Canvas) {
        hourLabelPaint.textSize = sp(9.5f)
        val labelR = outerR + ringW + dp(14f)
        listOf(0 to "0", 6 to "6", 12 to "12", 18 to "18").forEach { (h, label) ->
            val a  = Math.toRadians(timeToAngle(h, 0).toDouble())
            val x  = (cx + labelR * cos(a)).toFloat()
            val y  = (cy + labelR * sin(a)).toFloat() + hourLabelPaint.textSize / 3
            canvas.drawText(label, x, y, hourLabelPaint)
        }
    }

    private fun timeToAngle(h: Int, m: Int) =
        ((h * 60 + m).toFloat() / (24 * 60)) * 360f - 90f

    private fun angleToTime(deg: Float): Pair<Int, Int> {
        val norm     = (deg + 90f + 360f) % 360f
        val totalMin = ((norm / 360f) * 24 * 60).roundToInt()
        val snapped  = (totalMin / 15) * 15
        return Pair((snapped / 60) % 24, snapped % 60)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val dx   = event.x - cx; val dy = event.y - cy
        val dist = sqrt(dx * dx + dy * dy)
        if (dist < outerR * 0.35f || dist > (outerR + ringW) * 1.4f) return false

        if (event.action == MotionEvent.ACTION_DOWN ||
            event.action == MotionEvent.ACTION_MOVE) {
            val angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
            val (h, m) = angleToTime(angle)
            selectedHour = h; selectedMinute = m
            onTimeChanged?.invoke(h, m)
            invalidate()
            return true
        }
        return true
    }

    fun setTime(hour: Int, minute: Int) {
        selectedHour = hour; selectedMinute = minute; invalidate()
    }

    fun setSchedules(list: List<Schedule>) {
        schedules = list; invalidate()
    }

    fun getSelectedHour()   = selectedHour
    fun getSelectedMinute() = selectedMinute

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(size, size)
    }
}
