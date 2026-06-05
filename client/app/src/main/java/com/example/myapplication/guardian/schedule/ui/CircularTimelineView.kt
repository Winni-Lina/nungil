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
    private val blockLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color     = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface  = Typeface.DEFAULT_BOLD
    }
    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#EEF2FF")
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        cx = w / 2f; cy = h / 2f
        val size = minOf(w, h).toFloat()
        ringW  = size * 0.095f
        outerR = size * 0.36f

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
        for (h in 0 until 24) {
            val angle   = Math.toRadians(timeToAngle(h, 0).toDouble())
            val isMajor = (h % 6 == 0)
            val paint   = if (isMajor) tickBigPaint else tickSmallPaint
            val tickLen = if (isMajor) ringW * 0.55f else ringW * 0.30f
            val r1 = outerR - tickLen / 2
            val r2 = outerR + tickLen / 2
            canvas.drawLine(
                (cx + r1 * cos(angle)).toFloat(), (cy + r1 * sin(angle)).toFloat(),
                (cx + r2 * cos(angle)).toFloat(), (cy + r2 * sin(angle)).toFloat(),
                paint
            )
        }
    }

    private fun drawBlock(canvas: Canvas, s: Schedule) {
        try {
            val t = s.scheduledAt.split("T").getOrNull(1) ?: return
            val p = t.split(":")
            val h = p[0].toInt(); val m = p[1].toInt()

            val color = when (s.status) {
                "completed"   -> Color.parseColor("#88A6D8")
                "in_progress" -> Color.parseColor("#C98AB7")
                else          -> Color.parseColor("#E39A8D")
            }
            blockPaint.color = color

            val startAngle = timeToAngle(h, m)
            val sweepAngle = 360f / 24f * 0.85f

            canvas.drawArc(arcOval, startAngle, sweepAngle, false, blockPaint)

            val midAngle = Math.toRadians((startAngle + sweepAngle / 2).toDouble())
            val labelR   = outerR + ringW * 0.85f
            val lx = (cx + labelR * cos(midAngle)).toFloat()
            val ly = (cy + labelR * sin(midAngle)).toFloat()

            blockLabelPaint.textSize = sp(8f)
            blockLabelPaint.color    = color

            val name = if (s.taskName.length > 4) s.taskName.take(4) + "…" else s.taskName
            canvas.drawText(name, lx, ly + blockLabelPaint.textSize / 3, blockLabelPaint)

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
