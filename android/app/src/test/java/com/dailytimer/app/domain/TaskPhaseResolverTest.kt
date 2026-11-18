package com.dailytimer.app.domain

import com.dailytimer.app.data.model.Task
import com.dailytimer.app.data.model.TaskStatus
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class TaskPhaseResolverTest {
    private val utc: ZoneId = ZoneOffset.UTC
    private fun at(time: String) = TaskTime.toEpochMillis("2026-11-18", time, utc)!!

    private fun task(status: String = TaskStatus.UPCOMING, start: String = "09:00 AM", end: String = "10:00 AM") =
        Task(id = 1, date = "2026-11-18", startTime = start, endTime = end,
             taskName = "t", description = "", status = status)

    @Test
    fun `before start is upcoming`() =
        assertEquals(TaskPhase.UPCOMING, TaskPhaseResolver.resolve(task(), at("08:59 AM"), utc))

    @Test
    fun `between start and end is in progress`() =
        assertEquals(TaskPhase.IN_PROGRESS, TaskPhaseResolver.resolve(task(), at("09:30 AM"), utc))

    @Test
    fun `after end and not completed is overdue`() =
        assertEquals(TaskPhase.OVERDUE, TaskPhaseResolver.resolve(task(), at("08:00 PM"), utc))

    @Test
    fun `completed always wins`() =
        assertEquals(TaskPhase.COMPLETED, TaskPhaseResolver.resolve(task(TaskStatus.COMPLETED), at("09:30 AM"), utc))

    @Test
    fun `no end time stays in progress after start`() =
        assertEquals(TaskPhase.IN_PROGRESS, TaskPhaseResolver.resolve(task(end = ""), at("11:00 PM"), utc))

    @Test
    fun `unparseable start falls back to upcoming`() =
        assertEquals(TaskPhase.UPCOMING, TaskPhaseResolver.resolve(task(start = "??"), at("09:30 AM"), utc))

    @Test
    fun `only upcoming and in progress are pending`() {
        assertEquals(
            setOf(TaskPhase.UPCOMING, TaskPhase.IN_PROGRESS),
            TaskPhase.entries.filter { it.isPending }.toSet(),
        )
    }
}
