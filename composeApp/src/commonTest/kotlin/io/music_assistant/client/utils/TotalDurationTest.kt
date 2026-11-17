package io.music_assistant.client.utils

import kotlin.test.Test
import kotlin.test.assertEquals

class TotalDurationTest {
    @Test
    fun roundsToTheMinute() {
        assertEquals(TotalDuration(0, 7), 420.0.toTotalDuration())
        assertEquals(TotalDuration(0, 8), 450.0.toTotalDuration())
        assertEquals(TotalDuration(0, 7), 449.0.toTotalDuration())
    }

    @Test
    fun splitsHours() {
        assertEquals(TotalDuration(1, 23), (83 * 60.0).toTotalDuration())
        assertEquals(TotalDuration(2, 0), (120 * 60.0).toTotalDuration())
        assertEquals(TotalDuration(11, 44), (11 * 3600 + 44 * 60 + 8.0).toTotalDuration())
    }

    @Test
    fun neverUnderOneMinute() {
        assertEquals(TotalDuration(0, 1), 0.0.toTotalDuration())
        assertEquals(TotalDuration(0, 1), 12.0.toTotalDuration())
    }
}
