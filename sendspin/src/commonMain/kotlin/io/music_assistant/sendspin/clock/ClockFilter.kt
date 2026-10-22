package io.music_assistant.sendspin.clock

import kotlin.math.abs
import kotlin.math.roundToLong

/** One `client/time` exchange: [t1] and [t4] are local, [t2] and [t3] server microseconds. */
internal class ClockSample(val t1: Long, val t2: Long, val t3: Long, val t4: Long) {
    val rtt: Long get() = (t4 - t1) - (t3 - t2)

    // Integer division truncating toward zero, as the spec defines them.
    val measurement: Long get() = ((t2 - t1) + (t3 - t4)) / 2
    val maxError: Long get() = rtt / 2
}

/**
 * The spec's time filter: a 2-state Kalman filter over (offset, drift) with
 * adaptive forgetting, using the spec's parameter values. offset = server clock
 * - local clock, in microseconds. Pure and single-threaded; [ClockSync] owns
 * the locking.
 *
 * One addition to the spec: [RESEED_AFTER] consecutive samples that each exclude
 * the pre-streak prediction by more than their own `max_error` (plus slack), on
 * the same side, re-seed the filter from the lowest-error one. A sample bounds
 * the true offset to `measurement ± max_error`, so such a streak means the clock
 * really moved (a doze pause, a server restart). The filter alone turns a step
 * into a ringing drift, and adaptive forgetting does not apply before
 * `min_samples`. A merely noisy sample never re-seeds, and every sample still
 * feeds the filter. The streak is judged against the prediction from before it
 * started, because the samples it feeds move the filter.
 */
internal class ClockFilter {
    class Estimate(
        val offsetMicros: Double,
        /** Applied drift (us per us); zero unless statistically significant. */
        val driftPerMicro: Double,
        val lastUpdateMicros: Long,
        val rttMinMicros: Long,
        val samples: Int,
    ) {
        /** The offset `compute_server_time` applies at [localMicros]. */
        fun offsetAt(localMicros: Long): Double = offsetMicros + driftPerMicro * (localMicros - lastUpdateMicros)

        /** The spec's `compute_client_time`. */
        fun toLocalMicros(serverMicros: Long): Long =
            roundHalfAwayFromZero(
                (serverMicros - offsetMicros + driftPerMicro * lastUpdateMicros) / (1 + driftPerMicro),
            )
    }

    private var offset = 0.0
    private var drift = 0.0
    private var pOO = Double.POSITIVE_INFINITY
    private var pOD = 0.0
    private var pDD = 0.0
    private var lastUpdate = 0L
    private var useDrift = false

    /** The spec's `count`: stops at [MIN_SAMPLES]. */
    var samples = 0
        private set

    /** Consecutive samples that exclude [anchor] on the same side; the lowest-error one. */
    private var streak = 0
    private var streakSign = 0
    private var streakBest: ClockSample? = null

    /** The prediction the streak is judged against. */
    private var anchor: Prediction? = null
    private val recentRtts = ArrayDeque<Long>()

    val rttMinMicros: Long get() = recentRtts.minOrNull() ?: Long.MAX_VALUE

    fun update(t1: Long, t2: Long, t3: Long, t4: Long): Boolean = update(ClockSample(t1, t2, t3, t4))

    /**
     * Feeds one measurement. Returns false when the spec discards it. A first
     * sample is never too early: the spec's `last_update` starts at 0, which
     * only a clock that starts at 0 can reach.
     */
    fun update(sample: ClockSample): Boolean {
        if ((samples > 0 && sample.t4 <= lastUpdate) || sample.maxError <= 0) return false
        rememberRtt(sample.rtt)
        val reseedFrom = contradiction(sample)
        if (reseedFrom != null) {
            reset()
            rememberRtt(reseedFrom.rtt)
        }
        apply(reseedFrom ?: sample)
        return true
    }

    fun estimate(): Estimate? = samples.takeIf { it > 0 }?.let {
        Estimate(offset, if (useDrift) drift else 0.0, lastUpdate, rttMinMicros, it)
    }

