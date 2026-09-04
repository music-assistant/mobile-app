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
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
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

    // Reactive: true while the consumer has no audio to feed the sink (queue drained to the
    // reorder cushion), false while it is actively playing. Purely reflects the current buffer
    // state — the owner composes it with transport + play state to decide on teardown, so this
    // class carries no judgement about *why* the buffer is empty.
    private val _isStarved = MutableStateFlow(false)
    override val isStarved: StateFlow<Boolean> = _isStarved.asStateFlow()

    private var streamConfig: StreamStartPlayer? = null
    private var isStreaming = false

    // Admission gate, deliberately separate from [isStreaming].
    //
    // The server front-loads a new stream at roughly 25x realtime against the advertised
    // buffer_capacity. Gating admission on [isStreaming] meant every chunk that landed during
    // the decoder rebuild and AudioTrack setup was discarded — measured at ~4.9s of audio for
    // 186ms of cold-start setup. Those chunks carry *future* timestamps, so the wall-clock gate
    // would have played them on time; they were simply thrown away before they could be queued.
    //
    // So admission opens as soon as the queue is reset, before the sink exists. The consumer
    // still waits for [isStreaming], and the wall-clock gate still decides when each frame
    // plays. Queue growth stays bounded by the server honouring buffer_capacity, exactly as
    // before — this changes what we keep, not how much the server sends.
    @Volatile
    private var isAccepting = false

    // Shared sorted queue between producer (processBinaryMessage) and consumer (playback thread).
    // ArrayDeque so the consumer's head removal is O(1) instead of an O(n) array shift per frame
    // (the buffer holds up to 30s of compressed frames). Reorder inserts are still indexed, but
    // those are rare (out-of-order delivery only); the hot path is removeFirst().
    private class RawFrame(val timestamp: Long, val data: ByteArray)

    private val queue = ArrayDeque<RawFrame>(64)
    private val queueLock = Mutex()

    // Timestamp of the most recently consumed frame. Lets the producer drop frames the
    // server re-sends after a reconnect (already played), so overlap doesn't double-play.
    // Long.MIN_VALUE = nothing consumed yet. Read/written under queueLock. Assumes a monotonic
    // server timeline: it is reset to MIN_VALUE on every discontinuity (clearStream / fresh
    // startStream), so a legitimate timeline restart isn't mistaken for stale replays.
    private var lastConsumedTs = Long.MIN_VALUE

    // Signal from producer to consumer: "new frame available". Channel(1) with DROP_OLDEST
    // coalesces multiple signals into one wakeup — consumer drains all ready frames per wakeup.
    private val frameSignal = Channel<Unit>(Channel.CONFLATED)

    // --- Head-loss diagnostics (SSHEAD) ------------------------------------------------
    // Field instrumentation for "the local player skips the first seconds of a track".
    // Two uncapped drop paths can eat the head of a stream: chunks discarded while the sink
    // is still being built (producer), and chunks discarded by the wall-clock gate as late
    // (consumer). Counting both, plus the server-timeline span actually lost, tells the two
    // apart from a log. Counts only — a burst is thousands of chunks, so never log per chunk.
    //
    // Threading: the producer coroutine owns the pre* fields, the consumer coroutine owns the
    // late* fields and emits the summary. The sets are disjoint, so no lock is needed; only
    // the fields that cross the boundary are @Volatile.
    private class HeadLossStats {
        var startRequestedUs = 0L

        @Volatile
        var streamingAtUs = 0L

        @Volatile
        var preDropped = 0

        @Volatile
        var preBytes = 0

        @Volatile
        var preFirstTs = Long.MIN_VALUE

        @Volatile
        var firstQueuedTs = Long.MIN_VALUE
        var lateDropped = 0
        var lateFirstMs = 0L
        var lateLastMs = 0L
        var reported = false
    }

    private val trace = Logger.withTag("SSHEAD")

    @Volatile
    private var headLoss = HeadLossStats()

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

    // Tracks current AudioTrack format to enable reuse across reconnections
    private data class SinkConfig(
        val outputCodec: AudioCodec,
        val sampleRate: Int,
        val channels: Int,
        val bitDepth: Int,
    )

    private var currentSinkConfig: SinkConfig? = null

    override suspend fun startStream(config: StreamStartPlayer) = streamLifecycleLock.withLock {
        logger.i { "Starting stream: ${config.codec}, ${config.sampleRate}Hz, ${config.channels}ch, ${config.bitDepth}bit" }

        // A rapid skip burst can queue several stream/start events behind this lock. If a
        // newer one already superseded us (collectLatest cancelled this coroutine), bail before
        // touching the decoder so the final stream is the only one that materializes.
        currentCoroutineContext().ensureActive()

        // Resume path: a reconnect can re-issue stream/start with the same format while we're
        // still streaming. Keep the buffered audio, decoder, and consumer alive — chunks just
        // resume flowing into the live queue (producer de-dups overlap). Wiping here would
        // drop the entire prebuffer and audibly cut playback mid-hiccup.
        if (isStreaming && audioDecoder != null && config == streamConfig) {
            logger.i { "stream/start for the in-flight stream — resuming, buffer preserved" }
            // Intentionally keep the existing decoder (no reset): the server stitches tracks into
            // one continuous stream, so a same-format stream/start is a continuation, not a new
            // codec context — resetting would discard valid decoder state.
            mediaPlayerController.resumeSink()
            return@withLock
        }

        // Arm a fresh measurement for this stream. After the resume fast-path, so a
        // same-format reconnect does not wipe a live one.
        headLoss = HeadLossStats().apply {
            startRequestedUs = clockSynchronizer.getCurrentTimeMicros()
        }

        try {
            stopPlaybackThread()

            // Reset the queue and open admission *before* the expensive rebuild below, so the
            // server's front-load burst is captured instead of discarded. stopPlaybackThread
            // joined the old consumer, so nothing is draining while we clear.
            queueLock.withLock {
                queue.clear()
                lastConsumedTs = Long.MIN_VALUE
            }
            isAccepting = true

            streamConfig = config
            // Create and configure decoder atomically under lock
            val (outputCodec, outputBitDepth) = decoderLock.withLock {
                audioDecoder?.release()
                audioDecoder = null

                // Guard the expensive (~50-200ms, blocking) decoder rebuild — the dominant
                // per-skip cost. If superseded, abort before paying it.
                currentCoroutineContext().ensureActive()

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

            isStreaming = true
            headLoss.streamingAtUs = clockSynchronizer.getCurrentTimeMicros()
            _isStarved.value = false

            // Don't start a consumer for a track that's already been skipped past.
            currentCoroutineContext().ensureActive()
            startPlaybackThread()
        } catch (e: CancellationException) {
            // Superseded mid-setup. Invalidate the reuse guard so the next (final) startStream
            // is forced into a clean rebuild rather than the resume fast-path — otherwise an
            // armed guard with no running consumer would leave us silent. Also stop any consumer
            // this call managed to launch on the detached playback scope.
            // Close admission too: without a consumer, an open gate would accumulate frames for
            // a stream that will never play. The next startStream re-opens it.
            isAccepting = false
            streamConfig = null
            stopPlaybackThread(join = false)
            throw e
        }
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
        if (!isAccepting) {
            // Chunks arriving outside an armed stream. This used to also catch the whole
            // front-load burst during sink setup — the head-loss bug. It should now only fire
            // between streams; the counter stays so a regression is visible in the log.
            val stats = headLoss
            BinaryMessage.decode(data)
                ?.takeIf { it.type == BinaryMessageType.AUDIO_CHUNK }
                ?.let { chunk ->
                    stats.preDropped++
                    stats.preBytes += data.size
                    if (stats.preFirstTs == Long.MIN_VALUE) stats.preFirstTs = chunk.timestamp
                }
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

        // Sorted insert into reorder queue, then signal consumer.
        // De-dup so a reconnect replaying overlapping chunks doesn't double-play: skip frames
        // already consumed (ts <= lastConsumedTs) and exact-timestamp duplicates still queued.
        val inserted = queueLock.withLock {
            if (ts <= lastConsumedTs) return@withLock false
            val pos = queue.binarySearchBy(ts) { it.timestamp }
            if (pos >= 0) return@withLock false
            queue.add(-(pos + 1), RawFrame(ts, binaryMessage.data))
            updateBufferedDurationLocked()
            true
        }
        if (inserted) {
            val stats = headLoss
            if (stats.firstQueuedTs == Long.MIN_VALUE) stats.firstQueuedTs = ts
            frameSignal.trySend(Unit)
        }
    }

    /**
     * Consumer: decode the oldest frame from sorted queue and write PCM to AudioTrack.
     * Runs on high-priority [audioDispatcher]. Paced by blocking AudioTrack.write().
     */
    private suspend fun stopPlaybackThread(join: Boolean = true) {
        isStreaming = false
        if (join) {
            playbackJob?.cancelAndJoin()
        } else {
            playbackJob?.cancel()
        }
        playbackJob = null
    }

    /**
     * One-shot head-loss alarm, evaluated on the first chunk that reaches the sink.
     *
     * Silent on a healthy stream. It logs only when audio was actually lost, so a regression
     * of the admission gate shows up in a user-shared log without costing a line per track.
     * [headLossMs] is the span of the server timeline between the earliest chunk this stream
     * saw and the first one it played — exactly the audio the user does not hear.
     */
    private fun reportHeadLoss(firstPlayedTs: Long) {
        val stats = headLoss
        if (stats.reported) return
        stats.reported = true
        if (stats.preDropped == 0 && stats.lateDropped == 0) return

        // Prefer the earliest chunk dropped before the sink existed; fall back to the first
        // one that made it into the queue when nothing was dropped that early.
        val headTs = stats.preFirstTs.takeIf { it != Long.MIN_VALUE }
            ?: stats.firstQueuedTs.takeIf { it != Long.MIN_VALUE }
        val headLossMs = headTs?.let { (firstPlayedTs - it) / 1000 } ?: -1L
        val clock = clockSynchronizer.getStats()

        trace.w {
            "SSHEAD head-loss headLossMs=$headLossMs " +
                    "setupMs=${(stats.streamingAtUs - stats.startRequestedUs) / 1000} " +
                    "preDropped=${stats.preDropped} preBytes=${stats.preBytes} " +
                    "lateDropped=${stats.lateDropped} " +
                    "lateMs=${stats.lateFirstMs}..${stats.lateLastMs} " +
                    "clock=off=${clock.offsetMs}ms rtt=${clock.rttMs}ms ${clock.quality} " +
                    "samples=${clockSynchronizer.currentSampleCount}"
        }
    }

    /**
     * Mid-stream drift alarm. The head-loss line is emitted at first play, so it cannot see
     * chunks the wall-clock gate drops later. Silent unless the gate actually dropped some.
     */
    private fun reportStreamEnd(reason: String) {
        val stats = headLoss
        if (stats.lateDropped == 0) return
        trace.w {
            "SSHEAD stream-end reason=$reason lateDroppedTotal=${stats.lateDropped} " +
                    "lateMs=${stats.lateFirstMs}..${stats.lateLastMs}"
        }
    }

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
                            if (queue.size > reorderDepth) {
                                queue.removeFirst().also {
                                    lastConsumedTs = it.timestamp
                                    updateBufferedDurationLocked()
                                }
                            } else {
                                null
                            }
                        } ?: break

                        drained = true
                        _isStarved.value = false

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
                                    // Head-loss path 2: uncapped fast-forward to the wall
                                    // clock. Count it; a per-chunk log here would flood.
                                    val lateMs = -waitUs / 1000
                                    val stats = headLoss
                                    if (stats.lateDropped == 0) stats.lateFirstMs = lateMs
                                    stats.lateLastMs = lateMs
                                    stats.lateDropped++
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
                            reportHeadLoss(frame.timestamp)
                            mediaPlayerController.writeRawPcm(pcmData)
                        }
                    }

                    if (!drained) {
                        // Out of audio to feed the sink. Publish it and park until inflow resumes
                        // or we're cancelled. Whether this is an outage (tear down) or just a
                        // transient gap (pause/resume/ramp) is the owner's call to make from
                        // transport + play state — not ours.
                        _isStarved.value = true
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
        reportStreamEnd("clear")
        queueLock.withLock {
            queue.clear()
            lastConsumedTs = Long.MIN_VALUE
        }
        _isStarved.value = false
        _playbackPosition.update { 0L }
        _bufferState.update { BufferState(0L, false, 0) }
    }

    /**
     * Recompute [BufferState.bufferedDuration] = microseconds of audio queued ahead of the
     * consumer head (server presentation-time span). MUST be called while holding [queueLock].
     * Before the first consume [lastConsumedTs] is MIN_VALUE, so the queue head is the baseline.
     */
    private fun updateBufferedDurationLocked() {
        val span = if (queue.isEmpty()) {
            0L
        } else {
            val head = if (lastConsumedTs != Long.MIN_VALUE) lastConsumedTs else queue.first().timestamp
            (queue.last().timestamp - head).coerceAtLeast(0L)
        }
        _bufferState.update { it.copy(bufferedDuration = span) }
    }

    override suspend fun stopStream() = streamLifecycleLock.withLock {
        logger.i { "Stopping stream" }
        reportStreamEnd("stop")
        isAccepting = false
        _isStarved.value = false
        stopPlaybackThread()

        queueLock.withLock {
            queue.clear()
            lastConsumedTs = Long.MIN_VALUE
        }
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
        isAccepting = false
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
