package com.example.myapplication.model

data class Task(
    val taskId: Int,
    val taskName: String
)

data class TaskStep(
    val order: Int,
    val description: String
)
