package io.music_assistant.sendspin.source

import co.touchlab.kermit.Logger
import io.music_assistant.sendspin.api.AudioCodec
import io.music_assistant.sendspin.api.AudioEncoder
import io.music_assistant.sendspin.api.CaptureFormat
import io.music_assistant.sendspin.api.EncoderFactory
import io.music_assistant.sendspin.api.MonotonicClock
import io.music_assistant.sendspin.api.SourceConfig
import io.music_assistant.sendspin.api.SourceDialect
import io.music_assistant.sendspin.api.SourceStatus
import io.music_assistant.sendspin.clock.ClockSync
import io.music_assistant.sendspin.session.Activation
import io.music_assistant.sendspin.session.ActivationPolicy
import io.music_assistant.sendspin.session.NoiseSession
import io.music_assistant.sendspin.wire.BinaryFrames
import io.music_assistant.sendspin.wire.ClientStreamEndMessage
import io.music_assistant.sendspin.wire.SourceCommandObject
import io.music_assistant.sendspin.wire.VersionedRole
import io.music_assistant.sendspin.wire.WireCodec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The `source@v1` role of one connection key. Only the server starts a stream: an
 * unsolicited `client-stream/start` is a protocol error, so the capture runs from a
 * `start` command to a `stop`, to the loss of the role, or to the end of the input.
 *
 * The session reader calls [onReady], [onActivated] and [onCommand]; they only set
 * state and never suspend. [run] does the work on the connection's companion scope.
 */
