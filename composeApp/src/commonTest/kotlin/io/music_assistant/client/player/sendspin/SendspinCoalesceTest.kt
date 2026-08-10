package io.music_assistant.client.player.sendspin

import io.music_assistant.client.player.sendspin.audio.AudioPipeline
import io.music_assistant.client.player.sendspin.model.StreamStartMessage
import io.music_assistant.client.player.sendspin.model.StreamStartPayload
import io.music_assistant.client.player.sendspin.model.StreamStartPlayer
import io.music_assistant.client.player.sendspin.protocol.StreamLifecycleEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Guards the coalescing contract at the [AudioPipeline] seam: when stream/start events arrive
 * faster than [AudioPipeline.startStream] can complete, only the LATEST track is fully set up —
 * the intermediate (skipped-past) setups are cancelled before finishing. This mirrors the
 * `collectLatest { handleStreamLifecycle(it) }` collector in [SendspinClient]; a regression that
 * swapped `collectLatest` for `collect`, or made `startStream` non-cancellable, would fail here.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SendspinCoalesceTest {
    /** Records which streams started, completed, and were cancelled; each start blocks on a gate. */
    private class GatedPipeline : AudioPipeline {
        val started = mutableListOf<String>()
        val completed = mutableListOf<String>()
        val cancelled = mutableListOf<String>()
        val gates = mutableMapOf<String, CompletableDeferred<Unit>>()
        var stopStreamCalls = 0

        private fun gate(header: String) = gates.getOrPut(header) { CompletableDeferred() }

        override suspend fun startStream(config: StreamStartPlayer) {
            val header = config.codecHeader!!
            started += header
            try {
                gate(header).await()
                completed += header
            } catch (e: CancellationException) {
                cancelled += header
                throw e
            }
        }

        override suspend fun stopStream() {
            stopStreamCalls++
        }

        override suspend fun clearStream() = Unit
        override suspend fun processBinaryMessage(data: ByteArray) = Unit
        override fun close() = Unit
        override val bufferState = MutableStateFlow(BufferState(0L, false, 0))
        override val playbackPosition = MutableStateFlow(0L)
        override val streamError: Flow<Throwable> = MutableSharedFlow()
        override val isStarved: StateFlow<Boolean> = MutableStateFlow(false)
    }

    private fun startEvent(header: String) = StreamLifecycleEvent.Start(
        StreamStartMessage(
            payload = StreamStartPayload(
                player = StreamStartPlayer(
                    codec = "flac",
                    sampleRate = 44100,
                    channels = 2,
                    bitDepth = 16,
                    codecHeader = header,
                ),
            ),
        ),
    )

    // Mirrors SendspinClient.handleStreamLifecycle's dispatch (without the state-machine side
    // effects, which aren't relevant to coalescing).
    private suspend fun dispatch(pipeline: AudioPipeline, event: StreamLifecycleEvent) {
        when (event) {
            is StreamLifecycleEvent.Start -> event.message.payload.player?.let { pipeline.startStream(it) }
            StreamLifecycleEvent.End -> pipeline.stopStream()
            StreamLifecycleEvent.Clear -> pipeline.clearStream()
        }
    }

    @Test
    fun onlyLatestStreamMaterializesUnderRapidBurst() = runTest {
        val pipeline = GatedPipeline()
        val events = MutableSharedFlow<StreamLifecycleEvent>(extraBufferCapacity = 64)

        val collector = launch(UnconfinedTestDispatcher(testScheduler)) {
            events.collectLatest { dispatch(pipeline, it) }
        }

        // Three rapid skips, none released yet — each new one supersedes the last.
        events.emit(startEvent("a"))
        events.emit(startEvent("b"))
        events.emit(startEvent("c"))

        // Land on "c": only it should run to completion.
        pipeline.gates.getValue("c").complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf("a", "b", "c"), pipeline.started, "each event should begin setup")
        assertEquals(listOf("c"), pipeline.completed, "only the final track completes")
        assertTrue("a" in pipeline.cancelled && "b" in pipeline.cancelled, "stale setups are cancelled")

        collector.cancel()
    }

    @Test
    fun terminalEndIsHonored() = runTest {
        val pipeline = GatedPipeline()
        val events = MutableSharedFlow<StreamLifecycleEvent>(extraBufferCapacity = 64)

        val collector = launch(UnconfinedTestDispatcher(testScheduler)) {
            events.collectLatest { dispatch(pipeline, it) }
        }

        // Start "a" then immediately stop — the End must fully run even though it superseded a Start.
        events.emit(startEvent("a"))
        events.emit(StreamLifecycleEvent.End)
        advanceUntilIdle()

        assertTrue("a" in pipeline.cancelled, "the superseded start is cancelled")
        assertEquals(1, pipeline.stopStreamCalls, "the terminal End's stopStream runs")

        collector.cancel()
    }
}
