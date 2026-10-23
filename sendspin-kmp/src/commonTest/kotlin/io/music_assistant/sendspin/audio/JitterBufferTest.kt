package io.music_assistant.sendspin.audio

import io.music_assistant.sendspin.wire.AudioChunk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JitterBufferTest {
    private fun chunk(ts: Long, bytes: Int = 10) = AudioChunk(ts, ByteArray(bytes), 0)

    @Test
    fun ordersByTimestampAndPollsTheHead() {
        val buffer = JitterBuffer(1_000)
        buffer.offer(chunk(30))
        buffer.offer(chunk(10))
        buffer.offer(chunk(20))
        assertEquals(listOf(10L, 20L, 30L), generateSequence { buffer.poll() }.map { it.timestampMicros }.toList())
        assertNull(buffer.poll())
    }

    @Test
    fun dropsReplayedAndDuplicateChunksUntilReset() {
        val buffer = JitterBuffer(1_000)
        buffer.offer(chunk(10))
        buffer.offer(chunk(20))
        assertEquals(JitterBuffer.Offer.Duplicate, buffer.offer(chunk(20)))
        buffer.poll()
        assertEquals(JitterBuffer.Offer.Stale, buffer.offer(chunk(10)), "already consumed")
        assertEquals(JitterBuffer.Offer.Stale, buffer.offer(chunk(5)))
        buffer.clear(resetDedup = true)
        assertEquals(JitterBuffer.Offer.Queued, buffer.offer(chunk(10)), "a discontinuity restarts the timeline")
    }

    @Test
    fun evictsTheFurthestFutureChunkOverTheByteCapWithoutSuspending() {
        val buffer = JitterBuffer(25)
        assertEquals(JitterBuffer.Offer.Queued, buffer.offer(chunk(10)))
        assertEquals(JitterBuffer.Offer.Queued, buffer.offer(chunk(20)))
        assertEquals(JitterBuffer.Offer.Evicted, buffer.offer(chunk(30)))
        assertEquals(2, buffer.size)
        assertEquals(20, buffer.byteCount)
        assertEquals(1, buffer.evicted)
        assertEquals(10L, buffer.peek()?.timestampMicros, "the head survives eviction")
        // A late-arriving early chunk evicts the newest, not itself.
        assertEquals(JitterBuffer.Offer.Evicted, buffer.offer(chunk(5)))
        assertEquals(listOf(5L, 10L), generateSequence { buffer.poll() }.map { it.timestampMicros }.toList())
    }

    @Test
    fun spanCountsFromTheLastConsumedChunk() {
        val buffer = JitterBuffer(1_000)
        assertEquals(0L, buffer.spanMicros)
        buffer.offer(chunk(1_000))
        buffer.offer(chunk(3_000))
        assertEquals(2_000L, buffer.spanMicros)
        buffer.poll()
        assertEquals(2_000L, buffer.spanMicros)
        buffer.poll()
        assertTrue(buffer.isEmpty)
        assertEquals(0L, buffer.spanMicros)
    }
}
