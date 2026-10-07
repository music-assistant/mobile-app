package io.music_assistant.sendspin.clock

import co.touchlab.kermit.Logger
import io.music_assistant.sendspin.api.ClockQuality
import io.music_assistant.sendspin.api.MonotonicClock
import io.music_assistant.sendspin.wire.ServerTimePayload
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlin.concurrent.Volatile
import kotlin.math.abs

/**
 * Server-time estimate shared between the session reader (replies), the probe
 * loop (bursts), and the audio thread (conversions). Replies of one burst are
 * collected and the lowest-RTT one feeds the filter; the audio thread reads an
 * immutable snapshot without taking the lock.
 */
internal class ClockSync(private val clock: MonotonicClock) {
    private class Sample(val t1: Long, val t2: Long, val t3: Long, val t4: Long) {
        val rtt: Long get() = (t4 - t1) - (t3 - t2)
        val measured: Double get() = ((t2 - t1) + (t3 - t4)) / 2.0
    }

    private val logger = Logger.withTag("ClockSync")
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
        if (best != null) {
            val before = estimate
            if (filter.update(best.t1, best.t2, best.t3, best.t4)) {
                val after = filter.estimate()
                estimate = after
                lastAcceptedMicros = best.t4
                logStep(before, after, best)
            } else {
                logger.i {
                    "Clock sample rejected: rttMs=${best.rtt / MICROS_PER_MILLI} " +
                        "innovationUs=${before?.let { (best.measured - it.offsetAt(best.t4)).toLong() }}"
                }
            }
        }
        count
    }

    /** Re-seeds and large steps only: each moves every chunk target at once. */
    private fun logStep(before: ClockFilter.Estimate?, after: ClockFilter.Estimate?, sample: Sample) {
        if (before == null || after == null) return
        val stepMicros = (after.offsetAt(sample.t4) - before.offsetAt(sample.t4)).toLong()
        val reseeded = after.samples == 1
        if (!reseeded && abs(stepMicros) < STEP_LOG_MICROS) return
        logger.w {
            "Clock ${if (reseeded) "re-seeded" else "step"}: stepUs=$stepMicros rttMs=${sample.rtt / MICROS_PER_MILLI} " +
                "rttMinMs=${after.rttMinMicros / MICROS_PER_MILLI} samples=${before.samples}"
        }
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
        const val STEP_LOG_MICROS = 5_000L
        const val MICROS_PER_MILLI = 1_000L
    }
}
