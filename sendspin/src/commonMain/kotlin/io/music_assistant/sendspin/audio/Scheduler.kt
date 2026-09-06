package io.music_assistant.sendspin.audio

import co.touchlab.kermit.Logger
import io.music_assistant.sendspin.api.AudioPhase
import io.music_assistant.sendspin.api.AudioSink
import io.music_assistant.sendspin.api.AudioStatus
import io.music_assistant.sendspin.api.MonotonicClock
import io.music_assistant.sendspin.api.SinkEvent
import io.music_assistant.sendspin.api.SinkFormat
import io.music_assistant.sendspin.api.SinkHandle
import io.music_assistant.sendspin.clock.ClockSync
import io.music_assistant.sendspin.wire.AudioChunk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

/**
 * The audio thread: decodes chunks and feeds the sink so that each chunk plays
 * at `serverTime -> localTime + userDelay`.
 *
 * With sink position feedback, the chunk's expected play time is `now + audio
 * queued in the sink + output latency`. The difference to its target is
 * corrected by inserting silence (early), trimming or dropping (late), or a
 * gentle resample inside the tolerance band. The sink's own buffer is the
 * write-ahead cushion: writes block at the hardware rate, which paces the loop.
 * Without feedback (iOS), scheduling is open loop: wait until due, drop when late.
 *
 * Only this coroutine touches the sink handle and the decoder.
 */
