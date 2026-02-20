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
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Manages the complete audio playback pipeline for Sendspin streaming.
 *
 * ## Architecture Overview
 *
 * This component implements a producer-consumer pattern with precise timestamp-based
 * playback scheduling. Audio chunks arrive with server-assigned timestamps and are
 * played back at the correct local time after clock synchronization.
 *
 * ## Multi-Threaded Architecture
 *
 * AudioStreamManager uses **three separate dispatcher contexts** for optimal performance:
 *
 * ### 1. Default Dispatcher (Producer)
 * - **Purpose**: Binary message reception and audio decoding
 * - **Why**: Decoding is CPU-intensive but not time-critical
 * - **Operations**:
 *   - Receive binary messages from WebSocket
 *   - Parse chunk headers (timestamp, codec, payload)
 *   - Decode audio (Opus/FLAC → PCM or passthrough)
 *   - Add decoded chunks to timestamp-ordered buffer
 * - **Characteristics**: Can run ahead of playback, preparing data in advance
 *
 * ### 2. audioDispatcher (High-Priority Consumer)
 * - **Purpose**: Precise playback timing and audio output
 * - **Why**: Requires low-latency, deterministic scheduling
 * - **Operations**:
 *   - Poll buffer for next chunk
 *   - Check chunk timestamp vs current time
 *   - Wait if too early, drop if too late, play if on-time
 *   - Write PCM data to MediaPlayerController (AudioTrack/MPV)
 * - **Characteristics**: High priority thread, minimal jitter, tight timing loop
 *
 * ### 3. Default Dispatcher (Adaptation)
 * - **Purpose**: Periodic buffer threshold adjustment
 * - **Why**: Network conditions change over time, buffer must adapt
 * - **Operations**:
 *   - Monitor RTT, jitter, drop rate every 5 seconds
 *   - Calculate optimal prebuffer threshold
 *   - Update adaptive buffer manager
 * - **Characteristics**: Low priority, infrequent (5s interval)
 *
 * ## Threading Rationale
 *
 * The separation of decoding (Default) from playback (audioDispatcher) is critical:
 * - **Decoding** can be slow (especially FLAC) and should not block playback
 * - **Playback** must be fast and deterministic to avoid audio glitches
 * - Producer can build buffer ahead of time, consumer drains at playback rate
 *
 * ## Synchronization
 *
 * - **TimestampOrderedBuffer**: Thread-safe queue (synchronized methods)
 * - **StateFlows**: Reactive state updates (thread-safe by design)
 * - **decoderLock**: Protects audioDecoder access across startStream/processBinaryMessage/stopStream
 *
 * ## Error Handling
 *
 * - Decoding errors: Return silence, log error, continue playback
 * - Playback errors: Emit via `streamError` StateFlow, stop stream
 * - Network errors: Auto-reconnect handled by SendspinWsHandler
 *
 * @see AudioPipeline for public interface
 * @see AdaptiveBufferManager for buffer adaptation algorithm
 * @see ClockSynchronizer for time synchronization
 */
