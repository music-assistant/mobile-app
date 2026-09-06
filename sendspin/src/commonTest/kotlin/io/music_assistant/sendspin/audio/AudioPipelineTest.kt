package io.music_assistant.sendspin.audio

import io.music_assistant.sendspin.api.AudioPhase
import io.music_assistant.sendspin.api.MonotonicClock
import io.music_assistant.sendspin.api.SinkEvent
import io.music_assistant.sendspin.clock.ClockSync
import io.music_assistant.sendspin.fakes.FakeDecoderFactory
import io.music_assistant.sendspin.fakes.FakeSink
import io.music_assistant.sendspin.wire.AudioChunk
import io.music_assistant.sendspin.wire.AudioCodec
import io.music_assistant.sendspin.wire.ServerTimePayload
import io.music_assistant.sendspin.wire.StreamStartPlayer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Pipeline and scheduler over a [FakeSink] whose device runs at nominal rate.
 * The server clock equals the local clock here (offset 0), so a chunk with
 * timestamp T is due at local T + userDelay.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AudioPipelineTest {
    private val flac = StreamStartPlayer("flac", 48_000, 2, 16)
    private val bytesPerFrame = 4

    private inner class Harness(val scope: TestScope) {
        val clock = MonotonicClock { scope.currentTime * 1_000 }
        val sink = FakeSink { clock.nowMicros() }
        val decoders = FakeDecoderFactory()
        val clockSync = ClockSync(clock).apply {
            onReply(ServerTimePayload(0, 0, 0))
            endBurst()
        }
        val pipeline = AudioPipeline(sink, decoders, clockSync, clock, capacityBytes = 10_000_000)
        val events = Channel<AudioEvent>(Channel.UNLIMITED)
        val job: Job = scope.launch {
            launch { pipeline.events.collect { events.trySend(it) } }
            pipeline.run()
        }

        val handle: FakeSink.Handle get() = sink.handles.last()

        /** A chunk of [millis] of PCM due at [dueMillis] local time. */
        fun chunk(dueMillis: Long, millis: Int = 10): AudioChunk {
            val bytes = ByteArray(millis * 48 * bytesPerFrame) { (it % 7 + 1).toByte() }
            return AudioChunk(dueMillis * 1_000, bytes, 0)
        }

        fun feed(vararg dueMillis: Long) = dueMillis.forEach { pipeline.onAudio(chunk(it)) }
    }

    private fun pipelineTest(block: suspend TestScope.(Harness) -> Unit) = runTest {
        val h = Harness(this)
        runCurrent()
        try {
            block(h)
        } finally {
            h.job.cancel()
        }
    }

    @Test
    fun chunksArrivingDuringSinkBuildAreKeptAndPlayed() = pipelineTest { h ->
        // The server bursts while the sink is being built: nothing may be lost.
        h.sink.onOpen = { h.feed(0, 10, 20, 30) }
        h.pipeline.apply(StreamAction.StartFresh(flac))
        runCurrent()
        assertEquals(4, h.handle.writes.size)
        assertEquals(AudioEvent.Started, h.events.tryReceive().getOrNull())
        assertEquals(AudioPhase.Playing, h.pipeline.status.value.phase)
    }

    @Test
    fun everyChunkIncludingTheTailIsPlayed() = pipelineTest { h ->
        h.pipeline.apply(StreamAction.StartFresh(flac))
        runCurrent()
        repeat(10) { h.feed(it * 10L) }
        runCurrent()
        assertEquals(10, h.handle.writes.size)
        assertTrue(h.pipeline.buffer.isEmpty)
        h.pipeline.apply(StreamAction.End)
        runCurrent()
        assertEquals(AudioPhase.Idle, h.pipeline.status.value.phase)
        assertTrue(h.handle.paused)
        assertEquals(1, h.handle.flushes)
        assertFalse(h.handle.closed, "the sink stays warm after stream/end")
    }

    @Test
    fun replayedChunksAreNotPlayedTwiceButAFreshStartRestartsTheTimeline() = pipelineTest { h ->
        h.pipeline.apply(StreamAction.StartFresh(flac))
        runCurrent()
        h.feed(0, 10, 20)
        runCurrent()
        assertEquals(3, h.handle.writes.size)
        h.feed(10, 20, 30) // reconnect overlap
        runCurrent()
        assertEquals(4, h.handle.writes.size)

        h.pipeline.apply(StreamAction.StartFresh(flac))
        runCurrent()
        assertEquals(2, h.sink.handles.size, "a real stream/start rebuilds the sink")
        assertTrue(h.sink.handles.first().closed)
        h.feed(0, 10)
        runCurrent()
        assertEquals(2, h.handle.writes.size)
    }

    @Test
    fun endAndClearAreNeverLostInABurst() = pipelineTest { h ->
        h.pipeline.apply(StreamAction.StartFresh(flac))
        h.pipeline.apply(StreamAction.End)
        h.pipeline.apply(StreamAction.Clear)
        h.pipeline.apply(StreamAction.StartFresh(flac))
        runCurrent()
        h.feed(0)
        runCurrent()
        val seen = generateSequence { h.events.tryReceive().getOrNull() }.toList()
        assertEquals(listOf(AudioEvent.Ended, AudioEvent.Cleared, AudioEvent.Started), seen)
        assertEquals(StreamPhase.Playing, h.pipeline.phase)
    }

    @Test
    fun resumeKeepsBufferSinkAndDecoder() = pipelineTest { h ->
        h.pipeline.apply(StreamAction.StartFresh(flac))
        runCurrent()
        h.feed(0, 1_000, 2_000)
        runCurrent()
        h.pipeline.apply(StreamAction.ResumeKeepBuffer)
        runCurrent()
        assertEquals(1, h.sink.handles.size)
        assertEquals(1, h.decoders.created.size)
        assertEquals(2, h.pipeline.buffer.size, "future chunks survive the resume")
    }

    @Test
    fun withPositionFeedbackEarlyInsertsSilenceLateDropsAndSmallDriftResamples() = pipelineTest { h ->
        h.pipeline.apply(StreamAction.StartFresh(flac))
        runCurrent()
        // Chunk due now: plain write of 10 ms.
        h.feed(0)
        runCurrent()
        assertEquals(1, h.handle.writes.size)
        val plain = h.handle.writes[0].size
        // 10 ms queued; chunks due at 30 and 60 ms are 20 and 40 ms early: resampled, net longer.
        h.feed(30, 60)
        runCurrent()
        assertEquals(3, h.handle.writes.size)
        val resampled = h.handle.writes[1].size + h.handle.writes[2].size
        assertTrue(resampled >= 2 * plain && resampled < 2 * plain * 1.01, "resampled $resampled vs ${2 * plain}")
        // 30 ms queued; a chunk due at 220 ms is ~190 ms early: silence first, then the chunk.
        h.feed(220)
        runCurrent()
        assertEquals(5, h.handle.writes.size)
        val silence = h.handle.writes[3]
        assertTrue(silence.all { it == 0.toByte() })
        assertTrue(silence.size in 180 * 48 * bytesPerFrame..200 * 48 * bytesPerFrame, "silence ${silence.size}")
        // A chunk far in the past is dropped without a write.
        h.pipeline.onAudio(h.chunk(-500))
        runCurrent()
        assertEquals(5, h.handle.writes.size)
    }

    @Test
    fun withoutPositionFeedbackTheLoopIsOpenWaitUntilDueAndDropLate() = pipelineTest { h ->
        h.sink.reportPosition = false
        h.sink.latencyMicros = null
        h.pipeline.apply(StreamAction.StartFresh(flac))
        runCurrent()
        h.feed(50)
        runCurrent()
        assertEquals(0, h.handle.writes.size, "not due yet")
        advanceTimeBy(51)
        runCurrent()
        assertEquals(1, h.handle.writes.size)
        assertTrue(h.handle.writes[0].none { it == 0.toByte() }, "no silence inserted in open loop")
        h.pipeline.onAudio(h.chunk(-100))
        runCurrent()
        assertEquals(1, h.handle.writes.size, "late chunk dropped")
    }

    @Test
    fun userDelayShiftsTheTarget() = pipelineTest { h ->
        h.sink.reportPosition = false
        h.pipeline.userDelayMicros = 100_000
        h.pipeline.apply(StreamAction.StartFresh(flac))
        runCurrent()
        h.feed(0)
        runCurrent()
        assertEquals(0, h.handle.writes.size, "delayed by the user lag")
        advanceTimeBy(101)
        runCurrent()
        assertEquals(1, h.handle.writes.size)
    }

    @Test
    fun starvedIsAPureSignalOfAnEmptyBufferWhilePlaying() = pipelineTest { h ->
        h.pipeline.apply(StreamAction.StartFresh(flac))
        runCurrent()
        assertEquals(AudioPhase.Buffering, h.pipeline.status.value.phase)
        assertFalse(h.pipeline.status.value.starved, "nothing played yet: buffering, not starved")
        h.feed(0)
        runCurrent()
        assertTrue(h.pipeline.status.value.starved, "played and now empty")
        h.feed(10, 20)
        runCurrent()
        assertTrue(h.pipeline.status.value.starved)
        h.pipeline.apply(StreamAction.End)
        runCurrent()
        assertFalse(h.pipeline.status.value.starved)
    }

    @Test
    fun unsupportedCodecIsIgnoredWithAWarning() = pipelineTest { h ->
        h.pipeline.apply(StreamAction.StartFresh(StreamStartPlayer("opus", 48_000, 2, 16)))
        runCurrent()
        assertEquals(AudioEvent.UnsupportedFormat("opus"), h.events.tryReceive().getOrNull())
        assertEquals(StreamPhase.Idle, h.pipeline.phase)
        h.feed(0)
        assertTrue(h.pipeline.buffer.isEmpty)
        assertTrue(h.sink.handles.isEmpty())
    }

    @Test
    fun repeatedDecodeFailuresEndTheStream() = pipelineTest { h ->
        h.pipeline.apply(StreamAction.StartFresh(flac))
        runCurrent()
        h.decoders.failing = true
        repeat(5) { h.feed(it * 10L) }
        runCurrent()
        assertEquals(AudioEvent.DecoderFailed("flac"), h.events.tryReceive().getOrNull())
        assertEquals(StreamPhase.Ended, h.pipeline.phase)
    }

    @Test
    fun sinkFocusLossEndsTheStream() = pipelineTest { h ->
        h.pipeline.apply(StreamAction.StartFresh(flac))
        runCurrent()
        h.feed(0)
        runCurrent()
        assertEquals(AudioEvent.Started, h.events.tryReceive().getOrNull())
        h.handle.emit(SinkEvent.FocusLost)
        runCurrent()
        assertEquals(AudioEvent.FocusLost, h.events.tryReceive().getOrNull())
        assertEquals(StreamPhase.Ended, h.pipeline.phase)
    }

    @Test
    fun cancellingRunClosesSinkAndDecoder() = pipelineTest { h ->
        h.pipeline.apply(StreamAction.StartFresh(flac))
        runCurrent()
        h.job.cancel()
        runCurrent()
        assertTrue(h.handle.closed)
        assertTrue(h.decoders.created.single().released)
    }

    @Test
    fun codecHeaderIsDecodedFromBase64() = pipelineTest { h ->
        h.pipeline.apply(StreamAction.StartFresh(flac.copy(codecHeader = "AAEC")))
        runCurrent()
        val decoder = h.decoders.created.single()
        assertEquals(AudioCodec.FLAC, decoder.configured?.codec)
        assertIs<ByteArray>(decoder.header)
        assertEquals(listOf<Byte>(0, 1, 2), decoder.header!!.toList())
    }
}
