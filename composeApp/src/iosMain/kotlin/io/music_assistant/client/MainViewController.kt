package io.music_assistant.client

import androidx.compose.ui.window.ComposeUIViewController
import io.music_assistant.client.di.initKoin
import io.music_assistant.client.di.iosModule
import io.music_assistant.client.logging.InMemoryLogWriter
import io.music_assistant.client.ui.compose.App
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.writeToFile

fun MainViewController() = ComposeUIViewController(
    configure = {
        initKoin(iosModule())
        cleanupStaleLogFile()
        installCrashHandler()
    }
) { App() }

private fun cleanupStaleLogFile() {
    NSFileManager.defaultManager.removeItemAtPath(
        "${NSTemporaryDirectory()}ma_client_logs.txt",
        error = null
    )
}

private fun installCrashHandler() {
    platform.Foundation.NSSetUncaughtExceptionHandler { exception ->
        try {
            val path = "${NSTemporaryDirectory()}ma_crash_log.txt"
            NSFileManager.defaultManager.removeItemAtPath(path, error = null)
            val text = InMemoryLogWriter.getLogText() +
                "\n\n=== CRASH ===\n" + (exception?.description ?: "Unknown exception")
            val nsString = NSString.create(string = text)
            nsString.writeToFile(path, atomically = true, encoding = NSUTF8StringEncoding, error = null)
        } catch (_: Exception) { }
    }
}