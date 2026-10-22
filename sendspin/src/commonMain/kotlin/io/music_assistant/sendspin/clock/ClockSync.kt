package io.music_assistant.sendspin.clock

import co.touchlab.kermit.Logger
import io.music_assistant.sendspin.api.ClockQuality
import io.music_assistant.sendspin.api.MonotonicClock
import io.music_assistant.sendspin.wire.ServerTimePayload
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.concurrent.Volatile
import kotlin.math.abs

/**
 * Server-time estimate shared between the session reader (replies), the probe
 * loop (bursts), and the audio thread (conversions). Replies of one burst are
 * collected and the one with the lowest `max_error` feeds the filter; the audio
 * thread reads an immutable snapshot without taking the lock.
 */
internal class ClockSync(private val clock: MonotonicClock) {
    private val logger = Logger.withTag("ClockSync")
    private val lock = SynchronizedObject()
    private val filter = ClockFilter()
    private val burst = ArrayList<ClockSample>()
    private val _replies = MutableStateFlow(0)

    /** Replies received so far; the probe loop waits on it before sending the next probe. */
    val replies: StateFlow<Int> = _replies.asStateFlow()

    @Volatile
    private var estimate: ClockFilter.Estimate? = null

    @Volatile
    private var lastAcceptedMicros = 0L

    /** A `server/time` reply; timestamped on arrival. */
    fun onReply(payload: ServerTimePayload) {
        val sample =
            ClockSample(payload.clientTransmitted, payload.serverReceived, payload.serverTransmitted, clock.nowMicros())
        synchronized(lock) { burst += sample }
        _replies.update { it + 1 }
    }

    /**
     * Closes the current burst; feeds its best sample. Returns the reply count.
     * A reply the filter would discard (`max_error <= 0`) cannot be the best one.
     */
    fun endBurst(): Int = synchronized(lock) {
        val best = burst.filter { it.maxError > 0 }.minByOrNull { it.maxError }
        val count = burst.size
        burst.clear()
        if (best != null) {
            val before = estimate
            if (filter.update(best)) {
                val after = filter.estimate()
                estimate = after
                lastAcceptedMicros = best.t4
                logStep(before, after, best)
            } else {
                logger.i { "Clock sample discarded: rttUs=${best.rtt} t4=${best.t4}" }
            }
        }
        count
    }

    /** Re-seeds and large steps only: each moves every chunk target at once. */
    private fun logStep(before: ClockFilter.Estimate?, after: ClockFilter.Estimate?, sample: ClockSample) {
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

    /** Two samples, offset and drift both estimated: enough to report `available` (as aiosendspin does). */
    val isConverged: Boolean get() = (estimate?.samples ?: 0) >= CONVERGED_SAMPLES

    /** Converts a server timestamp to local time, or null before the first burst. */
    fun toLocalMicros(serverMicros: Long): Long? = estimate?.toLocalMicros(serverMicros)

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
        const val CONVERGED_SAMPLES = 2
        const val DEGRADED_RTT_MICROS = 50_000L
        const val LOST_AFTER_MICROS = 60_000_000L
        const val STEP_LOG_MICROS = 5_000L
        const val MICROS_PER_MILLI = 1_000L
    }
}
