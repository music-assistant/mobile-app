package io.music_assistant.client.utils

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Runnable
import platform.darwin.DISPATCH_QUEUE_PRIORITY_HIGH
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_global_queue
import kotlin.coroutines.CoroutineContext

actual val mainDispatcher: CoroutineDispatcher = Dispatchers.Main

/**
 * High-priority audio dispatcher for iOS.
 * Uses the high-priority global concurrent dispatch queue for the playback loop.
 *
 * ONLY used for the playback loop - reading PCM from buffer and writing to audio output.
 * This is the only operation that needs real-time priority.
 *
 * All other operations use normal priority:
 * - Binary message processing (Dispatchers.Default) - decodes and buffers with headroom
 * - Monitoring, commands, metadata (Dispatchers.Default) - not time-critical
 *
 * This ensures smooth playback even when app is backgrounded while minimizing
 * the use of high-priority resources.
 *
 * Note: iOS has excellent background audio handling via AVAudioSession.
 */
@OptIn(ExperimentalForeignApi::class)
actual val audioDispatcher: CoroutineDispatcher = object : CoroutineDispatcher() {
    // Use high-priority global concurrent queue for time-sensitive audio work
    // Concurrent queue allows system to manage thread pool dynamically
    private val dispatchQueue = dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_HIGH.toLong(), 0u)

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        dispatch_async(dispatchQueue) {
            block.run()
        }
    }
}
