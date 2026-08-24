package io.music_assistant.client.data

import kotlin.test.Test
import kotlin.test.assertEquals

class RapidSkipTest {
    @Test
    fun `rapid next taps jump directly to final requested item`() {
        assertEquals(
            7,
            rapidSkipTargetIndex(currentIndex = 3, itemCount = 12, delta = 4),
        )
    }

    @Test
    fun `rapid previous taps jump backwards`() {
        assertEquals(
            2,
            rapidSkipTargetIndex(currentIndex = 6, itemCount = 12, delta = -4),
        )
    }

    @Test
    fun `rapid skip is clamped at queue boundaries`() {
        assertEquals(9, rapidSkipTargetIndex(currentIndex = 8, itemCount = 10, delta = 5))
        assertEquals(0, rapidSkipTargetIndex(currentIndex = 1, itemCount = 10, delta = -5))
    }
}
