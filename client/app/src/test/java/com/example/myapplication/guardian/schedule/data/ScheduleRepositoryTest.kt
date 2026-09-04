package com.example.myapplication.guardian.schedule.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * taskProcess/customSteps 파싱 버그 회귀 테스트.
 * 서버는 TASK.process를 CLOB(JSON 배열을 문자열로 담은 형태)로 내려주므로,
 * optJSONArray만으로는 항상 emptyList가 반환되던 버그(수행 단계가 화면에 절대 표시되지 않음)를 검증한다.
 */
class ScheduleRepositoryTest {

    private val repository = ScheduleRepository()

    @Test
    fun `실제 JSON 배열로 내려오면 그대로 파싱된다`() {
        val obj = JSONObject().put("taskProcess", JSONArray(listOf("1단계", "2단계")))
        assertEquals(listOf("1단계", "2단계"), repository.parseStepField(obj, "taskProcess"))
    }

    @Test
    fun `JSON 배열이 문자열(CLOB)로 내려와도 파싱된다`() {
        val obj = JSONObject().put("taskProcess", "[\"1단계\",\"2단계\"]")
        assertEquals(listOf("1단계", "2단계"), repository.parseStepField(obj, "taskProcess"))
    }

    @Test
    fun `줄바꿈 텍스트로 내려와도 파싱된다`() {
        val obj = JSONObject().put("taskProcess", "1. 손을 씻는다\n2. 수건으로 닦는다")
        assertEquals(listOf("손을 씻는다", "수건으로 닦는다"), repository.parseStepField(obj, "taskProcess"))
    }

    @Test
    fun `필드가 없거나 null이면 빈 리스트`() {
        val obj = JSONObject()
        assertEquals(emptyList<String>(), repository.parseStepField(obj, "taskProcess"))
    }

    @Test
    fun `필드값이 문자열 null이면 빈 리스트`() {
        val obj = JSONObject().put("taskProcess", "null")
        assertEquals(emptyList<String>(), repository.parseStepField(obj, "taskProcess"))
    }
}
