package com.example.myapplication.user.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 이번 주 작업 계획서의 필수 상황 6가지를 앱 판단 규칙 기준으로 검증한다.
 * 화면·네트워크 없이 순수 규칙만 확인하므로 기기 없이 실행된다.
 */
class IntentPolicyTest {

    // ── 필수 상황 1 ─────────────────────────────────────────────────────────
    @Test
    fun `상황1 다 했어 - STEP_DONE이면 확인창을 띄우고 질문 횟수는 세지 않는다`() {
        val intent = IntentPolicy.normalize("STEP_DONE")

        assertEquals(IntentPolicy.STEP_DONE, intent)
        assertTrue("완료 확인창이 표시되어야 함", IntentPolicy.shouldConfirmStep(intent))
        assertFalse(
            "완료 선언은 질문 횟수에 포함되지 않아야 함",
            IntentPolicy.shouldCountQuestion(intent, photoRequest = false)
        )
    }

    // ── 필수 상황 2 ─────────────────────────────────────────────────────────
    @Test
    fun `상황2 다 한 게 맞아 - STEP_QUESTION이면 질문 1회만 세고 확인창은 없다`() {
        val intent = IntentPolicy.normalize("STEP_QUESTION")

        assertTrue(
            "질문은 질문 횟수에 포함되어야 함",
            IntentPolicy.shouldCountQuestion(intent, photoRequest = false)
        )
        assertFalse("질문에는 확인창이 뜨면 안 됨", IntentPolicy.shouldConfirmStep(intent))
    }

    @Test
    fun `상황2-보강 STEP_QUESTION인데 stepComplete=true여도 확인창을 띄우지 않는다`() {
        // Gemini가 intent는 질문, stepComplete는 완료로 어긋나게 반환한 경우.
        // 이전 구현은 두 값을 OR로 묶어 질문을 완료로 잘못 처리했다.
        val intent = IntentPolicy.normalize("STEP_QUESTION")

        assertFalse(
            "stepComplete=true여도 intent 기준으로 확인창은 표시되지 않아야 함",
            IntentPolicy.shouldConfirmStep(intent)
        )
        assertTrue(
            "불일치로 감지되어 로그에 남아야 함",
            IntentPolicy.isInconsistent(intent, stepComplete = true)
        )
    }

    @Test
    fun `상황2-보강 STEP_DONE인데 stepComplete=false여도 intent 기준으로 확인창을 띄운다`() {
        val intent = IntentPolicy.normalize("STEP_DONE")

        assertTrue(
            "stepComplete=false여도 intent가 STEP_DONE이면 확인창 표시",
            IntentPolicy.shouldConfirmStep(intent)
        )
        assertTrue(IntentPolicy.isInconsistent(intent, stepComplete = false))
    }

    // ── 필수 상황 3 ─────────────────────────────────────────────────────────
    @Test
    fun `상황3 잘 모르겠어 - HELP_REQUEST면 질문 1회를 세고 단계는 유지한다`() {
        val intent = IntentPolicy.normalize("HELP_REQUEST")

        assertTrue(
            "도움 요청은 질문 횟수에 포함되어야 함",
            IntentPolicy.shouldCountQuestion(intent, photoRequest = false)
        )
        assertFalse("단계는 유지되어야 함", IntentPolicy.shouldConfirmStep(intent))
    }

    @Test
    fun `상황3-보강 HELP_REQUEST와 photoRequest가 함께 와도 질문은 한 번만 센다`() {
        val intent = IntentPolicy.normalize("HELP_REQUEST")

        // 두 조건에 모두 해당해도 Boolean 하나만 반환하므로 증가는 1회로 끝난다
        assertTrue(IntentPolicy.shouldCountQuestion(intent, photoRequest = true))
    }

    // ── 필수 상황 4 ─────────────────────────────────────────────────────────
    @Test
    fun `상황4 엄마 언제 와 - OFF_TOPIC이면 질문 횟수가 늘지 않고 단계도 유지된다`() {
        val intent = IntentPolicy.normalize("OFF_TOPIC")

        assertFalse(
            "관계없는 말은 질문 횟수에 포함되지 않아야 함",
            IntentPolicy.shouldCountQuestion(intent, photoRequest = false)
        )
        assertFalse("단계는 유지되어야 함", IntentPolicy.shouldConfirmStep(intent))
    }

    // ── 필수 상황 5 ─────────────────────────────────────────────────────────
    @Test
    fun `상황5 intent가 없거나 잘못된 값이면 OTHER로 처리해 단계를 유지한다`() {
        val badValues = listOf(
            null, "", "   ", "STEP_UNKNOWN", "완료", "123", "step done"
        )

        badValues.forEach { raw ->
            val intent = IntentPolicy.normalize(raw)
            assertEquals("[$raw]는 OTHER로 처리되어야 함", IntentPolicy.OTHER, intent)
            assertFalse(
                "[$raw]에서 확인창이 뜨면 안 됨",
                IntentPolicy.shouldConfirmStep(intent)
            )
            assertFalse(
                "[$raw]에서 질문 횟수가 늘면 안 됨",
                IntentPolicy.shouldCountQuestion(intent, photoRequest = false)
            )
        }
    }

    @Test
    fun `상황5-보강 소문자나 앞뒤 공백이 섞인 값도 정규화해 인식한다`() {
        assertEquals(IntentPolicy.STEP_DONE, IntentPolicy.normalize(" step_done "))
        assertEquals(IntentPolicy.HELP_REQUEST, IntentPolicy.normalize("help_request"))
        assertEquals(IntentPolicy.OFF_TOPIC, IntentPolicy.normalize("Off_Topic"))
    }

    // ── 허용 5종이 그대로 유지되는지 ─────────────────────────────────────────
    @Test
    fun `허용된 5개 Intent는 정규화 후에도 값이 바뀌지 않는다`() {
        listOf(
            IntentPolicy.STEP_DONE,
            IntentPolicy.STEP_QUESTION,
            IntentPolicy.HELP_REQUEST,
            IntentPolicy.OFF_TOPIC,
            IntentPolicy.OTHER
        ).forEach { intent ->
            assertEquals(intent, IntentPolicy.normalize(intent))
        }
    }

    @Test
    fun `사진 요청은 intent와 무관하게 질문 횟수에 포함된다`() {
        assertTrue(
            IntentPolicy.shouldCountQuestion(IntentPolicy.OTHER, photoRequest = true)
        )
        assertTrue(
            IntentPolicy.shouldCountQuestion(IntentPolicy.OFF_TOPIC, photoRequest = true)
        )
    }

    @Test
    fun `intent와 stepComplete가 맞으면 불일치로 보지 않는다`() {
        assertFalse(IntentPolicy.isInconsistent(IntentPolicy.STEP_DONE, stepComplete = true))
        assertFalse(IntentPolicy.isInconsistent(IntentPolicy.STEP_QUESTION, stepComplete = false))
        assertFalse(IntentPolicy.isInconsistent(IntentPolicy.OTHER, stepComplete = false))
    }
}
