package com.example.myapplication.config

object AppConfig {
    // 💡 여기서만 주소를 수정하면 앱 전체에 반영됩니다!
    const val BASE_URL = "https://reflector-carrousel-overstock.ngrok-free.dev/nungil-server/"

    // ── API 경로 상수 ────────────────────────────────────────────────────────
    // Guardian
    const val PATH_GUARDIAN_SCHEDULES    = "/v1/guardian/schedules"
    const val PATH_GUARDIAN_AUTH         = "/v1/guardian/auth"
    const val PATH_GUARDIAN_TASKS        = "/v1/guardian/tasks"
    const val PATH_GUARDIAN_REPORT       = "/v1/guardian/report"

    // User (nungil)
    const val PATH_USER_SCHEDULE         = "/v1/schedule"
    const val PATH_USER_INFO             = "/v1/user"
    const val PATH_QUESTION_ANALYZE      = "api/v1/question/analyze"
    const val PATH_QUESTION_LOG          = "/v1/question/log"
}
