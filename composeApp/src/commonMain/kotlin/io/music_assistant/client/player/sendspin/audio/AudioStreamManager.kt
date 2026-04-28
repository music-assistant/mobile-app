package io.music_assistant.client.player.sendspin.audio

import co.touchlab.kermit.Logger
import io.music_assistant.client.player.MediaPlayerController
import io.music_assistant.client.player.MediaPlayerListener
import io.music_assistant.client.player.sendspin.BufferState
import io.music_assistant.client.player.sendspin.ClockSynchronizer
import io.music_assistant.client.player.sendspin.SyncQuality
import io.music_assistant.client.player.sendspin.model.AudioCodec
import io.music_assistant.client.player.sendspin.model.AudioFormatSpec
import io.music_assistant.client.player.sendspin.model.BinaryMessage
import io.music_assistant.client.player.sendspin.model.BinaryMessageType
import io.music_assistant.client.player.sendspin.model.StreamStartPlayer
import io.music_assistant.client.utils.audioDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.Volatile
import kotlin.coroutines.CoroutineContext

/**
 * Manages the complete audio playback pipeline for Sendspin streaming.
 *
 * ## Architecture: Producer-Consumer with Reorder Buffer
 *
 * Audio chunks arrive via WebRTC/WebSocket with server-assigned timestamps.
 * Out-of-order delivery (common over WebRTC SCTP) would corrupt stateful codecs
 * like Opus. A sorted reorder buffer absorbs OOO packets before decoding.
 *
 * **Producer** (caller's coroutine via [processBinaryMessage]):
 * - Parses binary message header
 * - Sorted-inserts raw encoded frame into shared queue by server timestamp
 *
 * **Consumer** (dedicated high-priority [audioDispatcher] thread):
 * - Takes oldest frame once queue depth exceeds [reorderDepth]
 * - Decodes (Opus/FLAC → PCM) under [decoderLock]
 * - **Wall-clock gate**: waits until `serverTimeToLocal(frame.timestamp) - userDelayMicros`
 *   before writing, compensating for downstream pipeline lag (AudioTrack buffer,
 *   DAC, speakers). Drops chunks that are >100 ms late (per Sendspin spec).
 * - Writes PCM to [MediaPlayerController] — AudioTrack.write() then blocks on
 *   the hardware ring buffer, which keeps subsequent chunks self-paced.
 *
 * [userDelayMicros] is driven by [SendspinClientFactory] from the user's
 * playback-delay setting. Positive → play earlier in server time (compensates
 * for pipeline lag — the normal case, since downstream always adds delay).
 * Negative → play later (escape hatch if this device somehow leads).
 *
 * @see AudioPipeline for public interface
 * @see ClockSynchronizer for time synchronization
 */