internal class SourceStreamer(
    private val config: SourceConfig,
    private val encoders: EncoderFactory?,
    private val clockSync: ClockSync,
    private val clock: MonotonicClock,
    /** Where the capture is collected and encoded. */
    private val encodeDispatcher: CoroutineDispatcher,
    private val health: SourceCodecHealth,
) {
    private val logger = Logger.withTag("Sendspin.Source")
    private val _status = MutableStateFlow(SourceStatus.Off)

    /** True after this connection sent the `client/state` that carries the source. */
    private val reported = MutableStateFlow(false)

    /**
     * [SourceStatus.Ready] only after the state report: a server lists a client as a
     * source from its first `client/state`, and rejects a play request before it.
     */
    val status: Flow<SourceStatus> = combine(_status, reported) { status, reported ->
        if (status == SourceStatus.Ready && !reported) SourceStatus.Off else status
    }.distinctUntilChanged()

    /**
     * The number of the `start` in effect, or null while stopped. Each start gets a new
     * number, so a stop and a start in quick succession still end one stream and open another.
     */
    private val authorized = MutableStateFlow<Int?>(null)
    private var starts = 0

    // Written by the session reader only.
    private var trusted = false
    private var roleActive = false
    private var playback = false

    /** Spec 1.0.0-rc1 servers send `start` without declaring the playback activity. */
    private val canStream: Boolean
        get() = roleActive && (playback || config.dialect == SourceDialect.RC1)

    /** A new connection: a `start` does not survive a reconnect. */
    fun onReady(trusted: Boolean) {
        this.trusted = trusted
        roleActive = false
        playback = false
        authorized.value = null
        reported.value = false
        _status.value = SourceStatus.Off
    }

    /** The connection sent its `client/state`. */
    fun onStateReported() {
        reported.value = true
    }

    fun onActivated(activation: Activation) {
        roleActive = VersionedRole.SOURCE_V1.identifier in activation.activeRoles
        playback = ActivationPolicy.ACTIVITY_PLAYBACK in activation.activities
        if (!canStream) authorized.value = null
        _status.update { current ->
            when {
                // An unpaired session never gets the role, so only a paired one can refuse it.
                !roleActive -> if (trusted) SourceStatus.Refused else SourceStatus.Off
                current == SourceStatus.Streaming && authorized.value != null -> SourceStatus.Streaming
                else -> SourceStatus.Ready
            }
        }
    }

    fun onCommand(command: SourceCommandObject) {
        when (command.command) {
            SourceCommandObject.START -> when {
                !canStream -> logger.i { "Ignoring source start: role=$roleActive playback=$playback" }
                authorized.value == null -> {
                    logger.i { "Source start accepted" }
                    authorized.value = ++starts
                }

                else -> Unit // A duplicate start has no effect.
            }

            SourceCommandObject.STOP -> {
                logger.i { "Source stop" }
                authorized.value = null
            }
            else -> logger.d { "Ignoring source command ${command.command}" }
        }
    }

    /** Runs until cancelled, on one connection attempt. */
    suspend fun run(session: NoiseSession) {
        try {
            authorized.collectLatest { start -> start?.let { stream(session, it) } }
        } finally {
            _status.value = SourceStatus.Off
        }
    }

    private suspend fun stream(session: NoiseSession, start: Int) {
        var announced: SourceStreamFormat? = null
        var inputEnded = false
        // A stream that sends nothing looks like a dead source to the server: say why.
        var dropped = 0
        try {
            encoded()
                .flowOn(encodeDispatcher)
                .buffer(BACKLOG_CHUNKS, BufferOverflow.DROP_OLDEST)
                .collect { chunk ->
                    // After a stalled link, live audio is worth more than the backlog.
                    val ageMicros = clock.nowMicros() - chunk.audio.captureTimeMicros
                    val late = ageMicros > MAX_BACKLOG_MICROS
                    val serverMicros = clockSync.toServerMicros(chunk.audio.captureTimeMicros)?.takeUnless { late }
                        ?: run {
                            if (dropped++ % DROP_LOG_INTERVAL == 0) {
                                logger.w { "Dropping source audio: ageUs=$ageMicros synced=${clockSync.isSynced} n=$dropped" }
                            }
                            return@collect
                        }
                    if (chunk.format !== announced) {
                        // Also replaces the format of the open stream, when FLAC gives way to PCM.
                        session.send(WireCodec.encode(chunk.format.startMessage()))
                        logger.i { "Source stream open: ${chunk.format} ageUs=$ageMicros" }
                        announced = chunk.format
                        _status.value = SourceStatus.Streaming
                    }
                    session.sendBinary(
                        BinaryFrames.TYPE_SOURCE_AUDIO_CHUNK,
                        BinaryFrames.sourceChunk(serverMicros, chunk.audio.data),
                    )
                }
            inputEnded = true
            logger.i { "The capture ended" }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            inputEnded = true
            logger.w(e) { "Source stream failed" }
        } finally {
            authorized.compareAndSet(start, null)
            _status.update { if (it == SourceStatus.Streaming) SourceStatus.Ready else it }
            // An input that gave nothing declines the start, which only the current spec defines.
            val declines = inputEnded && config.dialect == SourceDialect.SPEC
            if (announced != null || declines) endStream(session)
        }
    }

    /** Best effort: the session may be gone, and then the server has ended the stream itself. */
    private suspend fun endStream(session: NoiseSession) = withContext(NonCancellable) {
        try {
            withTimeoutOrNull(END_TIMEOUT_MILLIS) { session.send(WireCodec.encode(ClientStreamEndMessage())) }
                ?: logger.d { "client-stream/end not sent in time" }
        } catch (e: Exception) {
            logger.d { "client-stream/end not sent: ${e.message}" }
        }
    }

    private fun encoded(): Flow<SourceChunk> = flow {
        val format = config.capture.format
        val encoding = SourceEncoding(format, flacEncoder(format), health)
        try {
            config.capture.frames().collect { audio -> encoding.encode(audio).forEach { emit(it) } }
        } finally {
            encoding.release()
        }
    }

    private fun flacEncoder(format: CaptureFormat): AudioEncoder? =
        encoders?.takeUnless { health.flacFailed }?.let { factory ->
            try {
                factory.create(AudioCodec.FLAC, format)
            } catch (e: Exception) {
                logger.w(e) { "No FLAC encoder. The source uses PCM" }
                health.flacFailed = true
                null
            }
        }

    private companion object {
        const val BACKLOG_CHUNKS = 32
        const val MAX_BACKLOG_MICROS = 500_000L
        const val END_TIMEOUT_MILLIS = 1_000L
        const val DROP_LOG_INTERVAL = 50
    }
}
