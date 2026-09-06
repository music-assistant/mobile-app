package io.music_assistant.sendspin.api

import kotlin.test.Test
import kotlin.test.assertEquals

class MonotonicFrameCounterTest {
    private val range = 0x1_0000_0000L

    @Test
    fun countsStayContinuousAcrossA32BitWrap() {
        val counter = MonotonicFrameCounter()
        assertEquals(range - 480, counter.extend(range - 480))
        assertEquals(range - 1, counter.extend(range - 1))
        assertEquals(range + 479, counter.extend(479), "just after the wrap")
        assertEquals(range + 48_000, counter.extend(48_000))
        assertEquals(2 * range - 5, counter.extend(range - 5))
        assertEquals(2 * range + 10, counter.extend(10), "second wrap")
    }

    @Test
    fun aRawReadingBeyond32BitsIsReducedToTheSameBase() {
        // AudioTimestamp.framePosition is a long; some devices report it wrapped, some not.
        val counter = MonotonicFrameCounter()
        counter.extend(range - 10)
        assertEquals(range + 5, counter.extend(range + 5))
    }

    @Test
    fun smallRewindsAreNotWraps() {
        val counter = MonotonicFrameCounter()
        counter.extend(1_000)
        assertEquals(900, counter.extend(900))
    }

    @Test
    fun resetStartsANewBase() {
        val counter = MonotonicFrameCounter()
        counter.extend(range - 1)
        counter.extend(5)
        counter.reset()
        assertEquals(7, counter.extend(7))
    }
}
