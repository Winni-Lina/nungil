package com.example.myapplication.user.schedule.data

import android.os.Build
import android.util.Log
import com.example.myapplication.config.AppConfig
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

object ScheduleRepository {

    private const val USE_DUMMY = false
    private const val BASE_URL  = AppConfig.BASE_URL + "api/v1"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    data class ScheduleItem(
        val scheduleId: Int,
        val title: String,
        val steps: List<String>,
        val triggerTimeMillis: Long,
        val location: String,
        val scheduleNote: String
    )

    data class UserInfo(
        val userName: String,
        val specialNote: String,
        val whitelistTaskNames: List<String>
    )

    // ── HTTP 헬퍼 ────────────────────────────────────────────────────────────

    private fun get(url: String): String =
        client.newCall(Request.Builder().url(url).get().build())
            .execute().use { it.body?.string() ?: "{}" }

    private fun post(url: String, json: JSONObject): String {
        val body = json.toString().toRequestBody("application/json".toMediaTypeOrNull())
        return client.newCall(Request.Builder().url(url).post(body).build())
            .execute().use { it.body?.string() ?: "{}" }
    }

    private fun patch(url: String) {
        val body = byteArrayOf().toRequestBody()
        client.newCall(Request.Builder().url(url).patch(body).build())
            .execute().close()
    }

    // ── API 2: 사용자 정보 조회 ──────────────────────────────────────────────
    // GET /api/v1/user/{userId}/{userIdx}

    fun fetchUserInfo(userId: String, userIdx: Int): UserInfo {
        if (USE_DUMMY) return dummyUserInfo()
        return try {
            val root = JSONObject(get("$BASE_URL/user/$userId/$userIdx"))
            if (root.optString("status") != "SUCCESS") return UserInfo("", "", emptyList())
            val result   = root.getJSONObject("result")
            val tasksArr = result.optJSONArray("whiteList")
            val tasks    = (0 until (tasksArr?.length() ?: 0)).map { tasksArr!!.getString(it) }
            UserInfo(
                userName           = result.optString("userName", ""),
                specialNote        = result.optString("specialNote", ""),
                whitelistTaskNames = tasks
            )
        } catch (e: Exception) {
            Log.e("ScheduleRepo", "사용자 정보 조회 실패: ${e.message}")
            UserInfo("", "", emptyList())
        }
    }

    // ── API 3: 오늘 일정 목록 조회 ──────────────────────────────────────────
    // GET /api/v1/schedule?userId=&idx=

