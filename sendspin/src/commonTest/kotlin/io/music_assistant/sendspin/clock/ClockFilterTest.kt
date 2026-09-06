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
        val error = abs(later.toLocalMicros(now + 500_000L + trueOffset, now + 500_000L) - (now + 500_000L))
        assertTrue(error < 1_000, "offset drifted by $error us")
    }

    @Test
    fun rejectsOutliersThenReseedsAfterThreeInARow() {
        val filter = ClockFilter()
        var now = 0L
        repeat(5) { now += 2_000_000L; exact(filter, now) }
        val before = assertNotNull(filter.estimate()).offsetMicros

        // One wildly wrong sample (server clock appears 5 s further ahead) is rejected.
        now += 2_000_000L
        val jump = 5_000_000L
        val t2 = now + 2_500L + trueOffset + jump
        assertFalse(filter.update(now, t2, t2 + 1_000, now + 6_000L))
        assertEquals(before, assertNotNull(filter.estimate()).offsetMicros)

        // Three consecutive: the clock really moved, so the filter re-seeds there.
        repeat(2) {
            now += 2_000_000L
            val t2b = now + 2_500L + trueOffset + jump
            filter.update(now, t2b, t2b + 1_000, now + 6_000L)
        }
        val after = assertNotNull(filter.estimate()).offsetMicros
        assertTrue(abs(after - (trueOffset + jump)) < 1_000, "re-seeded at $after")
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
                val local = assertNotNull(sync.toLocalMicros(localNow + trueOffset, localNow))
                worst = maxOf(worst, abs(local - localNow))
            }
        }
        assertTrue(worst < 10_000, "worst offset error ${worst / 1000.0} ms")
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
