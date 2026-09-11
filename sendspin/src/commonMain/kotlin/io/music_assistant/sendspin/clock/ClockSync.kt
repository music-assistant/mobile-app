package io.music_assistant.sendspin.clock

import io.music_assistant.sendspin.api.ClockQuality
import io.music_assistant.sendspin.api.MonotonicClock
import io.music_assistant.sendspin.wire.ServerTimePayload
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlin.concurrent.Volatile

/**
 * Server-time estimate shared between the session reader (replies), the probe
 * loop (bursts), and the audio thread (conversions). Replies of one burst are
 * collected and the lowest-RTT one feeds the filter; the audio thread reads an
 * immutable snapshot without taking the lock.
 */
internal class ClockSync(private val clock: MonotonicClock) {
    private class Sample(val t1: Long, val t2: Long, val t3: Long, val t4: Long) {
        val rtt: Long get() = (t4 - t1) - (t3 - t2)
    }

    private val lock = SynchronizedObject()
    private val filter = ClockFilter()
    private val burst = ArrayList<Sample>()

    @Volatile
    private var estimate: ClockFilter.Estimate? = null

    @Volatile
    private var lastAcceptedMicros = 0L

    /** A `server/time` reply; timestamped on arrival. */
    fun onReply(payload: ServerTimePayload) {
        val sample =
            Sample(payload.clientTransmitted, payload.serverReceived, payload.serverTransmitted, clock.nowMicros())
        synchronized(lock) { burst += sample }
    }

    /** Closes the current burst; feeds its best sample. Returns the reply count. */
    fun endBurst(): Int = synchronized(lock) {
        val best = burst.minByOrNull { it.rtt }
        val count = burst.size
        burst.clear()
        if (best != null && filter.update(best.t1, best.t2, best.t3, best.t4)) {
            estimate = filter.estimate()
            lastAcceptedMicros = best.t4
        }
        count
    }

    val isSynced: Boolean get() = estimate != null

    /** Converts a server timestamp to local time, or null before the first burst. */
    fun toLocalMicros(serverMicros: Long, nowMicros: Long = clock.nowMicros()): Long? =
        estimate?.toLocalMicros(serverMicros, nowMicros)

    fun quality(nowMicros: Long = clock.nowMicros()): ClockQuality {
        val current = estimate ?: return ClockQuality.Lost
        return when {
            nowMicros - lastAcceptedMicros > LOST_AFTER_MICROS -> ClockQuality.Lost
            current.rttMinMicros > DEGRADED_RTT_MICROS -> ClockQuality.Degraded
            else -> ClockQuality.Good
        }
    }

    /** Diagnostics only. */
    fun snapshot(): ClockFilter.Estimate? = estimate

    private companion object {
        const val DEGRADED_RTT_MICROS = 50_000L
        const val LOST_AFTER_MICROS = 60_000_000L
    }
}