    fun fetchTodaySchedules(userId: String, userIdx: Int): List<ScheduleItem> {
        if (USE_DUMMY) return dummySchedules()
        return try {
            val root = JSONObject(get("$BASE_URL/schedule?userId=$userId&idx=$userIdx"))
            if (root.optString("status") != "SUCCESS") return emptyList()
            val arr = root.getJSONArray("result")
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                ScheduleItem(
                    scheduleId        = obj.getLong("scheduleId").toInt(),
                    title             = obj.optString("taskName", ""),
                    steps             = parseTaskProcess(obj),
                    triggerTimeMillis = parseScheduledAt(obj),
                    location          = obj.optString("location", ""),
                    scheduleNote      = obj.optString("specialNote", "")
                )
            }
        } catch (e: Exception) {
            Log.e("ScheduleRepo", "일정 조회 실패: ${e.message}")
            emptyList()
        }
    }

    // ── API 3-1: 알람 발동 시 단계 재조회 (목록에서 필터) ───────────────────

    fun fetchStepsByScheduleId(scheduleId: Int, userId: String, userIdx: Int): Pair<List<String>, String> {
        if (USE_DUMMY) return dummySteps(scheduleId)
        return try {
            val item = fetchTodaySchedules(userId, userIdx).find { it.scheduleId == scheduleId }
                ?: return Pair(emptyList(), "")
            val note = buildString {
                if (item.location.isNotBlank())     append("장소: ${item.location} ")
                if (item.scheduleNote.isNotBlank()) append("메모: ${item.scheduleNote}")
            }.trim()
            Pair(item.steps, note)
        } catch (e: Exception) {
            Log.e("ScheduleRepo", "단계 조회 실패: ${e.message}")
            Pair(emptyList(), "")
        }
    }

    // ── API 3-2: 일정 완료 처리 ─────────────────────────────────────────────
    // PATCH /api/v1/schedule/{scheduleId}/complete

    fun completeSchedule(scheduleId: Int) {
        if (scheduleId < 0) return
        Thread {
            try { patch("$BASE_URL/schedule/$scheduleId/complete") }
            catch (e: Exception) { Log.e("ScheduleRepo", "일정 완료 처리 실패: ${e.message}") }
        }.start()
    }

    // ── API 4-1: 질문 발생 카운트 ───────────────────────────────────────────
    // POST /api/v1/question/log

    fun logQuestion(scheduleId: Long) {
        if (USE_DUMMY) return
        Thread {
            try {
                val json = JSONObject().put("scheduleId", scheduleId)
                post("$BASE_URL/question/log", json)
            } catch (e: Exception) {
                Log.e("ScheduleRepo", "질문 카운트 실패: ${e.message}")
            }
        }.start()
    }

    // ── 파싱 유틸 ────────────────────────────────────────────────────────────

    // taskProcess: 이미 파싱된 배열 or DB JSON 문자열 둘 다 처리
    private fun parseTaskProcess(obj: JSONObject): List<String> = try {
        val arr = obj.optJSONArray("taskProcess")
        if (arr != null) {
            (0 until arr.length()).map { arr.getString(it) }
        } else {
            val parsed = JSONArray(obj.optString("taskProcess", "[]"))
            (0 until parsed.length()).map { parsed.getString(it) }
        }
    } catch (e: Exception) { emptyList() }

    // scheduledAt: ISO 문자열 or Jackson 배열 [year,month,day,hour,min,sec] 둘 다 처리
    private fun parseScheduledAt(obj: JSONObject): Long = try {
        val arr = obj.optJSONArray("scheduledAt")
        if (arr != null && arr.length() >= 5) {
            val year = arr.getInt(0); val month = arr.getInt(1); val day = arr.getInt(2)
            val hour = arr.getInt(3); val min   = arr.getInt(4)
            val sec  = if (arr.length() > 5) arr.getInt(5) else 0
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                java.time.LocalDateTime.of(year, month, day, hour, min, sec)
                    .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            } else {
                Calendar.getInstance().apply { set(year, month - 1, day, hour, min, sec); set(Calendar.MILLISECOND, 0) }.timeInMillis
            }
        } else {
            val iso = obj.optString("scheduledAt", "")
            if (iso.isBlank()) 0L
            else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                java.time.LocalDateTime.parse(iso).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            } else {
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).parse(iso)?.time ?: 0L
            }
        }
    } catch (e: Exception) { 0L }

    // ── 더미 데이터 ──────────────────────────────────────────────────────────

    private fun dummyUserInfo() = UserInfo(
        userName           = "",
        specialNote        = "큰 소리를 무서워함. 차분하게 말해주세요.",
        whitelistTaskNames = listOf("약 먹기", "손 씻기", "양치하기", "운동하기")
    )

    private fun dummySchedules(): List<ScheduleItem> {
        val fiveMinLater = System.currentTimeMillis() + 5 * 60 * 1000L
        return listOf(
            ScheduleItem(
                scheduleId        = 1,
                title             = "약 먹기",
                steps             = listOf("약통에서 오늘 약을 꺼내세요", "물 한 컵을 준비하세요", "약을 물과 함께 드세요"),
                triggerTimeMillis = fiveMinLater,
                location          = "주방",
                scheduleNote      = ""
            )
        )
    }

    private fun dummySteps(scheduleId: Int): Pair<List<String>, String> = when (scheduleId) {
        1    -> Pair(listOf("약통에서 오늘 약을 꺼내세요", "물 한 컵을 준비하세요", "약을 물과 함께 드세요"), "장소: 주방")
        else -> Pair(emptyList(), "")
    }
}