internal class Scheduler(
    private val pipeline: AudioPipeline,
    private val sink: AudioSink,
    private val decoder: DecoderStage,
    private val clockSync: ClockSync,
    private val clock: MonotonicClock,
) {
    private val logger = Logger.withTag("AudioScheduler")
    private val _status = MutableStateFlow(AudioStatus.IDLE)
    val status: StateFlow<AudioStatus> = _status

    private var handle: SinkHandle? = null
    private var format: SinkFormat? = null
    private var corrector: DriftCorrector? = null
    private var openGeneration = -1
    private var seenFlush = 0
    private var framesWritten = 0L
    private var played = false
    private var lateDrops = 0L
    private var insertedSilenceMicros = 0L
    private var clockWaitSinceMicros: Long? = null

    suspend fun run(): Nothing = coroutineScope {
        try {
            while (true) step(this)
            @Suppress("UNREACHABLE_CODE")
            error("unreachable")
        } finally {
            closeSink()
            decoder.close()
        }
    }

    private suspend fun step(scope: kotlinx.coroutines.CoroutineScope) {
        val state = pipeline.stream.value
        if (state.flushGeneration != seenFlush) {
            seenFlush = state.flushGeneration
            handle?.let {
                it.pause()
                it.flush()
                if (state.phase == StreamPhase.Playing) it.resume()
            }
            framesWritten = 0
            played = false // the next write is a new start: the app must be told again
            corrector?.reset()
        }
        when (state.phase) {
            StreamPhase.Idle, StreamPhase.Ended -> {
                publish(AudioPhase.Idle, starved = false)
                pipeline.wakeups.receive()
            }

            StreamPhase.Playing -> {
                if (state.generation != openGeneration) reopen(state, scope)
                if (handle == null) {
                    pipeline.wakeups.receive()
                    return
                }
                val chunk = pipeline.buffer.peek()
                val phase = if (played) AudioPhase.Playing else AudioPhase.Buffering
                if (chunk == null) {
                    publish(phase, starved = played)
                    pipeline.wakeups.receive()
                    return
                }
                // Audio is queued (even if not due yet): a stale starved flag must not survive.
                if (_status.value.starved) publish(phase, starved = false)
                if (play(chunk)) pipeline.buffer.poll()
            }
        }
    }

    private fun reopen(state: AudioPipeline.StreamState, scope: kotlinx.coroutines.CoroutineScope) {
        closeSink()
        val streamFormat = state.format ?: return
        val pcmFormat = try {
            decoder.open(streamFormat)
        } catch (e: Exception) {
            logger.e(e) { "Decoder open failed for ${streamFormat.codec}" }
            pipeline.onSinkFailure(AudioEvent.DecoderFailed(streamFormat.codec))
            return
        }
        val opened = try {
            sink.open(pcmFormat)
        } catch (e: Exception) {
            logger.e(e) { "Sink open failed" }
            decoder.close()
            pipeline.onSinkFailure(AudioEvent.SinkDied)
            return
        }
        handle = opened
        format = pcmFormat
        corrector = if (pcmFormat.isPcm) DriftCorrector(pcmFormat.channels, pcmFormat.bitDepth / BITS_PER_BYTE) else null
        openGeneration = state.generation
        framesWritten = 0
        played = false
        lateDrops = 0
        insertedSilenceMicros = 0
        scope.launch {
            opened.events.collect { event ->
                when (event) {
                    SinkEvent.FocusLost -> pipeline.onSinkFailure(AudioEvent.FocusLost)
                    SinkEvent.FocusRegained -> pipeline.emit(AudioEvent.FocusRegained)
                    SinkEvent.Died -> pipeline.onSinkFailure(AudioEvent.SinkDied)
                    SinkEvent.RouteChanged -> logger.i { "Audio route changed" }
                }
                pipeline.wakeups.trySend(Unit)
            }
        }
        publish(AudioPhase.Buffering, starved = false)
    }

    /**
     * Returns true when [chunk] was consumed (played or dropped); false to retry later.
     *
     * Every "retry later" exit happens before the decode: decoders are stateful
     * (Opus advances per call, MediaCodec queues the frame), so a chunk is decoded
     * exactly once, on the iteration that consumes it.
     */
    private suspend fun play(chunk: AudioChunk): Boolean {
        val out = handle ?: return false
        val fmt = format ?: return false
        val target = clockSync.toLocalMicros(chunk.timestampMicros)?.plus(pipeline.userDelayMicros)
        if (target == null) {
            // No clock estimate yet (first probe burst pending): hold the chunk briefly
            // rather than dump the server's lead-in into the sink; give up after a bound.
            val since = clockWaitSinceMicros ?: clock.nowMicros().also { clockWaitSinceMicros = it }
            if (clock.nowMicros() - since < CLOCK_WAIT_MICROS) {
                waitOrWake(CLOCK_POLL_MICROS)
                return false
            }
            return decodeAndWrite(out, chunk)
        }
        clockWaitSinceMicros = null
        // Opaque (natively decoded) bytes cannot be trimmed, padded, or resampled: open loop only.
        val opaque = !fmt.isPcm
        val position = if (opaque) null else out.position()
        val latency = out.latencyMicros ?: 0L
        val queuedMicros = position?.let { queuedMicros(it, fmt) } ?: 0L
        val lead = target - (clock.nowMicros() + queuedMicros + latency)

        if (lead < -HARD_TOLERANCE_MICROS) {
            val late = -lead
            lateDrops++
            val pcm = decode(chunk) ?: return true
            val blockMicros = if (opaque) 0L else framesToMicros((pcm.size / fmt.bytesPerFrame).toLong(), fmt)
            if (late >= blockMicros) return true // whole chunk is in the past
            val skip = microsToBytes(late, fmt)
            return write(out, pcm, skip, pcm.size - skip)
        }
        if (position == null) {
            // Open loop: no feedback, so the wall clock is the only reference.
            if (lead > SOFT_TOLERANCE_MICROS) {
                waitOrWake(lead)
                return false
            }
            return decodeAndWrite(out, chunk)
        }
        if (lead > MAX_SILENCE_MICROS) {
            waitOrWake(lead - MAX_SILENCE_MICROS)
            return false
        }
        val pcm = decode(chunk) ?: return true
        return when {
            lead > HARD_TOLERANCE_MICROS -> {
                insertedSilenceMicros += lead
                write(out, ByteArray(microsToBytes(lead, fmt))) && write(out, pcm)
            }

            abs(lead) > SOFT_TOLERANCE_MICROS -> {
                val blockMicros = framesToMicros((pcm.size / fmt.bytesPerFrame).toLong(), fmt)
                write(out, corrector?.correct(pcm, driftMicros = -lead, blockMicros = blockMicros) ?: pcm)
            }

            else -> write(out, pcm)
        }
    }

    private fun decodeAndWrite(out: SinkHandle, chunk: AudioChunk): Boolean {
        val pcm = decode(chunk) ?: return true
        return write(out, pcm)
    }

    /** PCM for [chunk], or null when the chunk is dropped for a decode failure (the stream ends at the limit). */
    private fun decode(chunk: AudioChunk): ByteArray? {
        val pcm = decoder.decode(chunk)
        if (pcm == null && decoder.consecutiveFailures >= DECODER_FAILURE_LIMIT) {
            pipeline.onSinkFailure(AudioEvent.DecoderFailed(pipeline.stream.value.format?.codec ?: "?"))
        }
        return pcm
    }

    /** Waits up to [micros], but wakes early on any stream change or new chunk. */
    private suspend fun waitOrWake(micros: Long) {
        withTimeoutOrNull((micros / MICROS_PER_MILLI).coerceAtLeast(1)) { pipeline.wakeups.receive() }
    }

    private fun write(out: SinkHandle, pcm: ByteArray, offset: Int = 0, length: Int = pcm.size): Boolean {
        val fmt = format ?: return false
        var at = offset
        val end = offset + length
        while (at < end) {
            val written = out.write(pcm, at, end - at)
            if (written < 0) {
                pipeline.onSinkFailure(AudioEvent.SinkDied)
                return true
            }
            at += written
        }
        if (fmt.isPcm) framesWritten += length / fmt.bytesPerFrame
        if (!played && length > 0) {
            played = true
            pipeline.emit(AudioEvent.Started)
        }
        publish(AudioPhase.Playing, starved = false)
        return true
    }

    private fun queuedMicros(position: io.music_assistant.sendspin.api.SinkPosition, fmt: SinkFormat): Long {
        val playedByNow = position.framesPlayed + microsToFrames(clock.nowMicros() - position.atMicros, fmt)
        return framesToMicros((framesWritten - playedByNow).coerceAtLeast(0L), fmt)
    }

    private fun publish(phase: AudioPhase, starved: Boolean) {
        val bufferedMs = (pipeline.buffer.spanMicros / MICROS_PER_MILLI).toInt()
        _status.update { AudioStatus(phase, bufferedMs, starved) }
    }

    private fun closeSink() {
        try {
            handle?.close()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.w { "Sink close failed: ${e.message}" }
        }
        handle = null
        if (lateDrops > 0 || insertedSilenceMicros > 0) {
            logger.w { "Stream stats: lateDrops=$lateDrops insertedSilenceMs=${insertedSilenceMicros / MICROS_PER_MILLI}" }
        }
    }

    private fun framesToMicros(frames: Long, fmt: SinkFormat): Long = frames * MICROS_PER_SECOND / fmt.sampleRate

    private fun microsToFrames(micros: Long, fmt: SinkFormat): Long = micros * fmt.sampleRate / MICROS_PER_SECOND

    private fun microsToBytes(micros: Long, fmt: SinkFormat): Int = (microsToFrames(micros, fmt) * fmt.bytesPerFrame).toInt()

    private companion object {
        /** Below this, do nothing. */
        const val SOFT_TOLERANCE_MICROS = 2_000L

        /** Between soft and hard, resample; beyond, insert silence or drop. */
        const val HARD_TOLERANCE_MICROS = 40_000L

        /** Largest silence written in one go; larger leads wait instead. */
        const val MAX_SILENCE_MICROS = 200_000L

        const val DECODER_FAILURE_LIMIT = 5
        const val BITS_PER_BYTE = 8
        const val MICROS_PER_SECOND = 1_000_000L
        const val MICROS_PER_MILLI = 1_000L

        /** How long a chunk may wait for the first clock estimate. */
        const val CLOCK_WAIT_MICROS = 3_000_000L
        const val CLOCK_POLL_MICROS = 50_000L
    }
}
