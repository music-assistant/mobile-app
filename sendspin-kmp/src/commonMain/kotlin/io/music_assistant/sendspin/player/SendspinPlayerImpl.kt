package io.music_assistant.sendspin.player

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.music_assistant.sendspin.api.AudioCodec
import io.music_assistant.sendspin.api.AudioPhase
import io.music_assistant.sendspin.api.ClockQuality
import io.music_assistant.sendspin.api.Endpoint
import io.music_assistant.sendspin.api.FailureCause
import io.music_assistant.sendspin.api.LocalPlayerConfig
import io.music_assistant.sendspin.api.PlayerEvent
import io.music_assistant.sendspin.api.PlayerState
import io.music_assistant.sendspin.api.SendspinDeps
import io.music_assistant.sendspin.api.SendspinPlayer
import io.music_assistant.sendspin.api.StopCause
import io.music_assistant.sendspin.api.WarningCode
import io.music_assistant.sendspin.api.WireVersion
import io.music_assistant.sendspin.api.advertisedFormats
import io.music_assistant.sendspin.api.bufferCapacityBytes
import io.music_assistant.sendspin.audio.AudioEvent
import io.music_assistant.sendspin.audio.AudioPipeline
import io.music_assistant.sendspin.audio.StreamAction
import io.music_assistant.sendspin.audio.StreamLifecycle
import io.music_assistant.sendspin.audio.StreamPhase
import io.music_assistant.sendspin.clock.ClockProbe
import io.music_assistant.sendspin.clock.ClockSync
import io.music_assistant.sendspin.connection.ConnectionState
import io.music_assistant.sendspin.connection.ConnectionSupervisor
import io.music_assistant.sendspin.identity.SendspinTrustStore
import io.music_assistant.sendspin.noise.crypto.CryptographyKotlinNoiseCrypto
import io.music_assistant.sendspin.noise.crypto.NoiseCrypto
import io.music_assistant.sendspin.session.Activation
import io.music_assistant.sendspin.session.NoiseSession
import io.music_assistant.sendspin.session.SessionConfig
import io.music_assistant.sendspin.session.SessionHandler
import io.music_assistant.sendspin.session.SessionInfo
import io.music_assistant.sendspin.transport.TransportConnector
import io.music_assistant.sendspin.wire.AudioChunk
import io.music_assistant.sendspin.wire.ClientStateMessage
import io.music_assistant.sendspin.wire.ClientStatePayload
import io.music_assistant.sendspin.wire.EncryptedDeviceInfo
import io.music_assistant.sendspin.wire.GoodbyeReason
import io.music_assistant.sendspin.wire.PlayerStateObject
import io.music_assistant.sendspin.wire.PlayerStateValue
import io.music_assistant.sendspin.wire.PlayerSupport
import io.music_assistant.sendspin.wire.ServerMessage
import io.music_assistant.sendspin.wire.WireAudioFormat
import io.music_assistant.sendspin.wire.WireCodec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Composition root. `config == null` is the only stop. Reconnect-class fields
 * restart the connection; the audio pipeline lives as long as the player is
 * enabled, so buffered audio drains across reconnects.
 *
 * Ownership follows the coroutine tree, which is also the teardown order:
 *
 * ```
 * scope (supplied by the application)
 * └─ config.enabled collector           collectLatest: "false" cancels everything below
 *    └─ runEnabled()                    trust store, clock sync, pipeline, connector
 *       ├─ pipeline.run()               on deps.audioDispatcher; outlives every connection
 *       ├─ live-config collectors       userDelayMicros, hold
 *       └─ ConnectionSupervisor.run()   restarted when a reconnect-class field changes
 *          └─ one attempt at a time
 *             ├─ session.run()          reader coroutine
 *             └─ companion              clock probes and state reports
 * ```
 *
 * Three invariants hold across the module. No class implements `CoroutineScope`,
 * so cancellation is the only teardown. The session reader never blocks on the
 * sink, because it must stay free to process the `stream/clear` that frees the
 * buffer. The audio thread is the single owner of the decoder and the sink handle.
 */
