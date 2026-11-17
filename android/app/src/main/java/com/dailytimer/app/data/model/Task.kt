package com.dailytimer.app.data.model

data class Task(
    val id: Long = 0,
    val date: String,          // Format: YYYY-MM-DD
    val startTime: String,     // Format: HH:MM (e.g. 09:30 AM)
    val endTime: String = "",  // Format: HH:MM (e.g. 10:30 AM)
    val taskName: String,
    val description: String = "",
    val status: String = "UPCOMING" // UPCOMING, IN_PROGRESS, COMPLETED
)
