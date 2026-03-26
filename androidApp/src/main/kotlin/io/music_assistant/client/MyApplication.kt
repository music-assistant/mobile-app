package io.music_assistant.client

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import io.music_assistant.client.di.androidModule
import io.music_assistant.client.di.appModule
import io.music_assistant.client.di.cleanupSingletons
import io.music_assistant.client.di.initKoin
import io.music_assistant.client.logging.InMemoryLogWriter
import io.music_assistant.client.services.MediaNotificationManager
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.stopKoin
import java.io.File

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin(androidModule(), appModule()) {
            androidContext(this@MyApplication)
        }
        createNotificationChannel(this)
        cleanupStaleLogFile()
        installCrashHandler()
    }

    private fun cleanupStaleLogFile() {
        File(cacheDir, "ma_client_logs.txt").delete()
    }

    private fun installCrashHandler() {
        val crashLogFile = File(cacheDir, "ma_crash_log.txt")
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                crashLogFile.delete()
                val text = InMemoryLogWriter.getLogText() +
                    "\n\n=== CRASH ===\n" + throwable.stackTraceToString()
                crashLogFile.writeText(text)
            } catch (_: Exception) { }
            previousHandler?.uncaughtException(thread, throwable)
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        // Clean up resources when app is terminated
        // Note: This is called on emulator/testing but not on real devices
        // However, having it ensures proper cleanup in tests
        cleanupSingletons()
        stopKoin()
    }

    private fun createNotificationChannel(context: Context) {
        val channel = NotificationChannel(
            MediaNotificationManager.CHANNEL_ID,
            "Media Playback",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }
}