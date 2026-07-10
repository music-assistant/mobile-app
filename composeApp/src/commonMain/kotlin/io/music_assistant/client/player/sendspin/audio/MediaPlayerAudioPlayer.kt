package io.music_assistant.client.player.sendspin.audio

import co.touchlab.kermit.Logger
import com.sendspin.protocol.AudioBuffer
import com.sendspin.protocol.AudioPlayer
import com.sendspin.protocol.ClockSync
import com.sendspin.protocol.StreamFormat
import io.music_assistant.client.player.MediaPlayerController
import io.music_assistant.client.player.MediaPlayerListener
import io.music_assistant.client.player.sendspin.model.AudioCodec
import io.music_assistant.client.player.sendspin.model.AudioFormatSpec
import io.music_assistant.client.utils.audioDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Host implementation of the library's [AudioPlayer] SPI. The library owns buffering/scheduling
 * ([AudioBuffer] does reorder, dedup, late-drop and clock-scheduled admission); this class owns the
 * consumer half — pulling ready chunks, decoding (FLAC/Opus/PCM via the app's [AudioDecoder]s), and
 * writing PCM to the platform [MediaPlayerController]. It is the KMP-native replacement for the
 * consumer side of the former AudioStreamManager.
 *
 * All SPI methods and the consumer loop run on the single-threaded [audioDispatcher], so decoder and
 * sink access is serialised without explicit locks (the library serialises its `AudioPlayer` calls on
 * the same dispatcher, injected as its audio context).
 */
class MediaPlayerAudioPlayer(
    @Suppress("UNUSED_PARAMETER") clock: ClockSync,
    private val buffer: AudioBuffer,
    private val sink: MediaPlayerController,
) : AudioPlayer {
    private val logger = Logger.withTag("MediaPlayerAudioPlayer")
    private val scope = CoroutineScope(audioDispatcher + SupervisorJob())

    private var decoder: AudioDecoder? = null
    private var playbackJob: Job? = null

    @Volatile private var playing = false
    @Volatile private var droppedFrames = 0L

    private val _isStarved = MutableStateFlow(false)
    /** True while the consumer has drained the buffer; consumed by the app's SendspinClient adapter. */
    val isStarved: StateFlow<Boolean> = _isStarved.asStateFlow()

    private val _streamError = MutableSharedFlow<Throwable>(replay = 0, extraBufferCapacity = 1)
    /** Sink errors (e.g. audio output disconnected); consumed by the app's SendspinClient adapter. */
    val streamError: Flow<Throwable> = _streamError.asSharedFlow()

    override val isPlaying: Boolean get() = playing
    override val droppedDecodeFrames: Long get() = droppedFrames

    // Tracks the current AudioTrack format to enable reuse across track transitions/reconnects.
    private data class SinkConfig(
        val outputCodec: AudioCodec,
        val sampleRate: Int,
        val channels: Int,
        val bitDepth: Int,
    )

    private var currentSinkConfig: SinkConfig? = null

    override fun configure(format: StreamFormat) = setupSink(format, reuseAllowed = false)

    override fun transition(format: StreamFormat) = setupSink(format, reuseAllowed = true)

    private fun setupSink(format: StreamFormat, reuseAllowed: Boolean) {
        logger.i { "Configuring stream: ${format.codec}, ${format.sampleRate}Hz, ${format.channels}ch, ${format.bitDepth}bit" }
        val newDecoder = createDecoder(format)
        newDecoder.configure(
            AudioFormatSpec(
                codec = AudioCodec.valueOf(format.codec.uppercase()),
                channels = format.channels,
                sampleRate = format.sampleRate,
                bitDepth = format.bitDepth,
            ),
            format.codecHeader,
        )
        decoder?.release()
        decoder = newDecoder

        val outputCodec = newDecoder.getOutputCodec()
        val outputBitDepth = newDecoder.getOutputBitDepth()
        val newSinkConfig = SinkConfig(outputCodec, format.sampleRate, format.channels, outputBitDepth)

        if (reuseAllowed && newSinkConfig == currentSinkConfig) {
            // Same format on a track handoff — keep the AudioTrack to avoid a click, just flush.
            logger.i { "Reusing AudioTrack (same format: $newSinkConfig)" }
            sink.flush()
            sink.resumeSink()
        } else {
            logger.i { "Creating AudioTrack: $newSinkConfig" }
            sink.prepareStream(
                codec = outputCodec,
                sampleRate = format.sampleRate,
                channels = format.channels,
                bitDepth = outputBitDepth,
                codecHeader = format.codecHeader,
                listener = object : MediaPlayerListener {
                    override fun onReady() { logger.i { "MediaPlayer ready ($outputCodec)" } }
                    override fun onAudioCompleted() { logger.i { "Audio completed" } }
                    override fun onError(error: Throwable?) {
                        logger.e(error) { "MediaPlayer error — stopping" }
                        _streamError.tryEmit(error ?: Exception("Unknown MediaPlayer error"))
                        stop()
                    }
                },
            )
            currentSinkConfig = newSinkConfig
        }
    }

    private fun createDecoder(format: StreamFormat): AudioDecoder {
        val codec = codecByName(format.codec.uppercase())
        return codec?.decoderInitializer?.invoke() ?: PcmDecoder()
    }

    override fun start() {
        playbackJob?.cancel()
        playing = true
        _isStarved.value = false
        playbackJob = scope.launch {
            logger.i { "Playback consumer started" }
            try {
                while (isActive) {
                    val chunk = buffer.poll()
                    if (chunk == null) {
                        val waitUs = buffer.nextChunkDelayMicros()
                        if (waitUs == null) {
                            // Buffer empty — publish starvation and poll again shortly. The library
                            // refills via offer(); a short interval bounds resume latency to ~ms.
                            buffer.signalUnderrun()
                            _isStarved.value = true
                            delay(STARVED_POLL_MS)
                        } else {
                            _isStarved.value = false
                            delay((waitUs / 1000).coerceAtLeast(1L))
                        }
                        continue
                    }
                    _isStarved.value = false
                    val active = decoder ?: continue
                    val pcm = try {
                        active.decode(chunk.data)
                    } catch (e: Exception) {
                        droppedFrames++
                        logger.w(e) { "Decode failed — dropping frame" }
                        continue
                    }
                    // Blocks on the hardware ring buffer, which self-paces subsequent chunks.
                    sink.writeRawPcm(pcm)
                }
            } catch (_: CancellationException) {
                // normal shutdown
            } catch (e: Exception) {
                logger.e(e) { "Consumer error: ${e.message}" }
                _streamError.tryEmit(e)
            }
            logger.i { "Playback consumer stopped" }
        }
    }

    override fun flush() {
        buffer.flush()
        // pause+flush+resume drains the HW ring buffer so dropped audio doesn't keep playing.
        sink.pauseSink()
        sink.flush()
        sink.resumeSink()
    }

    override fun stop() {
        playing = false
        _isStarved.value = false
        playbackJob?.cancel()
        playbackJob = null
        decoder?.reset()
        // Pause+flush (don't release) to keep HW warm for deterministic re-start latency.
        sink.pauseSink()
        sink.flush()
    }

    override fun setVolume(gain: Float) {
        // The library folds mute into gain=0. Preserve the app's separate mute + system-volume path:
        // recover the 0-100 value from the perceptual curve gain = (vol/100)^1.5.
        if (gain <= 0f) {
            sink.setMuted(true)
        } else {
            sink.setMuted(false)
            val volume = (gain.toDouble().pow(2.0 / 3.0) * 100).roundToInt().coerceIn(0, 100)
            sink.setVolume(volume)
        }
    }

    /** Full teardown — releases the decoder, the sink, and the consumer scope. */
    fun close() {
        playbackJob?.cancel()
        sink.stopRawPcmStream()
        currentSinkConfig = null
        decoder?.release()
        decoder = null
        scope.cancel()
    }

    private companion object {
        const val STARVED_POLL_MS = 15L
    }
}
