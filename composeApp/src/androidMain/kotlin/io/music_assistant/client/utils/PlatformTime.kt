package io.music_assistant.client.utils

import android.os.SystemClock
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

actual fun currentTimeMillis(): Long = System.currentTimeMillis()

/** False in local JVM unit tests, where the android.jar [SystemClock] stub throws. */
private val hasRealSystemClock: Boolean = try {
    SystemClock.elapsedRealtime()
    true
} catch (_: RuntimeException) {
    false
}

private const val NANOS_PER_MILLI = 1_000_000L

/** [SystemClock.elapsedRealtime] ticks through deep sleep; [SystemClock.uptimeMillis] doesn't. */
actual fun monotonicMs(): Long =
    if (hasRealSystemClock) SystemClock.elapsedRealtime() else System.nanoTime() / NANOS_PER_MILLI

actual fun formatIsoDate(isoDate: String): String = try {
    LocalDate.parse(isoDate.substringBefore("T"))
        .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
} catch (_: Exception) {
    isoDate
}

actual fun platformLocale(): String = Locale.getDefault().toLanguageTag()
