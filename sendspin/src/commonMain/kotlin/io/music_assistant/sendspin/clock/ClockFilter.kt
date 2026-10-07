package io.music_assistant.sendspin.clock

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * NTP-style clock offset estimation with a 2-state Kalman filter over
 * (offset, drift). Pure and single-threaded; [ClockSync] owns the locking.
 *
 * offset = server clock - local clock, in microseconds. The filter is SEEDED
 * from the first measurement: starting at zero would make the first innovation
 * the entire ~10^12 us offset, and even a microscopic drift gain would then leak
 * that into the drift state and throw the offset off by seconds later.
 *
 * Measurement noise is `(rtt / 4)^2`, so low-RTT samples weigh more. An
 * innovation far outside what the recent minimum RTT can explain is rejected.
 * A sample bounds the true offset to `measured ± rtt / 2`; only three consecutive
 * samples that each exclude the estimate by that bound, on the same side, re-seed
 * the filter (the clock really moved). A merely noisy sample never moves it: its
 * RTT still enters the window, so a lasting RTT rise widens the gate instead.
 */
internal class ClockFilter {
    class Estimate(
        val offsetMicros: Double,
        /** Applied drift (us per us); zero unless statistically significant. */
        val driftPerMicro: Double,
        val atLocalMicros: Long,
        val rttMinMicros: Long,
        val samples: Int,
    ) {
        fun offsetAt(nowMicros: Long): Double = offsetMicros + driftPerMicro * (nowMicros - atLocalMicros)

        fun toLocalMicros(serverMicros: Long, nowMicros: Long): Long = (serverMicros - offsetAt(nowMicros)).toLong()
    }

    private var xOffset = 0.0
    private var xDrift = 0.0
    private var p00 = INITIAL_OFFSET_VARIANCE
    private var p01 = 0.0
    private var p10 = 0.0
    private var p11 = INITIAL_DRIFT_VARIANCE
    private var lastPredictMicros = 0L
    private var seeded = false

    /** Consecutive rejected samples that each exclude the estimate on the same side; the lowest-RTT one. */
    private var contradictions = 0
    private var contradictionBest: Contradiction? = null
    private val recentRtts = ArrayDeque<Long>()

    var samples = 0
        private set

    val rttMinMicros: Long get() = recentRtts.minOrNull() ?: Long.MAX_VALUE

    /**
     * Feeds one NTP four-timestamp measurement. Returns false when rejected as
     * an outlier. [t1] and [t4] are local, [t2] and [t3] server microseconds.
     */
    fun update(t1: Long, t2: Long, t3: Long, t4: Long): Boolean {
        val rtt = (t4 - t1) - (t3 - t2)
        if (rtt < 0) return false
        val measured = ((t2 - t1) + (t3 - t4)) / 2.0
        val scaledError = rtt * MAX_ERROR_SCALE / 2.0
        val variance = (scaledError * scaledError).coerceAtLeast(1.0)

        if (!seeded) {
            seed(measured, t4, variance)
            rememberRtt(rtt)
            return true
        }
        predict(t4)
        val innovation = measured - xOffset
        val gate = INNOVATION_GATE_RTT_FACTOR * rttMinMicros + INNOVATION_GATE_FLOOR_MICROS
        rememberRtt(rtt)
        if (abs(innovation) > gate) {
            val best = contradiction(innovation, measured, rtt, variance) ?: return false
            if (contradictions < REJECTIONS_BEFORE_RESEED) return false
            recentRtts.clear()
            seed(best.measured, t4, best.variance)
            rememberRtt(best.rtt)
            return true
        }
        resetContradictions()

        val s = p00 + variance
        val k0 = p00 / s
        val k1 = p10 / s
        xOffset += k0 * innovation
        xDrift += k1 * innovation
        val p00Pre = p00
        val p01Pre = p01
        p00 = (1 - k0) * p00Pre
        p01 = (1 - k0) * p01Pre
        p10 -= k1 * p00Pre
        p11 -= k1 * p01Pre
        samples++
        return true
    }

    fun estimate(): Estimate? {
        if (!seeded) return null
        val driftSnr = if (p11 > 0.0) abs(xDrift) / sqrt(p11) else 0.0
        val effectiveDrift = if (driftSnr >= DRIFT_SIGNIFICANCE) xDrift else 0.0
        return Estimate(xOffset, effectiveDrift, lastPredictMicros, rttMinMicros, samples)
    }

    /**
     * Extends the contradiction streak with this rejected sample and returns its
     * lowest-RTT member, or resets the streak and returns null when the sample
     * does not rule the estimate out. A contradiction on the other side starts a
     * new streak. The slack absorbs drift across the streak.
     */
    private fun contradiction(innovation: Double, measured: Double, rtt: Long, variance: Double): Contradiction? {
        if (abs(innovation) <= rtt / 2.0 + INNOVATION_GATE_FLOOR_MICROS) {
            resetContradictions()
            return null
        }
        val sign = if (innovation > 0) 1 else -1
        if (contradictionBest?.sign != sign) resetContradictions()
        contradictions++
        val best = contradictionBest?.takeIf { it.rtt <= rtt } ?: Contradiction(measured, rtt, variance, sign)
        contradictionBest = best
        return best
    }

    private fun resetContradictions() {
        contradictions = 0
        contradictionBest = null
    }

    private fun seed(measured: Double, atMicros: Long, variance: Double) {
        seeded = true
        resetContradictions()
        xOffset = measured
        xDrift = 0.0
        lastPredictMicros = atMicros
        p00 = variance
        p01 = 0.0
        p10 = 0.0
        p11 = INITIAL_DRIFT_VARIANCE
        samples = 1
    }

    private fun predict(nowMicros: Long) {
        val dt = (nowMicros - lastPredictMicros).coerceAtLeast(0L).toDouble()
        lastPredictMicros = nowMicros
        xOffset += xDrift * dt
        val p00New = p00 + dt * p10 + dt * p01 + dt * dt * p11
        val p01New = p01 + dt * p11
        val p10New = p10 + dt * p11
        val p11New = p11 + DRIFT_PROCESS_VARIANCE * dt
        p00 = p00New
        p01 = p01New
        p10 = p10New
        p11 = p11New
    }

    private class Contradiction(val measured: Double, val rtt: Long, val variance: Double, val sign: Int)

    private fun rememberRtt(rtt: Long) {
        recentRtts.addLast(rtt)
        if (recentRtts.size > RTT_WINDOW) recentRtts.removeFirst()
    }

    private companion object {
        const val INITIAL_OFFSET_VARIANCE = 1e12
        const val INITIAL_DRIFT_VARIANCE = 1e-6
        const val DRIFT_PROCESS_VARIANCE = 1e-22
        const val MAX_ERROR_SCALE = 0.5
        const val DRIFT_SIGNIFICANCE = 2.0
        const val INNOVATION_GATE_RTT_FACTOR = 3
        const val INNOVATION_GATE_FLOOR_MICROS = 20_000L
        const val REJECTIONS_BEFORE_RESEED = 3

        /** Bursts remembered for the minimum RTT: about two minutes at the steady cadence. */
        const val RTT_WINDOW = 12
    }
}