internal class SendspinPlayerImpl(
    private val config: StateFlow<LocalPlayerConfig?>,
    private val deps: SendspinDeps,
    scope: CoroutineScope,
    /** Noise primitives; a test seam only, the app never chooses them. */
    private val crypto: NoiseCrypto = CryptographyKotlinNoiseCrypto(),
    /** One connector per enabled lifetime; injectable so tests can watch its close. */
    private val connectorFactory: (HttpClient) -> TransportConnector = TransportConnector::ktor,
) : SendspinPlayer {
    private val logger = Logger.withTag("SendspinPlayer")
    private val _state = MutableStateFlow<PlayerState>(PlayerState.Disabled)
    private val _events = MutableSharedFlow<PlayerEvent>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    override val state: StateFlow<PlayerState> = _state
    override val events: Flow<PlayerEvent> = _events

    private val held = MutableStateFlow(false)

    override fun hold(held: Boolean) {
        this.held.value = held
    }

    private data class ConnectionKey(
        val endpoint: Endpoint,
        val deviceName: String,
        val codecs: List<AudioCodec>,
        val bitDepth: Int,
        val bufferSeconds: Int,
    ) {
        fun formats(supports: (AudioCodec) -> Boolean) = advertisedFormats(codecs, bitDepth, supports)
    }

    /**
     * `buffer_capacity` reaches the server only in `client/hello`, so the local cap changes with
     * the connection. A smaller local cap than the advertised one evicts the future chunks that
     * the server still sends, and every evicted chunk is a gap in the timeline.
     */
    private fun ConnectionKey.capacityBytes() = bufferCapacityBytes(formats(deps.decoders::supports), bufferSeconds)

    /** An unsupported endpoint never reaches a session, so its wire is moot. */
    private val Endpoint.wire: WireVersion
        get() = when (this) {
            is Endpoint.WebSocket -> wire
            is Endpoint.WebRtc -> wire
            is Endpoint.Unsupported -> WireVersion.LEGACY_9
        }

    init {
        scope.launch {
            config.map { it != null }.distinctUntilChanged().collectLatest { enabled ->
                if (enabled) runEnabledOrFail()
            }
        }
    }

    /**
     * An enabled lifetime must always end in a state. Without this the setup
     * in [runEnabled] could throw straight out of the collector, leaving the
     * app on whatever it last saw and offering a player that cannot play.
     */
    private suspend fun runEnabledOrFail() {
        try {
            runEnabled()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.e(e) { "Local player setup failed" }
            _state.value = PlayerState.Failed(FailureCause.SetupFailed)
        }
    }

    private suspend fun runEnabled() = coroutineScope {
        val trustStore = SendspinTrustStore.load(deps.keyStore, crypto)
        val clockSync = ClockSync(deps.clock)
        val pipeline = AudioPipeline(
            deps.sink,
            deps.decoders,
            clockSync,
            deps.clock,
            0,
        )
        val connector = connectorFactory(deps.httpClient)
        val supervisor = ConnectionSupervisor(
            connector = connector,
            trustStore = trustStore,
            crypto = crypto,
            online = deps.online,
            clock = deps.clock,
            approvePairing = deps.approvePairing,
        )
        val session = Session(pipeline, clockSync, trustStore, supervisor)
        launch(deps.audioDispatcher) { pipeline.run() }
        launch {
            config.filterNotNull().collect {
                pipeline.userDelayMicros = it.userDelayMs * 1_000L
            }
        }
        launch { held.collect(pipeline::hold) }
        launch { session.publishState() }
        launch { session.forwardAudioEvents() }
        launch { session.watchStarvation() }
        try {
            config.filterNotNull()
                .map { ConnectionKey(it.endpoint, it.deviceName, it.codecPreference, it.bitDepth, it.bufferSeconds) }
                .distinctUntilChanged()
                .collectLatest { key ->
                    pipeline.capacityBytes = key.capacityBytes()
                    supervisor.run(
                        endpoint = key.endpoint,
                        sessionConfig = session.sessionConfig(key),
                        handler = session,
                        goodbyeOnCancel = { if (config.value == null) GoodbyeReason.UserRequest else GoodbyeReason.Restart },
                        companion = session::companion,
                    )
                }
        } finally {
            connector.close()
            if (pipeline.status.value.phase == AudioPhase.Playing) {
                _events.tryEmit(
                    PlayerEvent.PlaybackStopped(StopCause.Disabled),
                )
            }
            _state.value = PlayerState.Disabled
        }
    }

    /** One enabled lifetime: session handler plus the state and event mappings. */
    private inner class Session(
        private val pipeline: AudioPipeline,
        private val clockSync: ClockSync,
        private val trustStore: SendspinTrustStore,
        private val supervisor: ConnectionSupervisor,
    ) : SessionHandler {
        private val playerId: String get() = trustStore.clientId
        private var serverName = ""

        /** True until the first stream/start of a new connection: that one may resume. */
        private var newConnection = false

        fun sessionConfig(key: ConnectionKey) = SessionConfig(
            wire = key.endpoint.wire,
            deviceName = key.deviceName,
            playerSupport = PlayerSupport(
                supportedFormats = key.formats(deps.decoders::supports).map(WireAudioFormat::from),
                bufferCapacity = key.capacityBytes(),
                supportedCommands = emptyList(),
            ),
            deviceInfo = with(deps.deviceInfo) {
                EncryptedDeviceInfo(
                    productName = productName,
                    manufacturer = manufacturer,
                    softwareVersion = softwareVersion,
                )
            },
        )

        override fun onReady(info: SessionInfo) {
            serverName = info.serverName
            newConnection = true
        }

        override fun onActivated(activation: Activation) {
            _events.tryEmit(PlayerEvent.ServerRefreshNeeded)
        }

        override suspend fun onMessage(message: ServerMessage) {
            when (message) {
                is ServerMessage.Time -> clockSync.onReply(message.payload)
                is ServerMessage.StreamStart -> message.player?.let { format ->
                    if (held.value) _events.tryEmit(PlayerEvent.StreamStartedWhileHeld(playerId))
                    pipeline.apply(
                        StreamLifecycle.onStart(pipeline.phase, pipeline.stream.value.format, format, newConnection),
                    )
                    newConnection = false
                }

                is ServerMessage.StreamEnd -> pipeline.apply(StreamLifecycle.onEnd(pipeline.phase))
                is ServerMessage.StreamClear -> pipeline.apply(StreamLifecycle.onClear(pipeline.phase))
                else -> logger.d { "Ignoring ${message::class.simpleName}" }
            }
        }

        override fun onAudio(chunk: AudioChunk) = pipeline.onAudio(chunk)

        /** Per attempt: clock probes (also liveness), state reports, and the clock-quality watch. */
        suspend fun companion(scope: CoroutineScope, session: NoiseSession) {
            scope.launch { ClockProbe(clockSync, deps.clock) { session.send(it) }.run() }
            scope.launch { reportState(session) }
            scope.launch { watchClockQuality() }
        }

        private suspend fun reportState(session: NoiseSession) = when (session.wire) {
            WireVersion.LEGACY_9 -> reportLegacyState(session)
            WireVersion.SPEC_1_0 -> reportSpecState(session)
        }

        /** 9.x wire: `synchronized` once after activation, then every 2 s while playing. */
        private suspend fun reportLegacyState(session: NoiseSession) {
            val json = WireCodec.encode(
                ClientStateMessage(
                    payload = ClientStatePayload(
                        PlayerStateObject.legacy(PlayerStateValue.SYNCHRONIZED),
                        available = true,
                    ),
                ),
            )
            session.send(json) // first send waits for activation
            while (true) {
                delay(STATE_REPORT_MILLIS)
                if (pipeline.phase == StreamPhase.Playing) session.send(json)
            }
        }

        /**
         * Spec 1.0.0-rc1: `available` only once the time filter has converged (the
         * server starts no stream before it), then again on every change. The reported
         * delay is informational for the server's send-ahead; the scheduler still applies
         * the signed [LocalPlayerConfig.userDelayMs] itself.
         */
        private suspend fun reportSpecState(session: NoiseSession) {
            while (!clockSync.isConverged) delay(CLOCK_POLL_MILLIS)
            config.filterNotNull().map { it.userDelayMs }.distinctUntilChanged().collect { delayMs ->
                session.send(
                    WireCodec.encode(
                        ClientStateMessage(
                            payload = ClientStatePayload(
                                player = PlayerStateObject.spec(
                                    outputDelayMs = delayMs.coerceIn(0, MAX_OUTPUT_DELAY_MS),
                                    requiredLeadTimeMs = REQUIRED_LEAD_TIME_MS,
                                    minBufferMs = MIN_BUFFER_MS,
                                ),
                                available = true,
                            ),
                        ),
                    ),
                )
            }
        }

        private suspend fun watchClockQuality() {
            var degradedSince: Long? = null
            while (true) {
                delay(STATE_REPORT_MILLIS)
                val now = deps.clock.nowMicros()
                if (clockSync.quality(now) == ClockQuality.Degraded) {
                    val since = degradedSince ?: now.also { degradedSince = it }
                    if (now - since >= CLOCK_UNSTABLE_MICROS) {
                        _events.tryEmit(PlayerEvent.Warning(WarningCode.ClockUnstable))
                        degradedSince = Long.MAX_VALUE / 2 // report once per streak
                    }
                } else {
                    degradedSince = null
                }
            }
        }

        suspend fun publishState() {
            combine(supervisor.state, pipeline.status) { connection, audio ->
                when (connection) {
                    ConnectionState.Idle -> PlayerState.Connecting(0)
                    is ConnectionState.Connecting -> PlayerState.Connecting(connection.attempt)
                    is ConnectionState.Active -> PlayerState.Connected(playerId, serverName, clockSync.quality(), audio)
                    is ConnectionState.Backoff -> PlayerState.Reconnecting(
                        connection.attempt,
                        connection.retryAtMicros / 1_000,
                        audio,
                    )
                    is ConnectionState.WaitingForNetwork -> PlayerState.Reconnecting(connection.attempt, null, audio)
                    is ConnectionState.Failed -> PlayerState.Failed(connection.cause)
                }
            }.collect { _state.value = it }
        }

        suspend fun forwardAudioEvents() {
            pipeline.events.collect { event ->
                val mapped = when (event) {
                    AudioEvent.Started -> PlayerEvent.PlaybackStarted(playerId)
                    AudioEvent.Ended -> PlayerEvent.PlaybackStopped(StopCause.ServerEnded)
                    AudioEvent.Cleared -> PlayerEvent.PlaybackStopped(StopCause.Cleared)
                    AudioEvent.FocusLost -> PlayerEvent.PlaybackStopped(StopCause.FocusLost)
                    AudioEvent.FocusRegained -> PlayerEvent.FocusRegained
                    AudioEvent.SinkDied -> PlayerEvent.PlaybackStopped(StopCause.SinkFailed)
                    is AudioEvent.DecoderFailed -> PlayerEvent.Warning(WarningCode.DecoderFailed(codecOf(event.codec)))
                    is AudioEvent.UnsupportedFormat -> PlayerEvent.Warning(WarningCode.UnsupportedFormat(event.codec))
                }
                _events.tryEmit(mapped)
            }
        }

        /** Starved while playing with the connection down is a real outage: stop, and say so. */
        suspend fun watchStarvation() {
            combine(pipeline.status, supervisor.state) { audio, connection ->
                audio.starved && audio.phase == AudioPhase.Playing && connection !is ConnectionState.Active
            }.distinctUntilChanged().collect { outage ->
                if (outage) {
                    logger.w { "Buffer ran dry while disconnected: stopping playback" }
                    pipeline.apply(StreamAction.Abort)
                    _events.tryEmit(PlayerEvent.PlaybackStopped(StopCause.Starved))
                }
            }
        }

        private fun codecOf(name: String): AudioCodec =
            AudioCodec.entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: AudioCodec.PCM
    }

    internal companion object {
        const val STATE_REPORT_MILLIS = 2_000L
        const val CLOCK_UNSTABLE_MICROS = 60_000_000L
        const val CLOCK_POLL_MILLIS = 100L

        /** Spec range of `output_delay_ms`. */
        const val MAX_OUTPUT_DELAY_MS = 5_000

        /** Startup lead the server grants a buffered source before the first chunk is due. */
        const val REQUIRED_LEAD_TIME_MS = 250

        /** Ongoing buffer the server keeps queued; absorbs Wi-Fi and WebRTC jitter. */
        const val MIN_BUFFER_MS = 1_000
    }
}
