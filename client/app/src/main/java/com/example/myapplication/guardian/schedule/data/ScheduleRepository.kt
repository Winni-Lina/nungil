package com.example.myapplication.guardian.schedule.data

import com.example.myapplication.core.network.ApiClient
import com.example.myapplication.core.network.ApiResult
import com.example.myapplication.core.network.Session
import com.example.myapplication.model.Schedule
import com.example.myapplication.model.Task
import org.json.JSONObject

class ScheduleRepository {

    private val guardianId get() = Session.guardianId
    private val userIdx    get() = Session.userIdx

    fun getSchedules(onResult: (ApiResult<List<Schedule>>) -> Unit) {
        val primaryPath = "/v1/guardian/schedules/$guardianId/$userIdx"
        val fallbackPath = "/schedule/$guardianId/$userIdx"
        android.util.Log.d("ScheduleRepo", "일정 요청(1차): $primaryPath")
        requestSchedules(primaryPath) { first ->
            when (first) {
                is ApiResult.Success -> onResult(first)
                is ApiResult.Error -> {
                    android.util.Log.w("ScheduleRepo", "1차 경로 실패, fallback 시도: ${first.message}")
                    requestSchedules(fallbackPath, onResult)
                }
            }
        }
    }

    private fun requestSchedules(path: String, onResult: (ApiResult<List<Schedule>>) -> Unit) {
        ApiClient.get(path) { result ->
            when (result) {
                is ApiResult.Success -> {
                    android.util.Log.d("ScheduleRepo", "응답($path): ${result.data}")
                    try {
                        val root = JSONObject(result.data)
                        val arr = when {
                            root.has("schedules") -> root.getJSONArray("schedules")
                            root.has("result") && root.getJSONObject("result").has("schedules") ->
                                root.getJSONObject("result").getJSONArray("schedules")
                            else -> throw IllegalStateException("schedules 필드가 없습니다.")
                        }
                        val list = mutableListOf<Schedule>()
                        for (i in 0 until arr.length()) {
                            val obj = arr.getJSONObject(i)
                            list.add(
                                Schedule(
                                    scheduleId = obj.getInt("scheduleId"),
                                    taskId = obj.getInt("taskId"),
                                    taskName = obj.getString("taskName"),
                                    status = obj.getString("status"),
                                    scheduledAt = parseScheduledAt(obj)
                                )
                            )
                        }
                        onResult(ApiResult.Success(list))
                    } catch (e: Exception) {
                        android.util.Log.e("ScheduleRepo", "파싱 실패($path): ${e.message}")
                        onResult(ApiResult.Error("일정 파싱 실패: ${e.message}"))
                    }
                }
                is ApiResult.Error -> {
                    android.util.Log.e("ScheduleRepo", "오류($path): ${result.message}")
                    onResult(result)
                }
            }
        }
    }

    /**
     * 서버가 scheduledAt을 두 형태로 내릴 수 있음:
     *   - 문자열: "2026-05-01T14:00:00"
     *   - 배열:   [2026, 5, 1, 14, 0]   ← Jackson WRITE_DATES_AS_TIMESTAMPS=true 일 때
     */
    private fun parseScheduledAt(obj: org.json.JSONObject): String {
        return try {
            val raw = obj.get("scheduledAt")
            if (raw is String) {
                raw
            } else {
                // JSONArray [year, month, day, hour, minute(, second)]
                val arr = obj.getJSONArray("scheduledAt")
                val year  = arr.getInt(0)
                val month = arr.getInt(1)
                val day   = arr.getInt(2)
                val hour  = arr.getInt(3)
                val min   = if (arr.length() > 4) arr.getInt(4) else 0
                "%04d-%02d-%02dT%02d:%02d:00".format(year, month, day, hour, min)
            }
        } catch (e: Exception) {
            android.util.Log.e("ScheduleRepo", "scheduledAt 파싱 실패: ${e.message}")
            ""
        }
    }

    fun deleteSchedule(scheduleId: Int, onResult: (ApiResult<Boolean>) -> Unit) {
        ApiClient.delete("/v1/guardian/schedules/$scheduleId") { result ->
            when (result) {
                is ApiResult.Success -> onResult(ApiResult.Success(true))
                is ApiResult.Error   -> onResult(result)
            }
        }
    }

