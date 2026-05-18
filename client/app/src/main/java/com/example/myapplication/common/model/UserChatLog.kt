package com.example.myapplication.common.model

// 질문과 답변을 쌍으로 기록하기 위한 클래스
data class UserChatLog(
    val role: String,    // "user" 또는 "assistant"
    val message: String  // 대화 내용
)