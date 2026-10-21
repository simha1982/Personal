package com.simha.puzzlealarm.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/**
 * A single alarm. [days] holds [Calendar.DAY_OF_WEEK] values; an empty set means "ring once".
 * Snooze is limited to one use per ring; [snoozeEnabled] and [snoozeMinutes] make it configurable.
 */
data class Alarm(
    val id: Int,
    val hour: Int,
    val minute: Int,
    val days: Set<Int> = emptySet(),
    val label: String = "",
    val enabled: Boolean = true,
    val snoozeEnabled: Boolean = true,
    val snoozeMinutes: Int = 5,
) {
    val isRepeating: Boolean get() = days.isNotEmpty()

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("hour", hour)
        .put("minute", minute)
        .put("days", JSONArray(days.sorted()))
        .put("label", label)
        .put("enabled", enabled)
        .put("snoozeEnabled", snoozeEnabled)
        .put("snoozeMinutes", snoozeMinutes)

    companion object {
        const val MIN_SNOOZE_MINUTES = 1
        const val MAX_SNOOZE_MINUTES = 30

        fun fromJson(o: JSONObject): Alarm {
            val daysJson = o.optJSONArray("days") ?: JSONArray()
            val days = (0 until daysJson.length()).map { daysJson.getInt(it) }.toSet()
            return Alarm(
                id = o.getInt("id"),
                hour = o.getInt("hour"),
                minute = o.getInt("minute"),
                days = days,
                label = o.optString("label", ""),
                enabled = o.optBoolean("enabled", true),
                snoozeEnabled = o.optBoolean("snoozeEnabled", true),
                snoozeMinutes = o.optInt("snoozeMinutes", 5)
                    .coerceIn(MIN_SNOOZE_MINUTES, MAX_SNOOZE_MINUTES),
            )
        }
    }
}
