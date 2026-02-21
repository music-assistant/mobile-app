package io.music_assistant.client.api

import co.touchlab.kermit.Logger
import io.music_assistant.client.utils.currentTimeMillis
import kotlinx.coroutines.delay

private fun backoffMs(attempt: Int): Long = when (attempt) {
    0 -> 500L
    1 -> 1000L
    2 -> 2000L
    3 -> 3000L
    else -> 5000L
}

/**
 * Generic reconnection loop with exponential backoff.
 *
 * Callers supply lambdas for transport-specific behaviour. The loop delays before
 * each attempt, calls [onAttempt] to trigger the connect, then polls [isConnected]
 * for up to [waitAfterConnectMs] before moving to the next attempt.
 *
 * Returns when:
 * - [isConnected] returns true (success)
 * - [shouldStop] returns true (user disconnected or state changed)
 * - All [maxAttempts] exhausted → [onGiveUp] is called and the function returns
 *
 * @param tag              Log tag prefix (e.g. "Direct", "WebRTC")
 * @param maxAttempts      Maximum number of connection attempts (default 10)
 * @param waitAfterConnectMs  How long to poll for a successful connection per attempt
 * @param shouldStop       Returns true if reconnection should abort (e.g. user disconnected)
 * @param isConnected      Returns true once the connection is established
 * @param onAttempt        Update state + trigger async connect; receives 0-based attempt index
 * @param onGiveUp         Called when all attempts are exhausted
 */
suspend fun runReconnectionLoop(
    tag: String,
    maxAttempts: Int = 10,
    waitAfterConnectMs: Long,
    shouldStop: () -> Boolean,
    isConnected: () -> Boolean,
    onAttempt: suspend (attempt: Int) -> Unit,
    onGiveUp: () -> Unit,
) {
    val logger = Logger.withTag("ReconnectionLoop")
    for (attempt in 0 until maxAttempts) {
        if (shouldStop()) return
        val delay = backoffMs(attempt)
        logger.i { "[$tag] Reconnect attempt ${attempt + 1}/$maxAttempts in ${delay}ms" }
        delay(delay)
        // Overlap guard: stop if already connected (another attempt succeeded while delaying)
        if (shouldStop() || isConnected()) return
        onAttempt(attempt)
        // Poll for connection result
        val deadline = currentTimeMillis() + waitAfterConnectMs
        while (currentTimeMillis() < deadline) {
            if (isConnected() || shouldStop()) return
            delay(100L)
        }
        if (isConnected() || shouldStop()) return
        logger.w { "[$tag] Attempt ${attempt + 1} failed, will retry..." }
    }
    logger.e { "[$tag] Max $maxAttempts reconnect attempts reached, giving up" }
    onGiveUp()
}
