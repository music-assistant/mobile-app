// The values in this file ARE the data — the backoff ladder is self-documenting in its
// `when (attempt)` form (see KDoc on reconnectBackoffMs). Extracting each rung to a named
// constant would make it less readable, not more.
@file:Suppress("MagicNumber")

package io.music_assistant.client.api

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first

/**
 * Two-phase reconnection backoff.
 * Phase 1 (quick recovery, attempts 0–4): 0 → 500ms → 1s → 2s → 4s — transient failures.
 * Phase 2 (patient recovery, attempts 5–9): 8s → 15s → 30s → 60s → 60s — server reboots.
 */
fun reconnectBackoffMs(attempt: Int): Long = when (attempt) {
    0 -> 0L
    1 -> 500L
    2 -> 1_000L
    3 -> 2_000L
    4 -> 4_000L
    5 -> 8_000L
    6 -> 15_000L
    7 -> 30_000L
    8 -> 60_000L
    else -> 60_000L
}

const val DEFAULT_MAX_RECONNECT_ATTEMPTS = 10

/**
 * Runs a reconnection loop with two-phase backoff.
 *
 * When [networkAvailable] is provided and reports `false`, the loop suspends until the network
 * returns instead of burning attempts against a dead connection. When the network comes back,
 * the attempt fires immediately (backoff delay is skipped since we already waited).
 *
 * @return true if [tryConnect] succeeded, false if all attempts exhausted.
 */
suspend fun runReconnectionLoop(
    maxAttempts: Int = DEFAULT_MAX_RECONNECT_ATTEMPTS,
    networkAvailable: StateFlow<Boolean>? = null,
    onAttemptStarting: (attempt: Int) -> Unit,
    tryConnect: suspend (attempt: Int) -> Boolean,
): Boolean {
    for (attempt in 0 until maxAttempts) {
        onAttemptStarting(attempt + 1)
        if (networkAvailable != null && !networkAvailable.value) {
            // Network is down — wait for it instead of wasting a timed delay
            networkAvailable.first { it }
        } else {
            delay(reconnectBackoffMs(attempt))
        }
        if (tryConnect(attempt + 1)) return true
    }
    return false
}
