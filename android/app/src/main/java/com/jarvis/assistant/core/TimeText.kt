package com.jarvis.assistant.core

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Locale-aware date, time and duration labels for Hindi and English. */
object TimeText {

    private val timeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)

    private fun hindi(hour: Int): String = when {
        hour < 12 -> "सुबह"
        hour < 16 -> "दोपहर"
        hour < 19 -> "शाम"
        else -> "रात"
    }

    fun time(epochMillis: Long, hindiUi: Boolean): String {
        val time = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalTime()
        val label = time.format(timeFormatter)
        return if (hindiUi) "${hindi(time.hour)} $label" else label
    }

    fun dayLabel(epochMillis: Long, hindiUi: Boolean): String {
        val zone = ZoneId.systemDefault()
        val date = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()
        val today = LocalDate.now(zone)
        return when {
            date == today -> if (hindiUi) "आज" else "Today"
            date == today.plusDays(1) -> if (hindiUi) "कल" else "Tomorrow"
            date == today.plusDays(2) -> if (hindiUi) "परसों" else "Day after"
            date.isBefore(today) -> if (hindiUi) "पहले" else "Overdue"
            else -> date.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH))
        }
    }

    fun due(epochMillis: Long?, hindiUi: Boolean): String {
        if (epochMillis == null) return if (hindiUi) "कोई समय नहीं" else "No time set"
        return "${dayLabel(epochMillis, hindiUi)} · ${time(epochMillis, hindiUi)}"
    }

    fun clockNow(hindiUi: Boolean): String = time(System.currentTimeMillis(), hindiUi)

    fun greeting(hindiUi: Boolean): String {
        val hour = LocalTime.now().hour
        return when {
            hour < 5 -> if (hindiUi) "नमस्ते" else "Hello"
            hour < 12 -> if (hindiUi) "सुप्रभात" else "Good morning"
            hour < 17 -> if (hindiUi) "नमस्ते" else "Good afternoon"
            hour < 21 -> if (hindiUi) "शुभ संध्या" else "Good evening"
            else -> if (hindiUi) "शुभ रात्रि" else "Good night"
        }
    }

    fun relativeFuture(epochMillis: Long, hindiUi: Boolean): String {
        val delta = epochMillis - System.currentTimeMillis()
        val minutes = (delta / 60_000).toInt()
        return when {
            minutes < 1 -> if (hindiUi) "अभी" else "now"
            minutes < 60 -> if (hindiUi) "$minutes मिनट में" else "in $minutes min"
            minutes < 60 * 24 -> if (hindiUi) "${minutes / 60} घंटे में" else "in ${minutes / 60} h"
            else -> dayLabel(epochMillis, hindiUi)
        }
    }

    fun startOfDay(date: LocalDate): Long =
        date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    fun tomorrowAt(hour: Int, minute: Int): Long {
        val date = LocalDate.now().plusDays(1)
        return LocalDateTime.of(date, LocalTime.of(hour, minute))
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    fun weekdayNumber(day: DayOfWeek): Int = day.value
}
