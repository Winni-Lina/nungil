package com.example.myapplication.core.util

import android.os.Build
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * 서버가 scheduledAt 을 두 가지 형태로 내릴 수 있음:
 *  - 배열: [year, month(1-indexed), day, hour, min(, sec)]  ← Jackson timestamp 모드
 *  - 문자열: "2026-05-01T14:00:00"                           ← ISO 8601
 *
 * 두 Repository(user / guardian) 에서 공유하는 파싱 로직.
 */
object DateParseUtil {

    /**
     * scheduledAt → 에포크 밀리초(Long).
     * user ScheduleRepository 에서 사용.
     */
    fun parseScheduledAtMillis(obj: JSONObject): Long = try {
        val arr = obj.optJSONArray("scheduledAt")
        if (arr != null && arr.length() >= 5) {
            val year  = arr.getInt(0)
            // 서버 month: 1-indexed
            val month = arr.getInt(1)
            val day   = arr.getInt(2)
            val hour  = arr.getInt(3)
            val min   = arr.getInt(4)
            val sec   = if (arr.length() > 5) arr.getInt(5) else 0
            arrayToMillis(year, month, day, hour, min, sec)
        } else {
            val iso = obj.optString("scheduledAt", "")
            isoToMillis(iso)
        }
    } catch (e: Exception) { 0L }

    /**
     * scheduledAt → ISO 문자열("yyyy-MM-dd'T'HH:mm:ss").
     * guardian ScheduleRepository 에서 사용.
     */
    fun parseScheduledAtIso(obj: JSONObject): String = try {
        val raw = obj.get("scheduledAt")
        if (raw is String) {
            raw
        } else {
            val arr   = obj.getJSONArray("scheduledAt")
            val year  = arr.getInt(0)
            val month = arr.getInt(1)
            val day   = arr.getInt(2)
            val hour  = arr.getInt(3)
            val min   = if (arr.length() > 4) arr.getInt(4) else 0
            "%04d-%02d-%02dT%02d:%02d:00".format(year, month, day, hour, min)
        }
    } catch (e: Exception) { "" }

    // ── private helpers ──────────────────────────────────────────────────────

    private fun arrayToMillis(year: Int, month: Int, day: Int, hour: Int, min: Int, sec: Int): Long {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // LocalDateTime.of month: 1(Jan)~12(Dec) — 서버 1-indexed 그대로 사용
            java.time.LocalDateTime.of(year, month, day, hour, min, sec)
                .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        } else {
            // Calendar.MONTH는 0-indexed → month - 1 보정
            Calendar.getInstance().apply {
                set(year, month - 1, day, hour, min, sec)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        }
    }

    private fun isoToMillis(iso: String): Long {
        if (iso.isBlank()) return 0L
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            java.time.LocalDateTime.parse(iso)
                .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        } else {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).parse(iso)?.time ?: 0L
        }
    }
}
