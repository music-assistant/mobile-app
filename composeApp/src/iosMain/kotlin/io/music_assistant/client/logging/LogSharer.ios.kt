@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package io.music_assistant.client.logging

import io.music_assistant.client.player.PlatformContext
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.writeToFile

actual class LogSharer actual constructor(@Suppress("UNUSED_PARAMETER") platformContext: PlatformContext) {
    private fun logFilePath() = "${NSTemporaryDirectory()}ma_client_logs.txt"
    private fun crashLogFilePath() = "${NSTemporaryDirectory()}ma_crash_log.txt"

    actual fun prepareLogShareFile(logText: String): String {
        val path = logFilePath()
        writeTextToFile(LogSanitizer.sanitize(logText), path)
        return path
    }

    actual fun hasCrashLog(): Boolean =
        NSFileManager.defaultManager.fileExistsAtPath(crashLogFilePath())

    actual fun prepareCrashLogShareFile(): String? {
        val path = crashLogFilePath()
        if (!NSFileManager.defaultManager.fileExistsAtPath(path)) return null
        val rawText = NSString.create(
            contentsOfFile = path,
            encoding = NSUTF8StringEncoding,
            error = null,
        )?.toString() ?: return null
        val sanitizedPath = "${NSTemporaryDirectory()}ma_crash_log_share.txt"
        writeTextToFile(LogSanitizer.sanitize(rawText), sanitizedPath)
        return sanitizedPath
    }

    actual fun deleteCrashLog() {
        NSFileManager.defaultManager.removeItemAtPath(crashLogFilePath(), error = null)
    }

    private fun writeTextToFile(text: String, path: String) {
        val nsString = NSString.create(string = text)
        nsString.writeToFile(path, atomically = true, encoding = NSUTF8StringEncoding, error = null)
    }
}
