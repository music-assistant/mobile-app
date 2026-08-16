package io.music_assistant.client.utils

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterMediumStyle
import platform.Foundation.NSDateFormatterNoStyle
import platform.Foundation.NSISO8601DateFormatter
import platform.Foundation.timeIntervalSince1970
import platform.posix.CLOCK_MONOTONIC
import platform.posix.clock_gettime_nsec_np

actual fun currentTimeMillis(): Long {
    return (NSDate().timeIntervalSince1970 * 1000).toLong()
}

/**
 * Darwin's `CLOCK_MONOTONIC` (same domain as `mach_continuous_time`, which K/N doesn't
 * expose) ticks through device sleep; `CLOCK_MONOTONIC_RAW`/`CLOCK_UPTIME_RAW` don't.
 */
@OptIn(ExperimentalForeignApi::class)
actual fun monotonicMs(): Long =
    (clock_gettime_nsec_np(CLOCK_MONOTONIC.toUInt()) / 1_000_000u).toLong()

actual fun formatIsoDate(isoDate: String): String {
    val parser = NSISO8601DateFormatter().apply {
        formatOptions = platform.Foundation.NSISO8601DateFormatWithFullDate
    }
    val date = parser.dateFromString(isoDate.substringBefore("T")) ?: return isoDate
    val formatter = NSDateFormatter().apply {
        dateStyle = NSDateFormatterMediumStyle
        timeStyle = NSDateFormatterNoStyle
    }
    return formatter.stringFromDate(date)
}