    /** Spec steps 2-8; step 1 (discard) is the caller's. */
    private fun apply(sample: ClockSample) {
        val dt = (sample.t4 - lastUpdate).toDouble()
        lastUpdate = sample.t4
        val measurement = sample.measurement.toDouble()
        val r = square(MAX_ERROR_SCALE * sample.maxError)
        when (samples) {
            0 -> {
                offset = measurement
                pOO = r
                drift = 0.0
                samples = 1
            }

            1 -> {
                drift = (measurement - offset) / dt
                pDD = (pOO + r) / (dt * dt)
                offset = measurement
                pOO = r
                samples = 2
            }

            else -> correct(measurement, sample.maxError, dt, r)
        }
    }

    private fun correct(measurement: Double, maxError: Long, dt: Double, r: Double) {
        val offsetP = offset + drift * dt
        var pOOp = pOO + 2 * pOD * dt + pDD * dt * dt + square(PROCESS_STD_DEV) * dt
        var pODp = pOD + pDD * dt
        var pDDp = pDD + square(DRIFT_PROCESS_STD_DEV) * dt

        val y = measurement - offsetP
        if (samples < MIN_SAMPLES) {
            samples++
        } else if (abs(y) > ADAPTIVE_CUTOFF * maxError) {
            val forget = square(FORGET_FACTOR)
            pOOp *= forget
            pODp *= forget
            pDDp *= forget
        }

        val s = pOOp + r
        val kO = pOOp / s
        val kD = pODp / s
        offset = offsetP + kO * y
        drift += kD * y
        pOO = pOOp - kO * pOOp
        pOD = pODp - kD * pOOp
        pDD = pDDp - kD * pODp
        useDrift = drift * drift > square(DRIFT_SIGNIFICANCE_THRESHOLD) * pDD
    }

    /**
     * Extends the contradiction streak with [sample] and returns its lowest-error
     * member once the streak is long enough to re-seed. A sample that does not
     * rule the prediction out, or one on the other side, restarts the streak.
     * The slack absorbs drift across the streak.
     */
    private fun contradiction(sample: ClockSample): ClockSample? {
        if (samples == 0) return null
        val y = sample.measurement - (anchor ?: prediction()).offsetAt(sample.t4)
        if (abs(y) <= sample.maxError + RESEED_SLACK_MICROS) {
            resetStreak()
            return null
        }
        val sign = if (y > 0) 1 else -1
        if (sign != streakSign) {
            resetStreak()
            anchor = prediction()
        }
        streakSign = sign
        streak++
        val best = streakBest?.takeIf { it.maxError <= sample.maxError } ?: sample
        streakBest = best
        return best.takeIf { streak >= RESEED_AFTER }
    }

    private fun prediction() = Prediction(offset, drift, lastUpdate)

    private class Prediction(val offset: Double, val drift: Double, val at: Long) {
        fun offsetAt(t: Long) = offset + drift * (t - at)
    }

    private fun resetStreak() {
        streak = 0
        streakSign = 0
        streakBest = null
        anchor = null
    }

    private fun reset() {
        offset = 0.0
        drift = 0.0
        pOO = Double.POSITIVE_INFINITY
        pOD = 0.0
        pDD = 0.0
        lastUpdate = 0L
        useDrift = false
        samples = 0
        resetStreak()
        recentRtts.clear()
    }

    private fun rememberRtt(rtt: Long) {
        recentRtts.addLast(rtt)
        if (recentRtts.size > RTT_WINDOW) recentRtts.removeFirst()
    }

    private companion object {
        // Spec parameter values.
        const val PROCESS_STD_DEV = 0.0
        const val DRIFT_PROCESS_STD_DEV = 1e-11
        const val FORGET_FACTOR = 2.0
        const val ADAPTIVE_CUTOFF = 3.0
        const val MIN_SAMPLES = 100
        const val DRIFT_SIGNIFICANCE_THRESHOLD = 2.0
        const val MAX_ERROR_SCALE = 0.5

        const val RESEED_AFTER = 3
        const val RESEED_SLACK_MICROS = 20_000L

        /** Bursts remembered for the minimum RTT: about a minute and a half at the steady cadence. */
        const val RTT_WINDOW = 12

        fun square(x: Double) = x * x
    }
}

/** The spec's `round`: nearest integer, halves away from zero. */
private fun roundHalfAwayFromZero(x: Double): Long = if (x < 0) -(-x).roundToLong() else x.roundToLong()
