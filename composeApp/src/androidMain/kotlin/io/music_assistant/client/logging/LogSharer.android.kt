@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package io.music_assistant.client.logging

import io.music_assistant.client.player.PlatformContext
import java.io.File

actual class LogSharer actual constructor(private val platformContext: PlatformContext) {
    private val context get() = platformContext.applicationContext

    private fun logFile() = File(context.cacheDir, "ma_client_logs.txt")
    private fun crashLogFile() = File(context.cacheDir, "ma_crash_log.txt")

    actual fun prepareLogShareFile(logText: String): String {
        val file = logFile()
        file.writeText(LogSanitizer.sanitize(logText))
        return file.absolutePath
    }

    actual fun hasCrashLog(): Boolean = crashLogFile().exists()

    actual fun prepareCrashLogShareFile(): String? {
        val file = crashLogFile()
        if (!file.exists()) return null
        val sanitizedFile = File(context.cacheDir, "ma_crash_log_share.txt")
        sanitizedFile.writeText(LogSanitizer.sanitize(file.readText()))
        return sanitizedFile.absolutePath
    }

    actual fun deleteCrashLog() {
        crashLogFile().delete()
    }
}
