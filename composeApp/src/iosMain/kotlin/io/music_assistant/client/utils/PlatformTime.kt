package io.music_assistant.client.utils

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterMediumStyle
import platform.Foundation.NSDateFormatterNoStyle
import platform.Foundation.NSISO8601DateFormatter
import platform.Foundation.timeIntervalSince1970

actual fun currentTimeMillis(): Long {
    return (NSDate().timeIntervalSince1970 * 1000).toLong()
}

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
