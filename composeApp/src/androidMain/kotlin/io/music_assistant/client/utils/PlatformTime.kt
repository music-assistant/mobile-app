package io.music_assistant.client.utils

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

actual fun currentTimeMillis(): Long = System.currentTimeMillis()

actual fun formatIsoDate(isoDate: String): String = try {
    LocalDate.parse(isoDate.substringBefore("T"))
        .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
} catch (_: Exception) {
    isoDate
}