class AudioStreamManager(
    private val clockSynchronizer: ClockSynchronizer,
    private val mediaPlayerController: MediaPlayerController,
) : AudioPipeline, CoroutineScope {
    private val logger = Logger.withTag("AudioStreamManager")
    private val supervisorJob = SupervisorJob()

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Default + supervisorJob

    // Serializes startStream/stopStream to prevent race where stopStream nulls
    // audioTrack after startStream has already decided to reuse it.
    private val streamLifecycleLock = Mutex()

    // Lock protecting audioDecoder lifecycle (startStream/stopStream/processBinaryMessage/close)
    // Prevents race where processBinaryMessage() calls decode() on a decoder
    // that startStream() or close() has already released.
    private val decoderLock = Mutex()
    private var audioDecoder: AudioDecoder? = null

    private var playbackJob: Job? = null

    private val _bufferState = MutableStateFlow(BufferState(0L, false, 0))
    override val bufferState: StateFlow<BufferState> = _bufferState.asStateFlow()

    private val _playbackPosition = MutableStateFlow(0L)
    override val playbackPosition: StateFlow<Long> = _playbackPosition.asStateFlow()

    // Error events — SharedFlow(replay=0) so new subscribers never see stale errors
    private val _streamError = MutableSharedFlow<Throwable>(replay = 0, extraBufferCapacity = 1)
    override val streamError: Flow<Throwable> = _streamError.asSharedFlow()

    private var streamConfig: StreamStartPlayer? = null
    private var isStreaming = false

    // Shared sorted queue between producer (processBinaryMessage) and consumer (playback thread)
    private class RawFrame(val timestamp: Long, val data: ByteArray)

    private val queue = ArrayList<RawFrame>(64)
    private val queueLock = Mutex()

    // Signal from producer to consumer: "new frame available". Channel(1) with DROP_OLDEST
    // coalesces multiple signals into one wakeup — consumer drains all ready frames per wakeup.
    private val frameSignal = Channel<Unit>(Channel.CONFLATED)

    /**
     * Minimum queue depth before consumer starts draining.
     * WebSocket (TCP, ordered): low value (2) — just enough to absorb scheduling jitter.
     * WebRTC (SCTP, unordered): high value (32) — absorbs out-of-order delivery.
     * Set by [SendspinClientFactory] before each connection based on transport type.
     */
    @Volatile
    var reorderDepth: Int = 32

    /**
     * User's playback-delay knob in microseconds. Applied per-chunk in the consumer:
     *   target_local = serverTimeToLocal(ts) - userDelayMicros
     * Positive values play earlier in server time to compensate for downstream
     * pipeline lag (AudioTrack buffer, DAC, external speakers). Set by
     * [SendspinClientFactory] from the user's setting. Hot-tunable.
     */
    @Volatile
    var userDelayMicros: Long = 0L

    // Network disconnection tracking for starvation handling
    private var isNetworkDisconnected = false

    // Tracks current AudioTrack format to enable reuse across reconnections
    private data class SinkConfig(
        val outputCodec: AudioCodec,
        val sampleRate: Int,
        val channels: Int,
        val bitDepth: Int,
    )

    private var currentSinkConfig: SinkConfig? = null

    /**
     * Signal that the network transport has dropped.
     * Cleared automatically when startStream() is called on reconnect.
     */
    fun onNetworkDisconnected() {
        logger.i { "Network disconnected" }
        isNetworkDisconnected = true
    }

    override suspend fun startStream(config: StreamStartPlayer) = streamLifecycleLock.withLock {
        logger.i { "Starting stream: ${config.codec}, ${config.sampleRate}Hz, ${config.channels}ch, ${config.bitDepth}bit" }

        isNetworkDisconnected = false
        streamConfig = config
        isStreaming = true
        // Create and configure decoder atomically under lock
        val (outputCodec, outputBitDepth) = decoderLock.withLock {
            audioDecoder?.release()
            audioDecoder = null

            val newDecoder = createDecoder(config)
            val formatSpec = AudioFormatSpec(
                codec = AudioCodec.valueOf(config.codec.uppercase()),
                channels = config.channels,
                sampleRate = config.sampleRate,
                bitDepth = config.bitDepth,
            )
            newDecoder.configure(formatSpec, config.codecHeader)
            audioDecoder = newDecoder
            newDecoder.getOutputCodec() to newDecoder.getOutputBitDepth()
        }

        // Reuse existing AudioTrack if format unchanged (avoids click on track transitions)
        val newSinkConfig =
            SinkConfig(outputCodec, config.sampleRate, config.channels, outputBitDepth)
        if (newSinkConfig == currentSinkConfig) {
            logger.i { "Reusing existing AudioTrack (same format: $newSinkConfig)" }
            mediaPlayerController.flush()
            mediaPlayerController.resumeSink()
        } else {
            logger.i { "Creating new AudioTrack: $newSinkConfig" }
            mediaPlayerController.prepareStream(
                codec = outputCodec,
                sampleRate = config.sampleRate,
                channels = config.channels,
                bitDepth = outputBitDepth,
                codecHeader = config.codecHeader,
                listener = object : MediaPlayerListener {
                    override fun onReady() {
                        logger.i { "MediaPlayer ready for stream ($outputCodec)" }
                    }

                    override fun onAudioCompleted() {
                        logger.i { "Audio completed" }
                    }

                    override fun onError(error: Throwable?) {
                        logger.e(error) { "MediaPlayer error - stopping stream" }
                        launch {
                            _streamError.emit(error ?: Exception("Unknown MediaPlayer error"))
                            stopStream()
                        }
                    }
                },
            )
            currentSinkConfig = newSinkConfig
        }

        queueLock.withLock { queue.clear() }
        startPlaybackThread()
    }

    private fun createDecoder(config: StreamStartPlayer): AudioDecoder {
        val codec = codecByName(config.codec.uppercase())
        logger.i { "Creating decoder for codec: $codec" }
        return codec?.decoderInitializer?.invoke() ?: PcmDecoder()
    }

    /**
     * Producer: parse binary message, sorted-insert raw frame into shared queue.
     */
    override suspend fun processBinaryMessage(data: ByteArray) {
        if (!isStreaming) {
            logger.d { "Received audio chunk but not streaming (ignoring)" }
            return
        }

        val binaryMessage = BinaryMessage.decode(data) ?: run {
            logger.w { "Failed to decode binary message" }
            return
        }

        if (binaryMessage.type != BinaryMessageType.AUDIO_CHUNK) {
            logger.d { "Ignoring non-audio binary message: ${binaryMessage.type}" }
            return
        }

        val ts = binaryMessage.timestamp

        // Sorted insert into reorder queue, then signal consumer
        val frame = RawFrame(ts, binaryMessage.data)
        queueLock.withLock {
            val pos = queue.binarySearchBy(frame.timestamp) { it.timestamp }
            queue.add(if (pos < 0) -(pos + 1) else pos, frame)
        }
        frameSignal.trySend(Unit)
    }

    /**
     * Consumer: decode the oldest frame from sorted queue and write PCM to AudioTrack.
     * Runs on high-priority [audioDispatcher]. Paced by blocking AudioTrack.write().
     */
    private fun startPlaybackThread() {
        playbackJob?.cancel()
        playbackJob = CoroutineScope(audioDispatcher + SupervisorJob()).launch {
            logger.i {
                "Playback consumer started (reorderDepth=$reorderDepth, " +
                        "userDelayMs=${userDelayMicros / 1000})"
            }

            // Track the userDelay we last phase-aligned to. When it changes, flush
            // AudioTrack so the gate's new waitUs drives output timing instead of
            // being absorbed by the ~40–120 ms of buffer inertia. Without this,
            // ±10 ms tweaks don't produce audible shifts.
            var lastAppliedUserDelay = userDelayMicros

            try {
                while (isActive && isStreaming) {
                    // Drain all ready frames before suspending
                    var drained = false
                    while (isActive && isStreaming) {
                        val frame = queueLock.withLock {
                            if (queue.size > reorderDepth) queue.removeAt(0) else null
                        } ?: break

                        drained = true

                        val currentUserDelay = userDelayMicros
                        if (currentUserDelay != lastAppliedUserDelay) {
                            logger.i {
                                "userDelayMs changed ${lastAppliedUserDelay / 1000} → " +
                                        "${currentUserDelay / 1000} — flushing AudioTrack"
                            }
                            // AudioTrack.flush() is a no-op in PLAYING state — must pause first.
                            // pause + flush + resume drains the ring buffer so the gate's new
                            // waitUs actually sets fresh phase instead of being absorbed.
                            mediaPlayerController.pauseSink()
                            mediaPlayerController.flush()
                            mediaPlayerController.resumeSink()
                            lastAppliedUserDelay = currentUserDelay
                        }

                        // Wall-clock gate — only when clock sync is reliable.
                        // Decode still happens either way so the codec keeps state.
                        val shouldPlay = if (clockSynchronizer.currentQuality != SyncQuality.LOST) {
                            val target = clockSynchronizer.serverTimeToLocal(frame.timestamp) -
                                    currentUserDelay
                            val waitUs = target - clockSynchronizer.getCurrentTimeMicros()
                            when {
                                waitUs > 1_000 -> {
                                    delay(waitUs / 1000)
                                    true
                                }
                                waitUs < -100_000 -> {
                                    logger.d { "Dropped late chunk: lateBy=${-waitUs / 1000}ms" }
                                    false
                                }
                                else -> true
                            }
                        } else {
                            true
                        }

                        val pcmData = decoderLock.withLock {
                            audioDecoder?.decode(frame.data) ?: continue
                        }
                        if (shouldPlay) {
                            mediaPlayerController.writeRawPcm(pcmData)
                        }
                    }

                    if (!drained) {
                        // No frames ready — suspend until producer signals
                        frameSignal.receive()
                    }
                }
            } catch (_: CancellationException) {
                // Normal shutdown
            } catch (e: Exception) {
                logger.e(e) { "Consumer error: ${e.message}" }
            }
            logger.i { "Playback consumer stopped" }
        }
    }

    override suspend fun clearStream() {
        logger.i { "Clearing stream" }
        queueLock.withLock { queue.clear() }
        _playbackPosition.update { 0L }
    }

    override suspend fun stopStream() = streamLifecycleLock.withLock {
        logger.i { "Stopping stream" }
        isStreaming = false
        isNetworkDisconnected = false
        playbackJob?.cancel()
        playbackJob = null

        queueLock.withLock { queue.clear() }
        decoderLock.withLock { audioDecoder?.reset() }
        // Pause+flush (don't release). Keeps HW warmed up so the next startStream
        // can reuse the AudioTrack with deterministic latency instead of paying a
        // variable hardware warm-up cost that shifts phase across pause/resume.
        // Full release happens in close().
        mediaPlayerController.pauseSink()
        mediaPlayerController.flush()
        _playbackPosition.update { 0L }
        _bufferState.update { BufferState(0L, false, 0) }
    }

    override fun close() {
        logger.i { "Closing AudioStreamManager" }
        playbackJob?.cancel()
        // Full HW release now that we're done with this pipeline.
        mediaPlayerController.stopRawPcmStream()
        currentSinkConfig = null
        runBlocking {
            decoderLock.withLock {
                audioDecoder?.release()
                audioDecoder = null
            }
        }
        supervisorJob.cancel()
    }
}
