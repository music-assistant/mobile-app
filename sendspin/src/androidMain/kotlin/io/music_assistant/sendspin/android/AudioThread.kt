package io.music_assistant.sendspin.android

import android.os.Process
import co.touchlab.kermit.Logger
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors

/**
 * A dispatcher for [io.music_assistant.sendspin.api.SendspinDeps.audioDispatcher]: one
 * dedicated thread at [Process.THREAD_PRIORITY_AUDIO], so playback stays smooth while the
 * app is in the background or the UI is busy. Each call makes a new thread; the caller
 * owns it and closes the dispatcher when the player is gone.
 */
public fun sendspinAudioDispatcher(): ExecutorCoroutineDispatcher = Executors.newSingleThreadExecutor { runnable ->
    Thread({
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
        runnable.run()
    }, "AudioThread-${System.currentTimeMillis()}").apply {
        priority = Thread.MAX_PRIORITY
        setUncaughtExceptionHandler { thread, exception ->
            Logger.withTag("AudioDispatcher").e(exception) { "Uncaught exception in $thread" }
        }
    }
}.asCoroutineDispatcher()
