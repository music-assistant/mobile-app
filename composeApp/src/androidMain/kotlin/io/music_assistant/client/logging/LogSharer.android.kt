@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package io.music_assistant.client.logging

import android.content.Intent
import androidx.core.content.FileProvider
import io.music_assistant.client.player.PlatformContext
import java.io.File

actual class LogSharer actual constructor(private val platformContext: PlatformContext) {

    private val context get() = platformContext.applicationContext

    private fun logFile() = File(context.cacheDir, "ma_client_logs.txt")
    private fun crashLogFile() = File(context.cacheDir, "ma_crash_log.txt")

    actual fun shareLogs(logText: String) {
        val file = logFile()
        file.writeText(logText)
        shareFile(file)
    }

    actual fun hasCrashLog(): Boolean = crashLogFile().exists()

    actual fun shareCrashLog() {
        val file = crashLogFile()
        if (file.exists()) shareFile(file)
    }

    actual fun deleteCrashLog() {
        crashLogFile().delete()
    }

    private fun shareFile(file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Share logs").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }
}
