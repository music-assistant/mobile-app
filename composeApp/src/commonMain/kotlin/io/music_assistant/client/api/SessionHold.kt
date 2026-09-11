package io.music_assistant.client.api

import kotlin.time.Duration
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Bounded suppression of background teardown while a command tries to reach the
 * server. Stamped at the command choke point, so a request issued from a system
 * transport control keeps the reconnect it just asked for alive.
 *
 * Stamp-only by design: `sendRequestRaw` has no per-request timeout, so a hold
 * released on completion would leak forever on a silently dead socket. [ttl] is
 * the bound; the cost of over-holding is one [ttl] of extra background socket
 * after the last command.
 */
internal class SessionHold(
    private val ttl: Duration,
    private val clock: TimeSource = TimeSource.Monotonic,
) {
    private var heldAt: TimeMark? = null

    val isHeld: Boolean get() = heldAt?.elapsedNow()?.let { it < ttl } == true

    fun hold() {
        heldAt = clock.markNow()
    }

    fun clear() {
        heldAt = null
    }
}
