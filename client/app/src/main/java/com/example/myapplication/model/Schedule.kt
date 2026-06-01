package com.example.myapplication.model

data class Schedule(
    val scheduleId: Int,
    val taskId: Int,
    val taskName: String,
    val status: String,
    val scheduledAt: String,
    val location: String = "",
    val specialNote: String = "",
    val taskProcess: List<String> = emptyList()
)
