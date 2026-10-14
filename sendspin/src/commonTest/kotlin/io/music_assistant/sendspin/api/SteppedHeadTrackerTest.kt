package io.music_assistant.sendspin.api

import kotlin.test.Test
import kotlin.test.assertEquals

class SteppedHeadTrackerTest {
    /** 20 ms at 48 kHz: one normal-mixer period. */
    private val step = 960L

    @Test
    fun aReadingBetweenStepsKeepsTheTimeTheHeadMoved() {
        val head = SteppedHeadTracker(48_000)
        head.position(0, 0)
        head.position(step, 20_000)
        // 15 ms later the head has not moved: the reading is 15 ms old, not fresh.
        assertEquals(SinkPosition(step, 20_000), head.position(step, 35_000))
    }

    @Test
    fun noExtrapolationBeforeTheFirstStep() {
        // A fresh track holds its head at 0 for a few hundred ms before it plays.
        val head = SteppedHeadTracker(48_000)
        head.position(0, 0)
        assertEquals(SinkPosition(0, 300_000), head.position(0, 300_000))
    }

    @Test
    fun aStalledHeadIsReportedNoOlderThanOneStep() {
        val head = SteppedHeadTracker(48_000)
        head.position(0, 0)
        head.position(step, 20_000)
        // Underrun: the head stops. Assume at most one more step of playback.
        assertEquals(SinkPosition(step, 80_000), head.position(step, 100_000))
    }

    @Test
    fun theCapFollowsTheLastStepSize() {
        val head = SteppedHeadTracker(48_000)
        head.position(0, 0)
        head.position(2 * step, 40_000) // a 40 ms deep-buffer step
        assertEquals(SinkPosition(2 * step, 40_000), head.position(2 * step, 75_000))
        assertEquals(SinkPosition(2 * step, 50_000), head.position(2 * step, 90_000))
    }

    @Test
    fun resetForgetsTheStep() {
        val head = SteppedHeadTracker(48_000)
        head.position(0, 0)
        head.position(step, 20_000)
        head.reset()
        head.position(0, 50_000)
        assertEquals(SinkPosition(0, 90_000), head.position(0, 90_000))
    }

    @Test
    fun aRewindWithoutResetIsNotAStep() {
        val head = SteppedHeadTracker(48_000)
        head.position(0, 0)
        head.position(step, 20_000)
        head.position(100, 30_000)
        assertEquals(SinkPosition(100, 60_000), head.position(100, 60_000))
    }
}
