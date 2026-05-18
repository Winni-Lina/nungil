package com.example.myapplication.common.model

data class UserSchedule(
    val id: Int,
    val title: String,
    val taskDescription: String,
    val triggerTimeMillis: Long
)
