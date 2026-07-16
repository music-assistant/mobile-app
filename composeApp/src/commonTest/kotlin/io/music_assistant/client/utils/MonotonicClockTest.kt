package io.music_assistant.client.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertTrue

class MonotonicClockTest {
    @Test
    fun repeatedReadingsNeverDecrease() {
        var previous = monotonicMs()
        repeat(1000) {
            val next = monotonicMs()
            assertTrue(next >= previous, "clock went backwards: $previous -> $next")
            previous = next
        }
    }

    @Test
    fun advancesWithRealElapsedTime() = runTest {
        val before = monotonicMs()
        // Real (non-virtual) delay: runTest skips delays on its own scheduler,
        // so hop to a real dispatcher for the wait.
        withContext(Dispatchers.Default) { delay(60) }
        val after = monotonicMs()
        assertTrue(after - before >= 40, "expected >=40ms advance, got ${after - before}ms")
    }
}
