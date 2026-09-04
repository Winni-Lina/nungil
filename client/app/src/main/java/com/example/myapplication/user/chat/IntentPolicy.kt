package com.example.myapplication.user.chat

/**
 * Gemini 구조화 응답의 intent를 앱 동작으로 옮기는 규칙.
 *
 * 화면 코드에서 분리해 둔 이유는 이 규칙이 단위 테스트 대상이기 때문이다.
 * 서버가 이미 intent를 정규화하지만, 앱에서도 같은 기준으로 한 번 더 방어한다.
 */
object IntentPolicy {

    const val STEP_DONE     = "STEP_DONE"
    const val STEP_QUESTION = "STEP_QUESTION"
    const val HELP_REQUEST  = "HELP_REQUEST"
    const val OFF_TOPIC     = "OFF_TOPIC"
    const val OTHER         = "OTHER"

    private val ALLOWED = setOf(STEP_DONE, STEP_QUESTION, HELP_REQUEST, OFF_TOPIC, OTHER)

    /** 허용된 5개 값 외(null·빈 문자열·공백·오타·소문자)는 모두 OTHER로 처리한다. */
    fun normalize(raw: String?): String {
        val intent = raw?.trim()?.uppercase().orEmpty()
        return if (intent in ALLOWED) intent else OTHER
    }

    /**
     * 완료 확인창 표시 여부. intent만 기준으로 판단한다.
     *
     * stepComplete는 참고값으로만 쓴다. Gemini가 intent는 질문으로,
     * stepComplete는 완료로 서로 다르게 반환할 수 있기 때문에
     * 두 값을 함께 조건으로 쓰면 질문이 완료로 잘못 처리된다.
     */
    fun shouldConfirmStep(intent: String): Boolean = intent == STEP_DONE

    /**
     * 질문 횟수 증가 여부. 실제 질문과 도움 요청만 센다.
     * 여러 조건에 해당해도 한 발화당 한 번만 증가하도록 Boolean 하나로 반환한다.
     */
    fun shouldCountQuestion(intent: String, photoRequest: Boolean): Boolean =
        intent == STEP_QUESTION || intent == HELP_REQUEST || photoRequest

    /** intent와 stepComplete가 어긋났는지 — 로그로 남겨 원인 추적에 쓴다. */
    fun isInconsistent(intent: String, stepComplete: Boolean): Boolean =
        (intent == STEP_DONE) != stepComplete
}
