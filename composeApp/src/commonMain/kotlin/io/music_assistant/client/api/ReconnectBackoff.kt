// The values in this file ARE the data — the backoff ladder is self-documenting in its
// `when (attempt)` form (see KDoc on reconnectBackoffMs). Extracting each rung to a named
// constant would make it less readable, not more.
@file:Suppress("MagicNumber")

package io.music_assistant.client.api

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first

/**
 * Three-phase reconnection backoff.
 * Phase 1 (attempts 0–4, quick recovery):    0 → 500ms → 1s → 2s → 4s — transient blips.
 * Phase 2 (attempts 5–9, brief outages):      8s → 15s → 30s → 60s → 120s — tunnel / modem drop.
 * Phase 3 (attempts 10+, indefinite):         120s steps — server reboot, long travel.
 *
 * After Phase 2 the delay caps at 120s. The combined window for the default 20 attempts
 * is ~24 minutes — long enough for almost any transient outage (WireGuard reconnect,
 * modem reboot, tunnel dropout), short enough to surface a clean error state before
 * the user loses patience.
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
    else -> 120_000L       // 2‑minute ceiling from attempt 9 onward
}

/** Default reconnect attempt ceiling.  20 attempts × three-phase backoff ≈ 24 min window. */
const val DEFAULT_MAX_RECONNECT_ATTEMPTS = 20

/**
 * Runs a reconnection loop with three‑phase backoff and a configurable attempt ceiling.
 *
 * When [networkAvailable] is provided and reports `false`, the loop suspends
 * until the network returns instead of burning timed delays.  When the network
 * comes back a short grace period (500 ms) is added to let DNS stabilise
 * through a newly‑established route (e.g. WireGuard tunnel).
 *
 * @return true if [tryConnect] succeeded — the caller should resume normal
 *         operation.  false if all [maxAttempts] were exhausted.
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
            // Short grace period after the network comes back: DNS resolution through
            // the new route (e.g. WireGuard tunnel) may take a few ms to stabilise.
            // Without this delay, a reconnect attempt that fails with
            // NSURLErrorCannotFindHost (Code=-1003) poisons the HTTP client's
            // connection pool for all subsequent retries.
            delay(500)
        } else {
            delay(reconnectBackoffMs(attempt))
        }
        if (tryConnect(attempt + 1)) return true
    }
    return false
}
