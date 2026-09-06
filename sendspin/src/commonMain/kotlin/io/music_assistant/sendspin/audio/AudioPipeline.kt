package io.music_assistant.sendspin.audio

import co.touchlab.kermit.Logger
import io.music_assistant.sendspin.api.AudioSink
import io.music_assistant.sendspin.api.AudioStatus
import io.music_assistant.sendspin.api.DecoderFactory
import io.music_assistant.sendspin.api.MonotonicClock
import io.music_assistant.sendspin.clock.ClockSync
import io.music_assistant.sendspin.wire.AudioChunk
import io.music_assistant.sendspin.wire.StreamStartPlayer
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlin.concurrent.Volatile

/** What the pipeline reports upward; the player maps these to [io.music_assistant.sendspin.api.PlayerEvent]. */
internal sealed interface AudioEvent {
    data object Started : AudioEvent
    data object Ended : AudioEvent
    data object Cleared : AudioEvent
    data object SinkDied : AudioEvent
    data object FocusLost : AudioEvent
    data object FocusRegained : AudioEvent
    data class DecoderFailed(val codec: String) : AudioEvent
    data class UnsupportedFormat(val codec: String) : AudioEvent
}

/**
 * The audio pipeline's control surface, driven from the session reader, plus
 * the state the [Scheduler] consumes on the audio thread.
 *
 * Reader-side calls never block: they update [stream], the [buffer], and wake
 * the scheduler. Rebuilds and sink work happen on the audio thread when it
 * sees a new [StreamState.generation]. The pipeline outlives connections: only
 * the player's teardown cancels [run].
 */
internal class AudioPipeline(
    sink: AudioSink,
    decoders: DecoderFactory,
    clockSync: ClockSync,
    clock: MonotonicClock,
    capacityBytes: Int,
) {
    internal data class StreamState(
        val phase: StreamPhase,
        val format: StreamStartPlayer?,
        /** Bumped on every fresh start: the scheduler rebuilds decoder and sink. */
        val generation: Int,
        /** Bumped on every clear: the scheduler flushes the sink. */
        val flushGeneration: Int,
    )

    private val logger = Logger.withTag("AudioPipeline")
    internal val buffer = JitterBuffer(capacityBytes)
    internal val stream = MutableStateFlow(StreamState(StreamPhase.Idle, null, 0, 0))
    internal val wakeups = Channel<Unit>(Channel.CONFLATED)
    private val eventChannel = Channel<AudioEvent>(Channel.BUFFERED)
    private val decoderStage = DecoderStage(decoders)
    private val scheduler = Scheduler(this, sink, decoderStage, clockSync, clock)

    val events: Flow<AudioEvent> = eventChannel.receiveAsFlow()
    val status: StateFlow<AudioStatus> get() = scheduler.status

    val phase: StreamPhase get() = stream.value.phase

    @Volatile
    var userDelayMicros: Long = 0L

    var capacityBytes: Int
        get() = buffer.capacityBytes
        set(value) {
            buffer.capacityBytes = value
        }

    /** The audio loop. Run it on the audio dispatcher for the pipeline's whole life. */
    suspend fun run(): Nothing = scheduler.run()

    // --- Reader side ---

    fun onAudio(chunk: AudioChunk) {
        if (stream.value.phase != StreamPhase.Playing) return
        if (buffer.offer(chunk) != JitterBuffer.Offer.Stale) wakeups.trySend(Unit)
    }

    fun apply(action: StreamAction) {
        when (action) {
            is StreamAction.StartFresh -> startFresh(action.format)
            StreamAction.ResumeKeepBuffer -> wakeups.trySend(Unit)
            StreamAction.End, StreamAction.Abort -> {
                stream.update { it.copy(phase = StreamPhase.Ended, flushGeneration = it.flushGeneration + 1) }
                buffer.clear(resetDedup = false)
                if (action == StreamAction.End) emit(AudioEvent.Ended)
                wakeups.trySend(Unit)
            }

            StreamAction.Clear -> {
                buffer.clear(resetDedup = true)
                stream.update { it.copy(flushGeneration = it.flushGeneration + 1) }
                emit(AudioEvent.Cleared)
                wakeups.trySend(Unit)
            }

            StreamAction.Ignore -> Unit
        }
    }

    private fun startFresh(format: StreamStartPlayer) {
        if (!decoderStage.supports(format.codec)) {
            logger.w { "Ignoring stream with unsupported codec ${format.codec}" }
            emit(AudioEvent.UnsupportedFormat(format.codec))
            return
        }
        // Admission opens here, before the sink and decoder exist: the server
        // front-loads the stream at ~25x realtime and those chunks are future audio.
        buffer.clear(resetDedup = true)
        stream.update { it.copy(phase = StreamPhase.Playing, format = format, generation = it.generation + 1) }
        wakeups.trySend(Unit)
    }

    /** Sink-side failure reported by the scheduler: the stream is over. */
    internal fun onSinkFailure(event: AudioEvent) {
        stream.update { it.copy(phase = StreamPhase.Ended) }
        buffer.clear(resetDedup = false)
        emit(event)
    }

    internal fun emit(event: AudioEvent) {
        if (eventChannel.trySend(event).isFailure) logger.w { "Dropped audio event $event" }
    }
}
