package io.music_assistant.client.utils

import kotlin.math.roundToLong

/**
 * A total playing time in whole hours and minutes, for the length of a whole album or playlist,
 * where a clock-style "11:44:08" reads like a time of day. Rounded to the minute, never under one.
 */
data class TotalDuration(val hours: Long, val minutes: Long)

private const val SECONDS_PER_MINUTE = 60.0
private const val MINUTES_PER_HOUR = 60L

fun Double.toTotalDuration(): TotalDuration {
    val totalMinutes = (this / SECONDS_PER_MINUTE).roundToLong().coerceAtLeast(1)
    return TotalDuration(totalMinutes / MINUTES_PER_HOUR, totalMinutes % MINUTES_PER_HOUR)
}
