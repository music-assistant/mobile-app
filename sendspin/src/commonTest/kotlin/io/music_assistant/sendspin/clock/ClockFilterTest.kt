package io.music_assistant.sendspin.clock

import io.music_assistant.sendspin.api.MonotonicClock
import io.music_assistant.sendspin.wire.ServerTimePayload
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClockFilterTest {
    /** A realistic server clock: ~3.45e12 us ahead of the local monotonic clock. */
    private val trueOffset = 3_450_000_000_000L

    /** One exact probe with symmetric one-way delay [oneWayMicros] at local time [t1]. */
    private fun exact(filter: ClockFilter, t1: Long, oneWayMicros: Long = 2_500L): Boolean {
        val t2 = t1 + oneWayMicros + trueOffset
        val t3 = t2 + 1_000L
        val t4 = t3 - trueOffset + oneWayMicros
        return filter.update(t1, t2, t3, t4)
    }

    @Test
    fun seedsFromTheFirstMeasurementWithoutDriftBlowUp() {
        val filter = ClockFilter()
        assertNull(filter.estimate())
        var now = 1_000_000L
        exact(filter, now)
        val seeded = assertNotNull(filter.estimate())
        assertEquals(0.0, seeded.driftPerMicro)
        assertTrue(abs(seeded.offsetMicros - trueOffset) < 1_000, "seed offset ${seeded.offsetMicros}")

        // Sparse bursts every 10 s: the estimate must stay put for minutes.
        repeat(30) {
            now += 10_000_000L
            exact(filter, now)
        }
        val later = assertNotNull(filter.estimate())
        val error = abs(later.toLocalMicros(now + 500_000L + trueOffset) - (now + 500_000L))
        assertTrue(error < 1_000, "offset drifted by $error us")
    }

    @Test
    fun feedsOutliersThenReseedsAfterThreeInARow() {
        val filter = ClockFilter()
        var now = 0L
        repeat(5) {
            now += 2_000_000L
            exact(filter, now)
        }

        // The server clock jumps 5 s ahead. The spec filter takes the first sample, weighted
        // by its max_error, so the estimate moves towards it without reaching it.
        val jump = 5_000_000L
        fun jumped() {
            now += 2_000_000L
            val t2 = now + 2_500L + trueOffset + jump
            assertTrue(filter.update(now, t2, t2 + 1_000, now + 6_000L))
        }
        jumped()
        val moved = assertNotNull(filter.estimate()).offsetMicros - trueOffset
        assertTrue(moved > 0 && moved < jump, "moved by $moved")

        // Three consecutive: the clock really moved, so the filter re-seeds there.
        repeat(2) { jumped() }
        val after = assertNotNull(filter.estimate())
        assertEquals(1, after.samples)
        assertTrue(abs(after.offsetMicros - (trueOffset + jump)) < 1_000, "re-seeded at ${after.offsetMicros}")
    }

    @Test
    fun tracksAConstantDriftOnceSignificant() {
        val filter = ClockFilter()
        val ppm = 50.0 // a cheap crystal
        var now = 0L
        fun probe() {
            val serverOffset = trueOffset + (now * ppm / 1e6).toLong()
            val t2 = now + 2_500L + serverOffset
            filter.update(now, t2, t2 + 1_000L, now + 6_000L)
        }
        repeat(150) {
            now += 9_000_000L
            probe()
        }
        val estimate = assertNotNull(filter.estimate())
        assertTrue(abs(estimate.driftPerMicro * 1e6 - ppm) < 1.0, "drift ${estimate.driftPerMicro * 1e6} ppm")

        // A chunk due 5 s after the last sample: the conversion must apply the drift.
        val later = now + 5_000_000L
        val serverLater = later + trueOffset + (later * ppm / 1e6).toLong()
        val error = abs(estimate.toLocalMicros(serverLater) - later)
        assertTrue(error < 500, "error $error us")
    }

    @Test
    fun discardsSamplesTheSpecDiscards() {
        val filter = ClockFilter()
        assertTrue(exact(filter, 1_000_000L))
        // time_added (t4) not after the last update.
        assertFalse(exact(filter, 1_000_000L))
        // max_error 0.
        assertFalse(filter.update(t1 = 2_000_000L, t2 = 3_000_000L, t3 = 3_000_000L, t4 = 2_000_000L + 1))
        assertEquals(1, assertNotNull(filter.estimate()).samples)
    }

    @Test
    fun noisierNetworkWithinTheRttBoundNeverReseeds() {
        val filter = ClockFilter()
        var now = 0L
        repeat(12) {
            now += 10_000_000L
            exact(filter, now)
        }

        // The network turns slow and one-sided (a queue in the downstream path only):
        // every sample reads 90 ms off on the same side, but within its own rtt / 2.
        // A lasting one-way delay is unobservable, so a slow pull is fine; a step is not.
        var previous = assertNotNull(filter.estimate()).offsetMicros
        var worstStep = 0.0
        repeat(30) {
            now += 10_000_000L
            val t2 = now + 10_000L + trueOffset
            val t3 = t2 + 1_000L
            filter.update(now, t2, t3, t3 - trueOffset + 190_000L)
            val offset = assertNotNull(filter.estimate()).offsetMicros
            worstStep = maxOf(worstStep, abs(offset - previous))
            previous = offset
        }
        assertTrue(worstStep < 2_000, "estimate stepped by ${worstStep / 1000.0} ms")
    }

    @Test
    fun matchesALiteralTranscriptionOfTheSpecUpdate() {
        val random = Random(303)
        val filter = ClockFilter()
        val reference = SpecReference()
        var now = 1_000_000L
        // Past min_samples, with residuals that cross the adaptive cutoff but never re-seed.
        repeat(300) {
            now += 9_000_000L + random.nextLong(0, 1_000_000L)
            val maxError = random.nextLong(1_000L, 5_000L)
            val measurement = trueOffset + now * 40 / 1_000_000 + random.nextLong(-15_000L, 15_000L)
            val t1 = now - 2 * maxError
            val t2 = t1 + maxError + measurement
            assertTrue(filter.update(t1, t2, t2, now))
            reference.update(measurement, maxError, now)

            val estimate = assertNotNull(filter.estimate())
            assertEquals(reference.offset, estimate.offsetMicros)
            assertEquals(if (reference.useDrift) reference.drift else 0.0, estimate.driftPerMicro)
            assertEquals(reference.lastUpdate, estimate.lastUpdateMicros)
        }
    }

    /** The spec's time filter update, step by step, with the spec parameter values and no re-seed. */
    private class SpecReference {
        var offset = 0.0
        var drift = 0.0
        var pOO = Double.POSITIVE_INFINITY
        var pOD = 0.0
        var pDD = 0.0
        var lastUpdate = 0L
        var count = 0
        var useDrift = false

        fun update(measurement: Long, maxError: Long, timeAdded: Long) {
            if (timeAdded <= lastUpdate || maxError <= 0) return
            val dt = (timeAdded - lastUpdate).toDouble()
            lastUpdate = timeAdded
            val r = (0.5 * maxError) * (0.5 * maxError)
            if (count == 0) {
                offset = measurement.toDouble()
                pOO = r
                drift = 0.0
                count = 1
                return
            }
            if (count == 1) {
                drift = (measurement - offset) / dt
                pDD = (pOO + r) / (dt * dt)
                offset = measurement.toDouble()
                pOO = r
                count = 2
                return
            }
            val offsetP = offset + drift * dt
            var pOOp = pOO + 2 * pOD * dt + pDD * dt * dt + 0.0 * 0.0 * dt
            var pODp = pOD + pDD * dt
            var pDDp = pDD + 1e-11 * 1e-11 * dt
            val y = measurement - offsetP
            if (count < 100) {
                count++
            } else if (abs(y) > 3.0 * maxError) {
                pOOp *= 2.0 * 2.0
                pODp *= 2.0 * 2.0
                pDDp *= 2.0 * 2.0
            }
            val s = pOOp + r
            val kO = pOOp / s
            val kD = pODp / s
            offset = offsetP + kO * y
            drift += kD * y
            pOO = pOOp - kO * pOOp
            pOD = pODp - kD * pOOp
            pDD = pDDp - kD * pODp
            useDrift = drift * drift > 2.0 * 2.0 * pDD
        }
    }

    @Test
    fun negativeRttIsIgnored() {
        val filter = ClockFilter()
        assertFalse(filter.update(t1 = 100, t2 = 200, t3 = 500, t4 = 150))
        assertNull(filter.estimate())
    }

    @Test
    fun burstMinRttSelectionStaysStableUnderAsymmetricCellularJitter() {
        val random = Random(2026)
        var localNow = 0L
        val clock = MonotonicClock { localNow }
        val sync = ClockSync(clock)

        var worst = 0L
        var worstSettled = 0L
        repeat(60) { burstIndex ->
            repeat(ClockProbe.PROBES_PER_BURST) {
                val t1 = localNow
                val up = 5_000L + random.nextLong(0, 80_000L)
                val down = 5_000L + random.nextLong(0, 80_000L)
                val t2 = t1 + up + trueOffset
                val t3 = t2 + 1_000L
                localNow = t3 - trueOffset + down
                sync.onReply(ServerTimePayload(clientTransmitted = t1, serverReceived = t2, serverTransmitted = t3))
                localNow += 100_000L
            }
            assertEquals(ClockProbe.PROBES_PER_BURST, sync.endBurst())
            localNow += 10_000_000L
            if (burstIndex >= 10) {
                val local = assertNotNull(sync.toLocalMicros(localNow + trueOffset))
                worst = maxOf(worst, abs(local - localNow))
                if (burstIndex >= 25) worstSettled = maxOf(worstSettled, abs(local - localNow))
            }
        }
        // The spec filter briefly takes this jitter for a drift (about 10 ms of error
        // around burst 16), then settles.
        assertTrue(worst < 15_000, "worst offset error ${worst / 1000.0} ms")
        assertTrue(worstSettled < 5_000, "worst settled offset error ${worstSettled / 1000.0} ms")
    }

    @Test
    fun qualityReflectsRttAndStaleness() {
        var localNow = 0L
        val sync = ClockSync { localNow }
        assertEquals(io.music_assistant.sendspin.api.ClockQuality.Lost, sync.quality())
        val t1 = localNow
        localNow += 5_000L
        sync.onReply(ServerTimePayload(t1, t1 + 2_500L + trueOffset, t1 + 3_500L + trueOffset))
        sync.endBurst()
        assertEquals(io.music_assistant.sendspin.api.ClockQuality.Good, sync.quality())
        localNow += 61_000_000L
        assertEquals(io.music_assistant.sendspin.api.ClockQuality.Lost, sync.quality())
    }
}
