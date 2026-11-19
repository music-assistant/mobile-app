package io.music_assistant.sendspin.audio

import io.music_assistant.sendspin.wire.AudioChunk
import io.music_assistant.sendspin.wire.StreamStartPlayer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JitterBufferTest {
    private val pcm = StreamStartPlayer("pcm", 48_000, 2, 16)

    private fun chunk(ts: Long, bytes: Int = 10) = AudioChunk(ts, ByteArray(bytes), 0)

    private fun JitterBuffer.offer(chunk: AudioChunk) = offer(chunk, pcm)

    /** The scheduler's consume step: peek, then remove that head. */
    private fun JitterBuffer.poll(): AudioChunk? = peek()?.also { pollIf(it) }?.chunk

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
        assertEquals(10L, buffer.peek()?.chunk?.timestampMicros, "the head survives eviction")
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

    @Test
    fun clearFromDropsTheChunksFromTheCutAndKeepsTheRestAndTheDedupMemory() {
        val buffer = JitterBuffer(1_000)
        listOf(10L, 20L, 30L, 40L).forEach { buffer.offer(chunk(it)) }
        buffer.poll()
        assertEquals(2, buffer.clearFrom(30))
        assertEquals(1, buffer.size)
        assertEquals(10, buffer.byteCount)
        assertEquals(JitterBuffer.Offer.Stale, buffer.offer(chunk(10)), "audio already in the sink stays dropped")
        assertEquals(JitterBuffer.Offer.Queued, buffer.offer(chunk(30)), "the resent chunk replaces the cut one")
        assertEquals(listOf(20L, 30L), generateSequence { buffer.poll() }.map { it.timestampMicros }.toList())
    }

    @Test
    fun eachChunkKeepsTheFormatItArrivedIn() {
        val buffer = JitterBuffer(1_000)
        val flac = StreamStartPlayer("flac", 44_100, 2, 16)
        buffer.offer(chunk(10), pcm)
        buffer.offer(chunk(20), flac)
        buffer.clearFrom(20)
        buffer.offer(chunk(5), flac) // resent before the old boundary, in the latest format
        assertEquals(flac, buffer.peek()?.format)
        buffer.poll()
        assertEquals(pcm, buffer.peek()?.format)
    }

    @Test
    fun pollIfLeavesAHeadThatAClearReplaced() {
        val buffer = JitterBuffer(1_000)
        buffer.offer(chunk(10))
        val peeked = buffer.peek() ?: error("empty")
        buffer.clear(resetDedup = true)
        buffer.offer(chunk(50))
        assertFalse(buffer.pollIf(peeked))
        assertEquals(50L, buffer.peek()?.chunk?.timestampMicros)
    }
}
