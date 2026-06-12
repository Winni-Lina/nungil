package com.example.myapplication.user.schedule.service

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 알람 발동/완료 내역을 SharedPreferences에 로컬 저장.
 * 최대 50개 보관 (오래된 것부터 삭제).
 */
object AlarmEventLog {

    private const val PREFS_NAME = "alarm_event_log"
    private const val KEY_EVENTS = "events"
    private const val MAX_EVENTS = 50

    enum class Status { FIRED, COMPLETED, MISSED }

    data class AlarmEvent(
        val scheduleId: String,
        val title: String,
        val firedAtMillis: Long,
        val status: Status
    )

    fun record(context: Context, scheduleId: String, title: String, status: Status) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val arr = try { JSONArray(prefs.getString(KEY_EVENTS, "[]")) } catch (_: Exception) { JSONArray() }

        val event = JSONObject().apply {
            put("scheduleId", scheduleId)
            put("title", title)
            put("firedAt", System.currentTimeMillis())
            put("status", status.name)
        }
        arr.put(event)

        // 최대 MAX_EVENTS 유지 (오래된 것 삭제)
        val trimmed = JSONArray()
        val start = if (arr.length() > MAX_EVENTS) arr.length() - MAX_EVENTS else 0
        for (i in start until arr.length()) trimmed.put(arr.getJSONObject(i))

        prefs.edit().putString(KEY_EVENTS, trimmed.toString()).apply()
    }

    fun readAll(context: Context): List<AlarmEvent> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val arr = try { JSONArray(prefs.getString(KEY_EVENTS, "[]")) } catch (_: Exception) { return emptyList() }
        val list = mutableListOf<AlarmEvent>()
        for (i in arr.length() - 1 downTo 0) {   // 최신 순
            val obj = arr.getJSONObject(i)
            list.add(AlarmEvent(
                scheduleId   = obj.optString("scheduleId", ""),
                title        = obj.optString("title", ""),
                firedAtMillis = obj.optLong("firedAt", 0),
                status       = try { Status.valueOf(obj.optString("status", "FIRED")) } catch (_: Exception) { Status.FIRED }
            ))
        }
        return list
    }

    fun getLastSyncMillis(context: Context): Long {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong("last_sync", 0L)
    }

    fun saveLastSync(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putLong("last_sync", System.currentTimeMillis()).apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().remove(KEY_EVENTS).apply()
    }
}
