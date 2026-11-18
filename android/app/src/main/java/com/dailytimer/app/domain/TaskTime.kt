package com.dailytimer.app.domain

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.DateTimeParseException
import java.util.Locale

/**
 * Converts between the strings persisted in the database (`yyyy-MM-dd` dates, `hh:mm a` times)
 * and epoch milliseconds. Never uses the device locale for stored values.
 */
object TaskTime {
    private val DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE
    private val TIME_DISPLAY_FORMAT = DateTimeFormatter.ofPattern("hh:mm a", Locale.US)
    private val TIME_PARSERS = listOf(
        DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("h:mm a").toFormatter(Locale.US),
        DateTimeFormatter.ofPattern("H:mm", Locale.US),
    )

    fun formatDate(date: LocalDate): String = date.format(DATE_FORMAT)
    fun formatTime(time: LocalTime): String = time.format(TIME_DISPLAY_FORMAT)

    fun localDateOf(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()

    fun parseDate(date: String): LocalDate? =
        try { LocalDate.parse(date.trim(), DATE_FORMAT) } catch (_: DateTimeParseException) { null }

    fun parseTime(time: String): LocalTime? {
        // Newer Android versions may emit NBSP / NNBSP before AM/PM.
        val normalized = time.trim().replace('\u00A0', ' ').replace('\u202F', ' ')
        if (normalized.isEmpty()) return null
        for (parser in TIME_PARSERS) {
            try { return LocalTime.parse(normalized, parser) } catch (_: DateTimeParseException) { /* try next */ }
        }
        return null
    }

    /** Returns null when either part is blank or unparseable. */
    fun toEpochMillis(date: String, time: String, zone: ZoneId = ZoneId.systemDefault()): Long? {
        val parsedDate = parseDate(date) ?: return null
        val parsedTime = parseTime(time) ?: return null
        return LocalDateTime.of(parsedDate, parsedTime).atZone(zone).toInstant().toEpochMilli()
    }
}