    fun getWhitelistTasks(onResult: (ApiResult<List<Task>>) -> Unit) {
        val path = "/v1/guardian/settings/user/$guardianId/$userIdx/whitelist"
        android.util.Log.d("ScheduleRepo", "화이트리스트 요청: $path")
        ApiClient.get(path) { result ->
            when (result) {
                is ApiResult.Success -> {
                    android.util.Log.d("ScheduleRepo", "화이트리스트 응답: ${result.data}")
                    try {
                        // 실제 응답 구조:
                        // { "result": { "allowedItems": [{"item":"빨래하기","taskId":5}, ...] }, "status":"SUCCESS" }
                        val root       = JSONObject(result.data)
                        val resultObj  = root.getJSONObject("result")
                        val arr        = resultObj.getJSONArray("allowedItems")
                        val list = mutableListOf<Task>()
                        for (i in 0 until arr.length()) {
                            val obj = arr.getJSONObject(i)
                            val name = obj.optString("item", "")
                            list.add(Task(
                                taskId   = obj.getInt("taskId"),
                                taskName = name.ifEmpty { "알 수 없는 과업" }
                            ))
                        }
                        onResult(ApiResult.Success(list))
                    } catch (e: Exception) {
                        android.util.Log.e("ScheduleRepo", "화이트리스트 파싱 실패: ${e.message}")
                        onResult(ApiResult.Error("과업 파싱 실패: ${e.message}"))
                    }
                }
                is ApiResult.Error -> {
                    android.util.Log.e("ScheduleRepo", "화이트리스트 오류: ${result.message}")
                    onResult(result)
                }
            }
        }
    }

    fun deleteWhitelistTask(taskId: Int, onResult: (ApiResult<Boolean>) -> Unit) {
        val path = "/v1/guardian/settings/user/$guardianId/$userIdx/whitelist/$taskId"
        ApiClient.delete(path) { result ->
            when (result) {
                is ApiResult.Success -> onResult(ApiResult.Success(true))
                is ApiResult.Error   -> onResult(result)
            }
        }
    }

    // SC-002: 과업 단계 조회
    fun getTaskSteps(taskId: Int, onResult: (ApiResult<List<String>>) -> Unit) {
        ApiClient.get("/tasks/$taskId/steps") { result ->
            when (result) {
                is ApiResult.Success -> {
                    try {
                        val arr = JSONObject(result.data).getJSONArray("steps")
                        val list = mutableListOf<String>()
                        for (i in 0 until arr.length()) {
                            list.add(arr.getString(i))
                        }
                        onResult(ApiResult.Success(list))
                    } catch (e: Exception) {
                        onResult(ApiResult.Error("단계 파싱 실패: ${e.message}"))
                    }
                }
                is ApiResult.Error -> onResult(result)
            }
        }
    }

    // SM-002: 일정 시간 수정
    fun updateScheduleTime(scheduleId: Int, date: String, time: String, onResult: (ApiResult<Boolean>) -> Unit) {
        val scheduledAt = "${date}T${time}:00"
        val body = JSONObject().apply { put("scheduledAt", scheduledAt) }.toString()
        ApiClient.put("/v1/guardian/schedules/$scheduleId/time", body) { result ->
            when (result) {
                is ApiResult.Success -> {
                    try {
                        val status = JSONObject(result.data).optString("status", "")
                        if (status == "SUCCESS") onResult(ApiResult.Success(true))
                        else onResult(ApiResult.Error("수정 실패"))
                    } catch (e: Exception) {
                        onResult(ApiResult.Error("응답 파싱 실패: ${e.message}"))
                    }
                }
                is ApiResult.Error -> onResult(result)
            }
        }
    }

    fun addSchedule(
        taskId: Int,
        date: String,
        time: String,
        location: String = "",
        specialNote: String = "",
        onResult: (ApiResult<Unit>) -> Unit
    ) {
        val scheduledAt = "${date}T${time}:00"
        val body = JSONObject().apply {
            put("guardianId", guardianId)
            put("idx", userIdx)
            put("taskId", taskId)
            put("scheduledAt", scheduledAt)
            put("location", if (location.isNotEmpty() && location != "선택 안 함") location else "")
            put("specialNote", specialNote)
        }.toString()

        ApiClient.post("/v1/guardian/schedules", body) { result ->
            when (result) {
                is ApiResult.Success -> {
                    try {
                        val json = JSONObject(result.data)
                        val status = json.optString("status", "")
                        if (status == "SUCCESS") onResult(ApiResult.Success(Unit))
                        else {
                            val msg = json.optString("message", "").takeIf { it.isNotEmpty() && it != "null" } ?: "등록 실패"
                            onResult(ApiResult.Error(msg))
                        }
                    } catch (e: Exception) {
                        onResult(ApiResult.Error("응답 파싱 실패: ${e.message}"))
                    }
                }
                is ApiResult.Error -> onResult(result)
            }
        }
    }
}
