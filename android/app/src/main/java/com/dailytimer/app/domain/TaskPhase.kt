package com.dailytimer.app.domain

import com.dailytimer.app.data.model.Task
import com.dailytimer.app.data.model.TaskStatus
import java.time.ZoneId

enum class TaskPhase {
    UPCOMING, IN_PROGRESS, OVERDUE, COMPLETED;

    /** Phases that still need the user's attention today. */
    val isPending: Boolean get() = this == UPCOMING || this == IN_PROGRESS
}

object TaskPhaseResolver {
    fun resolve(task: Task, nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): TaskPhase {
        if (task.status == TaskStatus.COMPLETED) return TaskPhase.COMPLETED
        val start = TaskTime.toEpochMillis(task.date, task.startTime, zone) ?: return TaskPhase.UPCOMING
        if (nowMillis < start) return TaskPhase.UPCOMING
        val end = TaskTime.toEpochMillis(task.date, task.endTime, zone)
        return if (end == null || nowMillis <= end) TaskPhase.IN_PROGRESS else TaskPhase.OVERDUE
    }
}
