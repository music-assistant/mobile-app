package io.music_assistant.sendspin.audio

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import io.music_assistant.sendspin.api.AudioCodec
import io.music_assistant.sendspin.api.AudioPhase
import io.music_assistant.sendspin.api.MonotonicClock
import io.music_assistant.sendspin.api.SinkEvent
import io.music_assistant.sendspin.clock.ClockSync
import io.music_assistant.sendspin.fakes.FakeDecoderFactory
import io.music_assistant.sendspin.fakes.FakeSink
import io.music_assistant.sendspin.wire.AudioChunk
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
import kotlin.test.assertContentEquals
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
    private val ramp8k = StreamStartPlayer("flac", 8_000, 2, 16)
    private val bytesPerFrame = 4

    private inner class Harness(val scope: TestScope, synced: Boolean = true) {
        val clock = MonotonicClock { scope.currentTime * 1_000 }
        val sink = FakeSink { clock.nowMicros() }
        val decoders = FakeDecoderFactory()
        val clockSync = ClockSync(clock).apply { if (synced) sync() }

        /** One 2 us RTT probe answered "now": server clock == local clock. The filter discards a zero RTT. */
        fun ClockSync.sync() {
            val now = clock.nowMicros()
            onReply(ServerTimePayload(now, now + 1, now - 1))
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

        fun drainEvents(): List<AudioEvent> = generateSequence { events.tryReceive().getOrNull() }.toList()
    }

    private fun pipelineTest(synced: Boolean = true, block: suspend TestScope.(Harness) -> Unit) = runTest {
        val h = Harness(this, synced)
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
    fun theStreamStatsCountTheEvictionsOfTheirOwnStream() = pipelineTest { h ->
        val lines = mutableListOf<String>()
        val previous = Logger.config.logWriterList
        Logger.setLogWriters(
            object : LogWriter() {
                override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
                    lines += message
                }
            },
        )
        try {
            h.pipeline.capacityBytes = 2 * h.chunk(0).length
            h.pipeline.apply(StreamAction.StartFresh(flac))
            runCurrent()
            h.feed(1_000, 1_010, 1_020) // future audio: the third chunk is over the cap
            runCurrent()

            h.pipeline.apply(StreamAction.StartFresh(flac))
            runCurrent()
            assertEquals(listOf("evictions=1"), lines.mapNotNull { Regex("evictions=\\d+").find(it)?.value })

            h.pipeline.apply(StreamAction.StartFresh(flac))
            runCurrent()
            assertEquals(1, lines.count { "Stream stats" in it }, "an earlier stream's evictions are not counted again")
        } finally {
            Logger.setLogWriters(previous)
        }
    }

    @Test
    fun holdSilencesAtOnceDiscardsAudioAndReleasePlaysTheNextAudio() = pipelineTest { h ->
        h.pipeline.apply(StreamAction.StartFresh(flac))
        runCurrent()
        h.feed(0, 1_000, 2_000)
        runCurrent()
        assertEquals(AudioEvent.Started, h.events.tryReceive().getOrNull())
        val writes = h.handle.writes.size

        h.pipeline.hold(true)
        runCurrent()
        assertTrue(h.handle.paused, "the sink stops without a server message")
        assertEquals(1, h.handle.flushes)
        assertTrue(h.pipeline.buffer.isEmpty, "the stopped timeline is discarded")
        assertEquals(AudioPhase.Idle, h.pipeline.status.value.phase)
        h.feed(3_000)
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(writes, h.handle.writes.size, "audio that arrives while held is never played")
        assertTrue(h.pipeline.buffer.isEmpty)

        h.pipeline.hold(false)
        runCurrent()
        assertFalse(h.handle.paused)
        h.feed(currentTime + 100)
        advanceTimeBy(200)
        runCurrent()
        assertTrue(h.handle.writes.size > writes)
        assertEquals(AudioEvent.Started, h.events.tryReceive().getOrNull(), "a release is a new start for the app")
        assertTrue(h.events.isEmpty, "a hold reports no stop: the app asked for it")
    }

    @Test
    fun aStreamStartWhileHeldOpensNothingUntilRelease() = pipelineTest { h ->
        h.pipeline.hold(true)
        h.pipeline.apply(StreamAction.StartFresh(flac)) // a reconnect while the local pause is in flight
        runCurrent()
        h.feed(0, 10)
        runCurrent()
        assertTrue(h.sink.handles.isEmpty(), "no sink while held")
        assertEquals(StreamPhase.Playing, h.pipeline.phase, "the lifecycle still follows the server")

        h.pipeline.hold(false)
        runCurrent()
        assertEquals(1, h.sink.handles.size)
        h.feed(0)
        runCurrent()
        assertEquals(1, h.handle.writes.size)
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

    /**
     * Feeds 10 ms chunks of an 8 kHz rising ramp, 4 per frame, and returns the bytes fed: an interpolated
     * frame then lands between two input values. Time stands still and every write is queued, so each
     * chunk's lead is minus the sink latency set for it.
     */
    private fun TestScope.feedRamp(h: Harness, leadsMillis: List<Int>): ByteArray =
        leadsMillis.mapIndexed { n, leadMillis ->
            h.sink.latencyMicros = -leadMillis * 1_000L
            val bytes = ByteArray(80 * bytesPerFrame)
            for (i in 0 until 80) {
                val v = (n * 80 + i) * 4 // positive: the corrector truncates toward zero, a floor only here
                for (ch in 0..1) {
                    bytes[i * bytesPerFrame + ch * 2] = v.toByte()
                    bytes[i * bytesPerFrame + ch * 2 + 1] = (v shr 8).toByte()
                }
            }
            h.pipeline.onAudio(AudioChunk(n * 10_000L, bytes, 0))
            runCurrent()
            bytes
        }.reduce(ByteArray::plus)

    @Test
    fun resamplingThatStartsAndStopsKeepsTheSignalContinuous() = pipelineTest { h ->
        h.pipeline.apply(StreamAction.StartFresh(ramp8k))
        runCurrent()
        // Sustained leads move the smoothed lead out of the 2 ms band, back through it, out to the
        // other side, and in and out once more.
        val leads = List(1) { 0 } + List(20) { 35 } + List(40) { -35 } + List(5) { 0 } + List(30) { 35 }
        val input = feedRamp(h, leads)
        val pcm = h.handle.writes.reduce(ByteArray::plus)
        assertFalse(pcm.contentEquals(input), "the corrector never ran")
        val left = (0 until pcm.size / bytesPerFrame).map { f ->
            (pcm[f * bytesPerFrame].toInt() and 0xFF) or (pcm[f * bytesPerFrame + 1].toInt() shl 8)
        }
        // A resample at most 0.2% off keeps every step in 3..5; a lost or a stale frame does not.
        left.zipWithNext { a, b -> b - a }.forEachIndexed { f, step ->
            assertTrue(step in 3..5, "frame ${f + 1}: step $step (a lost or a stale frame)")
        }
    }

    @Test
    fun aLeadThatJittersAroundZeroIsNotCorrected() = pipelineTest { h ->
        h.pipeline.apply(StreamAction.StartFresh(ramp8k))
        runCurrent()
        // One reading is off by 10 ms either way (a coarse playback head); the average stays at zero.
        val leads = listOf(0) + List(40) { if (it % 2 == 0) 10 else -10 }
        val input = feedRamp(h, leads)
        assertContentEquals(input, h.handle.writes.reduce(ByteArray::plus))
    }

    @Test
    fun withoutPositionFeedbackTheLoopIsOpenWaitUntilDueAndWriteLate() = pipelineTest { h ->
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
        // The next chunk in sequence arrives 250 ms after it was due (an audio-thread stall).
        advanceTimeBy(250)
        h.feed(60)
        runCurrent()
        // The native queue still plays it in sequence; the local player is never grouped.
        assertEquals(2, h.handle.writes.size, "late chunk written, not dropped")
        assertTrue(h.handle.writes[1].none { it == 0.toByte() }, "late chunk written whole")
    }

    @Test
    fun aChunkThatWaitsForItsDueTimeIsDecodedOnce() = pipelineTest { h ->
        // Stateful decoders (Opus, MediaCodec) corrupt audio when a retried chunk is decoded again.
        h.sink.reportPosition = false
        h.pipeline.apply(StreamAction.StartFresh(flac))
        runCurrent()
        h.feed(500)
        runCurrent()
        // Wake the scheduler repeatedly while the chunk is still not due.
        repeat(5) {
            h.feed(600 + it * 10L)
            advanceTimeBy(20)
            runCurrent()
        }
        assertEquals(0, h.handle.writes.size, "nothing due yet")
        assertEquals(0, h.decoders.created.single().decodes, "no decode before the chunk is consumed")
        advanceTimeBy(700)
        runCurrent()
        assertEquals(6, h.handle.writes.size)
        assertEquals(6, h.decoders.created.single().decodes, "one decode per chunk")
    }

    @Test
    fun aChunkThatWaitsForTheClockIsDecodedOnce() = pipelineTest(synced = false) { h ->
        h.pipeline.apply(StreamAction.StartFresh(flac))
        runCurrent()
        h.feed(500) // due exactly when the clock estimate arrives
        advanceTimeBy(500)
        runCurrent()
        assertEquals(0, h.handle.writes.size, "held until the first clock estimate")
        assertEquals(0, h.decoders.created.single().decodes)
        with(h) { clockSync.sync() }
        h.pipeline.wakeups.trySend(Unit)
        runCurrent()
        assertEquals(1, h.handle.writes.size)
        assertEquals(1, h.decoders.created.single().decodes)
    }

    @Test
    fun audioAfterClearReportsAFreshStart() = pipelineTest { h ->
        // A local seek clears and continues without stream/start; the app waits for Started to unfreeze.
        h.pipeline.apply(StreamAction.StartFresh(flac))
        runCurrent()
        h.feed(0)
        runCurrent()
        assertEquals(AudioEvent.Started, h.events.tryReceive().getOrNull())
        h.pipeline.apply(StreamAction.Clear)
        runCurrent()
        assertEquals(AudioEvent.Cleared, h.events.tryReceive().getOrNull())
        assertEquals(AudioPhase.Buffering, h.pipeline.status.value.phase)
        h.feed(10)
        runCurrent()
        assertEquals(AudioEvent.Started, h.events.tryReceive().getOrNull(), "a second Started after clear")
        assertEquals(1, h.sink.handles.size, "clear keeps the sink")
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
    fun aSameFormatStartWhilePlayingKeepsTheSinkAndTheDecoder() = pipelineTest { h ->
        // MA sends stream/clear plus a same-format stream/start on every track change.
        h.pipeline.apply(StreamAction.StartFresh(flac))
        runCurrent()
        h.feed(0)
        runCurrent()
        assertEquals(AudioEvent.Started, h.events.tryReceive().getOrNull())
        val action = StreamLifecycle.onStart(h.pipeline.phase, h.pipeline.stream.value.format, flac, false)
        assertEquals(StreamAction.Restart, action)
        h.pipeline.apply(action)
        runCurrent()
        assertEquals(1, h.sink.handles.size, "no device rebuild on a skip")
        assertEquals(1, h.decoders.created.size)
        assertEquals(1, h.decoders.created.single().resets, "the codec drops the discarded timeline")
        assertEquals(1, h.handle.flushes)
        assertTrue(h.pipeline.buffer.isEmpty)
        h.feed(0) // the de-dup memory is reset with the timeline
        runCurrent()
        assertEquals(AudioEvent.Started, h.events.tryReceive().getOrNull(), "a fresh start for the app")
        assertEquals(2, h.handle.writes.size)
    }

    @Test
    fun aFormatChangeWhilePlayingStillRebuilds() = pipelineTest { h ->
        h.pipeline.apply(StreamAction.StartFresh(flac))
        runCurrent()
        val opus = StreamStartPlayer("pcm", 44_100, 2, 16)
        val action = StreamLifecycle.onStart(h.pipeline.phase, h.pipeline.stream.value.format, opus, false)
        assertEquals(StreamAction.StartFresh(opus), action)
        h.pipeline.apply(action)
        runCurrent()
        assertEquals(2, h.sink.handles.size)
        assertEquals(2, h.decoders.created.size)
    }

    @Test
    fun aFocusLossStopsAWriterThatTheDeviceNeverBlocks() = pipelineTest { h ->
        // A muted track keeps accepting audio, so the writer never suspends on its own; the
        // sink's event runs on the same audio thread and must still get a turn.
        h.pipeline.apply(StreamAction.StartFresh(flac))
        runCurrent()
        h.handle.onWrite = { if (h.handle.writes.isEmpty()) h.handle.emit(SinkEvent.FocusLost) }
        h.feed(*LongArray(50) { it * 10L })
        runCurrent()
        assertEquals(listOf<AudioEvent>(AudioEvent.Started, AudioEvent.FocusLost), h.drainEvents())
        assertTrue(h.handle.writes.size <= 2, "wrote ${h.handle.writes.size} chunks after the loss")
    }

    @Test
    fun aRepeatedFocusLossReportsOnce() = pipelineTest { h ->
        // A call can arrive as a mode change and a focus loss; the app must see one stop.
        h.pipeline.apply(StreamAction.StartFresh(flac))
        runCurrent()
        h.handle.emit(SinkEvent.FocusLost)
        h.handle.emit(SinkEvent.FocusLost)
        runCurrent()
        assertEquals(listOf<AudioEvent>(AudioEvent.FocusLost), h.drainEvents())
        assertEquals(StreamPhase.Ended, h.pipeline.phase)
    }

    @Test
    fun aSinkDeathAfterAFocusLossKeepsTheFocusLoss() = pipelineTest { h ->
        h.pipeline.apply(StreamAction.StartFresh(flac))
        runCurrent()
        h.handle.emit(SinkEvent.FocusLost)
        h.handle.emit(SinkEvent.Died)
        runCurrent()
        assertEquals(listOf<AudioEvent>(AudioEvent.FocusLost), h.drainEvents())
    }

    @Test
    fun aRegainReachesTheAppAfterAStreamStartWhileHeld() = pipelineTest { h ->
        // The app holds on the loss; a stream/start in that window moves the generation
        // but keeps the interrupted handle, and its regain must still reach the app.
        h.pipeline.apply(StreamAction.StartFresh(flac))
        runCurrent()
        val interrupted = h.handle
        interrupted.emit(SinkEvent.FocusLost)
        runCurrent()
        h.pipeline.hold(true)
        h.pipeline.apply(StreamAction.StartFresh(flac))
        runCurrent()
        interrupted.emit(SinkEvent.FocusRegained)
        runCurrent()
        assertEquals(listOf<AudioEvent>(AudioEvent.FocusLost, AudioEvent.FocusRegained), h.drainEvents())
    }

    @Test
    fun aDeadWriteReportsSinkDied() = pipelineTest { h ->
        h.pipeline.apply(StreamAction.StartFresh(flac))
        runCurrent()
        h.handle.onWrite = { h.handle.dead = true }
        h.feed(0)
        runCurrent()
        assertEquals(AudioEvent.SinkDied, h.events.tryReceive().getOrNull())
        assertEquals(StreamPhase.Ended, h.pipeline.phase)
    }

    @Test
    fun aSinkThatAcceptsNothingIsTreatedAsDead() = pipelineTest { h ->
        // The audio thread must not retry a sink that takes nothing: that is an endless spin.
        h.pipeline.apply(StreamAction.StartFresh(flac))
        runCurrent()
        h.handle.acceptNothing = true
        h.feed(0)
        runCurrent()
        advanceTimeBy(60)
        runCurrent()
        assertEquals(AudioEvent.SinkDied, h.events.tryReceive().getOrNull())
        assertEquals(StreamPhase.Ended, h.pipeline.phase)
    }

    @Test
    fun replacingTheSinkDropsTheOldHandlesEventCollector() = pipelineTest { h ->
        repeat(3) {
            h.pipeline.apply(StreamAction.StartFresh(flac))
            runCurrent()
        }
        assertEquals(3, h.sink.handles.size)
        assertEquals(listOf(0, 0, 1), h.sink.handles.map { it.subscribers })
    }

    @Test
    fun aFocusLossPendingFromTheOldSinkDoesNotEndTheReplacementStream() = pipelineTest { h ->
        h.pipeline.apply(StreamAction.StartFresh(flac))
        runCurrent()
        val old = h.handle
        old.emit(SinkEvent.FocusLost) // buffered, not yet delivered
        h.pipeline.apply(StreamAction.StartFresh(flac))
        runCurrent()
        assertEquals(StreamPhase.Playing, h.pipeline.phase)
        val seen = generateSequence { h.events.tryReceive().getOrNull() }.toList()
        assertEquals(emptyList(), seen, "the stale event is dropped")
        h.feed(0)
        runCurrent()
        assertEquals(1, h.handle.writes.size)
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