class AudioStreamManager(
    private val clockSynchronizer: ClockSynchronizer,
    private val mediaPlayerController: MediaPlayerController
) : AudioPipeline, CoroutineScope {

    private val logger = Logger.withTag("AudioStreamManager")
    private val supervisorJob = SupervisorJob()

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Default + supervisorJob

    private val audioBuffer = TimestampOrderedBuffer()

    // Lock protecting audioDecoder lifecycle (startStream/stopStream/processBinaryMessage/close)
    // This prevents the race where processBinaryMessage() calls decode() on a decoder
    // that startStream() or close() has already released.
    private val decoderLock = Mutex()
    private var audioDecoder: AudioDecoder? = null

    private var playbackJob: Job? = null
    private var adaptationJob: Job? = null

    // Adaptive buffering manager
    private val adaptiveBufferManager = AdaptiveBufferManager(clockSynchronizer)

    private val _bufferState = MutableStateFlow(
        BufferState(
            bufferedDuration = 0L,
            isUnderrun = false,
            droppedChunks = 0,
            targetBufferDuration = 0L,
            currentPrebufferThreshold = 0L,
            smoothedRTT = 0.0,
            jitter = 0.0,
            dropRate = 0.0
        )
    )
    override val bufferState: StateFlow<BufferState> = _bufferState.asStateFlow()

    private val _playbackPosition = MutableStateFlow(0L)
    override val playbackPosition: StateFlow<Long> = _playbackPosition.asStateFlow()

    // Error events — SharedFlow(replay=0) so new subscribers never see stale errors
    private val _streamError = MutableSharedFlow<Throwable>(replay = 0, extraBufferCapacity = 1)
    override val streamError: Flow<Throwable> = _streamError.asSharedFlow()

    private var streamConfig: StreamStartPlayer? = null
    private var isStreaming = false
    private var droppedChunksCount = 0

    // Network disconnection tracking for starvation handling
    @Volatile private var isNetworkDisconnected = false

    // Tracks current AudioTrack format to enable reuse across reconnections
    private data class SinkConfig(val outputCodec: AudioCodec, val sampleRate: Int, val channels: Int, val bitDepth: Int)
    private var currentSinkConfig: SinkConfig? = null

    /**
     * Signal that the network transport has dropped.
     * The playback loop will drain the buffer, then pause the sink and start a 30s starvation timer.
     * Cleared automatically when startStream() is called on reconnect.
     */
    fun onNetworkDisconnected() {
        logger.i { "Network disconnected - will pause sink when buffer empties" }
        isNetworkDisconnected = true
    }

    // Buffer state update throttling (performance optimization)
    private var lastBufferStateUpdate = 0L
    private val bufferStateUpdateInterval = 100_000L // Update max every 100ms

    override suspend fun startStream(config: StreamStartPlayer) {
        logger.i { "Starting stream: ${config.codec}, ${config.sampleRate}Hz, ${config.channels}ch, ${config.bitDepth}bit" }

        // Reconnect signal: clear network disconnection flag so starvation timer is cancelled
        isNetworkDisconnected = false

        streamConfig = config
        isStreaming = true
        droppedChunksCount = 0

        // Create and configure decoder atomically under lock to prevent race with
        // processBinaryMessage() calling decode() on a released decoder.
        val outputCodec = decoderLock.withLock {
            // Release old decoder before creating new one
            audioDecoder?.release()
            audioDecoder = null

            // Create appropriate decoder
            val newDecoder = createDecoder(config)

            // Configure decoder
            val formatSpec = AudioFormatSpec(
                codec = AudioCodec.valueOf(config.codec.uppercase()),
                channels = config.channels,
                sampleRate = config.sampleRate,
                bitDepth = config.bitDepth
            )
            newDecoder.configure(formatSpec, config.codecHeader)

            // Only make decoder visible after successful configure
            audioDecoder = newDecoder

            // Determine output codec for MediaPlayerController
            // Decoder tells us what format it outputs:
            // - iOS decoders: passthrough encoded data (OPUS, FLAC, etc) for MPV to handle
            // - Android/Desktop decoders: convert to PCM
            newDecoder.getOutputCodec()
        }

        // Check if we can reuse the existing AudioTrack (same format = no click, no recreation)
        val newSinkConfig = SinkConfig(outputCodec, config.sampleRate, config.channels, config.bitDepth)
        if (newSinkConfig == currentSinkConfig) {
            // Same format — flush stale data and resume playback without destroying AudioTrack
            logger.i { "Reusing existing AudioTrack (same format: $newSinkConfig)" }
            mediaPlayerController.flush()
            mediaPlayerController.resumeSink()
        } else {
            // Different format or first time — full AudioTrack initialization
            logger.i { "Creating new AudioTrack (format changed or first start): $newSinkConfig" }
            mediaPlayerController.prepareStream(
                codec = outputCodec,
                sampleRate = config.sampleRate,
                channels = config.channels,
                bitDepth = config.bitDepth,
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
                        // When MediaPlayer encounters an error (e.g., audio output disconnected),
                        // emit the error and stop the stream to prevent zombie playback
                        launch {
                            _streamError.emit(error ?: Exception("Unknown MediaPlayer error"))
                            stopStream()
                        }
                    }
                }
            )
            currentSinkConfig = newSinkConfig
        }

        // Clear buffer and start playback thread
        audioBuffer.clear()
        startPlaybackThread()
    }

    private fun createDecoder(config: StreamStartPlayer): AudioDecoder {
        val codec = codecByName(config.codec.uppercase())
        logger.i { "Creating decoder for codec: $codec" }
        return codec?.decoderInitializer?.invoke() ?: PcmDecoder()
    }

    override suspend fun processBinaryMessage(data: ByteArray) {
        if (!isStreaming) {
            // Server is still sending chunks after we stopped - this is normal
            // (server doesn't know we stopped until timeout or explicit notification)
            logger.d { "Received audio chunk but not streaming (ignoring)" }
            return
        }

        // Parse binary message (9-byte header + payload)
        val binaryMessage = BinaryMessage.decode(data)
        if (binaryMessage == null) {
            logger.w { "Failed to decode binary message" }
            return
        }

        if (binaryMessage.type != BinaryMessageType.AUDIO_CHUNK) {
            logger.d { "Ignoring non-audio binary message: ${binaryMessage.type}" }
            return
        }

        logger.d { "Received audio chunk: timestamp=${binaryMessage.timestamp}, size=${binaryMessage.data.size} bytes" }

        // Convert server timestamp to local time
        val localTimestamp = clockSynchronizer.serverTimeToLocal(binaryMessage.timestamp)

        // DECODE IMMEDIATELY (producer pattern - prepare data ahead of time)
        // This runs on Default dispatcher with buffer headroom - not time-critical
        // Lock ensures we don't call decode() on a decoder being released by startStream/stopStream
        val decodedPcm = decoderLock.withLock {
            val decoder = audioDecoder ?: run {
                logger.w { "No decoder available" }
                return@withLock null
            }

            try {
                decoder.decode(binaryMessage.data)
            } catch (e: Exception) {
                logger.e(e) { "Error decoding audio chunk" }
                return@withLock null
            }
        } ?: return

        logger.d { "Decoded chunk: ${binaryMessage.data.size} -> ${decodedPcm.size} PCM bytes" }

        // Create audio chunk with DECODED PCM data
        val chunk = AudioChunk(
            timestamp = binaryMessage.timestamp,
            data = decodedPcm,  // Store decoded PCM, not encoded!
            localTimestamp = localTimestamp
        )

        // Add to buffer
        audioBuffer.add(chunk)
        logger.d { "Buffer now has ${audioBuffer.size()} chunks, ${audioBuffer.getBufferedDuration()}μs buffered" }

        // Update buffer state
        updateBufferState()
    }

    private fun startPlaybackThread() {
        playbackJob?.cancel()
        // Launch playback loop on high-priority audioDispatcher
        playbackJob = CoroutineScope(audioDispatcher + SupervisorJob()).launch {
            logger.i { "Starting playback thread on high-priority dispatcher" }

            // Wait for pre-buffer
            waitForPrebuffer()

            // Start adaptation thread
            startAdaptationThread()

            // SYNC FAST-FORWARD: After prebuffer, skip to the first chunk that's "current"
            // This handles the case where pause/next caused a time gap and all buffered
            // chunks have timestamps in the past.
            val syncStartTime = getCurrentTimeMicros()
            var skippedChunks = 0
            while (isActive && isStreaming) {
                val chunk = audioBuffer.peek() ?: break
                val chunkPlaybackTime = chunk.localTimestamp
                val lateThreshold = adaptiveBufferManager.currentLateThreshold

                if (chunkPlaybackTime < syncStartTime - lateThreshold) {
                    // This chunk is late - skip it
                    audioBuffer.poll()
                    skippedChunks++
                } else {
                    // Found a chunk that's current or early - start playing from here
                    break
                }
            }
            if (skippedChunks > 0) {
                logger.i { "🔄 Sync fast-forward: skipped $skippedChunks late chunks to catch up" }
                adaptiveBufferManager.reset() // Reset stats after bulk skip
            }

            var chunksPlayed = 0
            var lastLogTime = getCurrentTimeMicros()
            var starvedAt: TimeSource.Monotonic.ValueTimeMark? = null

            while (isActive && isStreaming) {
                try {
                    val chunk = audioBuffer.peek()

                    if (chunk == null) {
                        if (isNetworkDisconnected) {
                            // Network is down — pause sink when buffer empties and start starvation timer
                            if (starvedAt == null) {
                                starvedAt = TimeSource.Monotonic.markNow()
                                logger.w { "Buffer empty + network disconnected: pausing sink, starting 30s starvation timer" }
                                mediaPlayerController.pauseSink()
                            } else if (starvedAt.elapsedNow() > 30.seconds) {
                                logger.e { "Starvation timeout: no audio data for 30s, stopping stream" }
                                _streamError.emit(Exception("Starvation timeout: no audio for 30s"))
                                break
                            }
                            delay(100)
                        } else {
                            // Normal underrun — network is connected, data will arrive soon
                            if (!_bufferState.value.isUnderrun) {
                                logger.w { "Buffer underrun" }
                                adaptiveBufferManager.recordUnderrun(getCurrentTimeMicros())
                                _bufferState.update { it.copy(isUnderrun = true) }
                            }
                            delay(2) // Wait for more data (was 10ms, reduced for faster recovery)
                        }
                        continue
                    }

                    // Chunk arrived — cancel starvation if active
                    if (starvedAt != null) {
                        starvedAt = null
                        logger.i { "Buffer refilled after starvation, resuming sink" }
                        mediaPlayerController.resumeSink()
                    }

                    // Check sync quality
                    if (clockSynchronizer.currentQuality == SyncQuality.LOST) {
                        logger.w { "Clock sync lost, waiting..." }
                        delay(10) // Reduced from 100ms for faster recovery
                        continue
                    }

                    val currentLocalTime = getCurrentTimeMicros()
                    val chunkPlaybackTime = chunk.localTimestamp
                    val timeDiff = chunkPlaybackTime - currentLocalTime

                    // Use adaptive thresholds
                    val lateThreshold = adaptiveBufferManager.currentLateThreshold
                    val earlyThreshold = adaptiveBufferManager.currentEarlyThreshold

                    when {
                        chunkPlaybackTime < currentLocalTime - lateThreshold -> {
                            // Chunk is too late, drop it
                            audioBuffer.poll()
                            droppedChunksCount++
                            adaptiveBufferManager.recordChunkDropped()
                            logger.w { "Dropped late chunk: ${(currentLocalTime - chunkPlaybackTime) / 1000}ms late" }
                            updateBufferState()
                        }

                        chunkPlaybackTime > currentLocalTime + earlyThreshold -> {
                            // Chunk is too early, wait
                            val delayMs =
                                ((chunkPlaybackTime - currentLocalTime) / 1000).coerceAtMost(20) // Was 100ms, reduced for lower latency
                            logger.d { "Chunk too early, waiting ${delayMs}ms (diff=${timeDiff / 1000}ms)" }
                            delay(delayMs)
                        }

                        else -> {
                            // Chunk is ready to play
                            playChunk(chunk)
                            audioBuffer.poll()
                            adaptiveBufferManager.recordChunkPlayed()
                            _playbackPosition.update { chunk.timestamp }
                            updateBufferState()
                            chunksPlayed++

                            // Log progress every 5 seconds with adaptive buffer info
                            val now = getCurrentTimeMicros()
                            if (now - lastLogTime > 5_000_000) {
                                val bufferMs = audioBuffer.getBufferedDuration() / 1000
                                val targetMs = adaptiveBufferManager.targetBufferDuration / 1000
                                logger.i { "Playback: $chunksPlayed chunks, buffer=${bufferMs}ms (target=${targetMs}ms)" }
                                lastLogTime = now
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.e(e) { "Error in playback thread" }
                }
            }

            logger.i { "Playback thread stopped, total chunks played: $chunksPlayed" }
        }
    }

    private suspend fun waitForPrebuffer() {
        val threshold = adaptiveBufferManager.currentPrebufferThreshold
        logger.i { "Waiting for prebuffer (threshold=${threshold / 1000}ms)..." }

        val startTime = getCurrentTimeMicros()
        val timeoutUs = 5_000_000L // 5 second timeout

        while (isActive && audioBuffer.getBufferedDuration() < threshold) {
            // Check timeout
            if (getCurrentTimeMicros() - startTime > timeoutUs) {
                val bufferMs = audioBuffer.getBufferedDuration() / 1000
                logger.w { "Prebuffer timeout after 5s (buffered=${bufferMs}ms, threshold=${threshold / 1000}ms)" }

                // Emit error state for UI
                _streamError.emit(Exception("Prebuffer timeout - check network connection"))

                // Start playback with whatever we have (graceful degradation)
                if (audioBuffer.getBufferedDuration() > 0) {
                    logger.i { "Starting playback with partial buffer" }
                    return
                } else {
                    // No data at all - stop stream
                    logger.e { "No data received, stopping stream" }
                    stopStream()
                    return
                }
            }

            delay(50)
        }

        val bufferMs = audioBuffer.getBufferedDuration() / 1000
        val thresholdMs = threshold / 1000
        logger.i { "Prebuffer complete: ${bufferMs}ms (threshold=${thresholdMs}ms)" }
    }

    /**
     * Starts the buffer adaptation thread.
     * Runs on Default dispatcher (not audioDispatcher) to avoid interfering with playback.
     * Updates buffer thresholds every 5 seconds based on network conditions (RTT, jitter, drops).
     */
    private fun startAdaptationThread() {
        adaptationJob?.cancel()
        // Use Default dispatcher - this is low-priority periodic work that shouldn't
        // consume cycles from the high-priority audioDispatcher playback thread
        adaptationJob = CoroutineScope(Dispatchers.Default + SupervisorJob()).launch {
            logger.i { "Starting adaptation thread" }
            while (isActive && isStreaming) {
                try {
                    // Update network stats from clock synchronizer
                    val stats = clockSynchronizer.getStats()
                    adaptiveBufferManager.updateNetworkStats(stats.rtt, stats.quality)

                    // Run adaptation logic every 5 seconds
                    adaptiveBufferManager.updateThresholds(getCurrentTimeMicros())

                    delay(5000)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.e(e) { "Error in adaptation thread" }
                }
            }
            logger.i { "Adaptation thread stopped" }
        }
    }

    private fun playChunk(chunk: AudioChunk) {
        try {
            val pcmData = chunk.data

            logger.d { "Writing ${pcmData.size} PCM bytes to AudioTrack" }

            // Write to MediaPlayerController
            val written = mediaPlayerController.writeRawPcm(pcmData)
            if (written < pcmData.size) {
                logger.w { "Only wrote $written/${pcmData.size} bytes to AudioTrack" }
            } else {
                logger.d { "Wrote $written bytes to AudioTrack successfully" }
            }

        } catch (e: Exception) {
            logger.e(e) { "Error writing PCM chunk" }
        }
    }

    private suspend fun updateBufferState() {
        val now = getCurrentTimeMicros()
        val bufferedDuration = audioBuffer.getBufferedDuration()
        val isUnderrun = bufferedDuration == 0L && isStreaming

        // Throttle updates to reduce GC pressure (max every 100ms)
        // Exception: Always update on underrun state changes
        if (now - lastBufferStateUpdate < bufferStateUpdateInterval &&
            _bufferState.value.isUnderrun == isUnderrun
        ) {
            return // Skip update
        }

        lastBufferStateUpdate = now

        _bufferState.update {
            BufferState(
                bufferedDuration = bufferedDuration,
                isUnderrun = isUnderrun,
                droppedChunks = droppedChunksCount,
                // Adaptive metrics
                targetBufferDuration = adaptiveBufferManager.targetBufferDuration,
                currentPrebufferThreshold = adaptiveBufferManager.currentPrebufferThreshold,
                smoothedRTT = adaptiveBufferManager.currentSmoothedRTT,
                jitter = adaptiveBufferManager.currentJitter,
                dropRate = adaptiveBufferManager.getCurrentDropRate()
            )
        }
    }

    override suspend fun clearStream() {
        logger.i { "Clearing stream" }
        audioBuffer.clear()
        _playbackPosition.update { 0L }
        droppedChunksCount = 0
        updateBufferState()
    }

    /**
     * Flush audio for track change (next/prev/seek).
     * Clears buffer and stops native audio for immediate responsiveness,
     * but keeps isStreaming=true so the playback thread can receive new chunks.
     */
    suspend fun flushForTrackChange() {
        logger.i { "Flushing for track change (keeping stream active)" }
        // Clear the audio buffer
        audioBuffer.clear()
        // Reset decoder for new track (under lock to prevent race with processBinaryMessage)
        decoderLock.withLock {
            audioDecoder?.reset()
        }
        // Stop native audio playback immediately (for responsiveness)
        mediaPlayerController.stopRawPcmStream()
        // Reset playback position
        _playbackPosition.update { 0L }
        droppedChunksCount = 0
        // Reset adaptive buffer for new track
        adaptiveBufferManager.reset()
        updateBufferState()
        // NOTE: isStreaming stays TRUE so we can receive new chunks
    }

    override suspend fun stopStream() {
        logger.i { "Stopping stream" }
        isStreaming = false
        isNetworkDisconnected = false
        playbackJob?.cancel()
        playbackJob = null
        adaptationJob?.cancel()
        adaptationJob = null

        audioBuffer.clear()
        decoderLock.withLock {
            audioDecoder?.reset()
        }

        // Reset adaptive buffer manager
        adaptiveBufferManager.reset()

        // Stop raw PCM stream on MediaPlayerController (destroys AudioTrack)
        mediaPlayerController.stopRawPcmStream()

        // Clear sink config so next startStream() recreates the AudioTrack
        currentSinkConfig = null

        _playbackPosition.update { 0L }
        droppedChunksCount = 0
        _bufferState.update {
            BufferState(
                bufferedDuration = 0L,
                isUnderrun = false,
                droppedChunks = 0,
                targetBufferDuration = 0L,
                currentPrebufferThreshold = 0L,
                smoothedRTT = 0.0,
                jitter = 0.0,
                dropRate = 0.0
            )
        }
    }

    // Use monotonic time for playback timing instead of wall clock time
    // This matches the server's relative time base
    // Use monotonic time for playback timing instead of wall clock time
    // This matches the server's relative time base
    private val startMark = kotlin.time.TimeSource.Monotonic.markNow()

    private fun getCurrentTimeMicros(): Long {
        // Use relative time since stream start, not Unix epoch time
        return startMark.elapsedNow().inWholeMicroseconds
    }

    override fun close() {
        logger.i { "Closing AudioStreamManager" }
        playbackJob?.cancel()
        runBlocking {
            decoderLock.withLock {
                audioDecoder?.release()
                audioDecoder = null
            }
        }
        supervisorJob.cancel()
    }
}

