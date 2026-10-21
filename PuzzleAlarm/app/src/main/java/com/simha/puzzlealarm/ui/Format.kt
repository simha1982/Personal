package com.simha.puzzlealarm.ui

import android.content.Context
import android.text.format.DateFormat
import com.simha.puzzlealarm.data.Alarm
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Calendar

/** Monday-first display order of [Calendar.DAY_OF_WEEK] values. */
val WEEK_ORDER = listOf(
    Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY,
    Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY,
)

private val SHORT = mapOf(
    Calendar.MONDAY to "Mon", Calendar.TUESDAY to "Tue", Calendar.WEDNESDAY to "Wed",
    Calendar.THURSDAY to "Thu", Calendar.FRIDAY to "Fri", Calendar.SATURDAY to "Sat", Calendar.SUNDAY to "Sun",
)

fun dayLetter(day: Int): String = SHORT.getValue(day).take(1)

fun formatTime(context: Context, hour: Int, minute: Int): String {
    val pattern = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"
    return LocalTime.of(hour, minute).format(DateTimeFormatter.ofPattern(pattern))
}

fun daysSummary(days: Set<Int>): String = when {
    days.isEmpty() -> "Once"
    days.size == 7 -> "Every day"
    days == setOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY) -> "Weekdays"
    days == setOf(Calendar.SATURDAY, Calendar.SUNDAY) -> "Weekends"
    else -> WEEK_ORDER.filter { it in days }.joinToString(", ") { SHORT.getValue(it) }
}

fun snoozeSummary(alarm: Alarm): String =
    if (alarm.snoozeEnabled) "Snooze ${alarm.snoozeMinutes} min (once)" else "No snooze"

fun untilText(triggerAt: Long, now: Long = System.currentTimeMillis()): String {
    val totalMinutes = ((triggerAt - now + 59_999) / 60_000).coerceAtLeast(0)
    val days = totalMinutes / (60 * 24)
    val hours = (totalMinutes / 60) % 24
    val minutes = totalMinutes % 60
    return buildList {
        if (days > 0) add("$days d")
        if (hours > 0) add("$hours h")
        if (minutes > 0 || (days == 0L && hours == 0L)) add("$minutes min")
    }.joinToString(" ")
}
