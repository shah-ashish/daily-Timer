package com.dailytimer.app.domain

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TaskTimeTest {
    private val utc: ZoneId = ZoneOffset.UTC

    @Test
    fun `parses 12 hour times case-insensitively and tolerates NBSP and NNBSP`() {
        assertEquals(LocalTime.of(13, 0), TaskTime.parseTime("01:00 PM"))
        assertEquals(LocalTime.of(13, 0), TaskTime.parseTime("1:00 pm"))
        assertEquals(LocalTime.of(13, 0), TaskTime.parseTime("01:00\u00A0PM"))
        assertEquals(LocalTime.of(13, 0), TaskTime.parseTime("01:00\u202FPM"))
        assertEquals(LocalTime.of(0, 30), TaskTime.parseTime("12:30 AM"))
    }

    @Test
    fun `returns null for blank or garbage input`() {
        assertNull(TaskTime.parseTime(""))
        assertNull(TaskTime.parseTime("noon"))
        assertNull(TaskTime.toEpochMillis("2026-13-45", "09:00 AM"))
    }

    @Test
    fun `epoch ordering is chronological even though string ordering is not`() {
        val times = listOf("01:00 PM", "09:00 AM", "12:00 PM", "10:00 AM")
        val byMillis = times.sortedBy { TaskTime.toEpochMillis("2026-11-18", it, utc) }
        assertEquals(listOf("09:00 AM", "10:00 AM", "12:00 PM", "01:00 PM"), byMillis)
        assertNotEquals(byMillis, times.sorted())   // documents the original bug
    }

    @Test
    fun `stored date format ignores the device locale`() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("th-TH-u-ca-buddhist"))
            assertEquals("2026-11-18", TaskTime.formatDate(LocalDate.of(2026, 11, 18)))
        } finally {
            Locale.setDefault(original)
        }
    }
}
