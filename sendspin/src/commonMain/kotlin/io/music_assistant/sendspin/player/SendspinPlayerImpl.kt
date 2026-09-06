package io.music_assistant.sendspin.player

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.music_assistant.sendspin.api.AudioPhase
import io.music_assistant.sendspin.api.ClockQuality
import io.music_assistant.sendspin.api.Endpoint
import io.music_assistant.sendspin.api.LocalPlayerConfig
import io.music_assistant.sendspin.api.PlayerEvent
import io.music_assistant.sendspin.api.PlayerState
import io.music_assistant.sendspin.api.SendspinDeps
import io.music_assistant.sendspin.api.SendspinPlayer
import io.music_assistant.sendspin.api.StopCause
import io.music_assistant.sendspin.api.WarningCode
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
import io.music_assistant.sendspin.session.Activation
import io.music_assistant.sendspin.session.NoiseSession
import io.music_assistant.sendspin.session.SessionConfig
import io.music_assistant.sendspin.session.SessionHandler
import io.music_assistant.sendspin.session.SessionInfo
import io.music_assistant.sendspin.transport.TransportConnector
import io.music_assistant.sendspin.wire.AudioChunk
import io.music_assistant.sendspin.wire.AudioCodec
import io.music_assistant.sendspin.wire.AudioFormatSpec
import io.music_assistant.sendspin.wire.ClientStateMessage
import io.music_assistant.sendspin.wire.ClientStatePayload
import io.music_assistant.sendspin.wire.EncryptedDeviceInfo
import io.music_assistant.sendspin.wire.GoodbyeReason
import io.music_assistant.sendspin.wire.PlayerStateObject
import io.music_assistant.sendspin.wire.PlayerStateValue
import io.music_assistant.sendspin.wire.PlayerSupport
import io.music_assistant.sendspin.wire.ServerMessage
import io.music_assistant.sendspin.wire.WireCodec
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
 */
internal class SendspinPlayerImpl(
    private val config: StateFlow<LocalPlayerConfig?>,
    private val deps: SendspinDeps,
    scope: CoroutineScope,
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

    private data class ConnectionKey(val endpoint: Endpoint, val deviceName: String, val codecs: List<AudioCodec>)

    init {
        scope.launch {
            config.map { it != null }.distinctUntilChanged().collectLatest { enabled ->
                if (enabled) runEnabled()
            }
        }
    }

    private suspend fun runEnabled() = coroutineScope {
        val trustStore = SendspinTrustStore.load(deps.keyStore, deps.crypto)
        val clockSync = ClockSync(deps.clock)
        val pipeline = AudioPipeline(
            deps.sink,
            deps.decoders,
            clockSync,
            deps.clock,
            config.value?.bufferCapacityBytes ?: 0,
        )
        val connector = connectorFactory(deps.httpClient)
        val supervisor = ConnectionSupervisor(
            connector = connector,
            trustStore = trustStore,
            crypto = deps.crypto,
            online = deps.online,
            clock = deps.clock,
            pairWebPlayer = deps.pairWebPlayer,
        )
        val session = Session(pipeline, clockSync, trustStore, supervisor)
        launch(deps.audioDispatcher) { pipeline.run() }
        launch {
            config.filterNotNull().collect {
                pipeline.userDelayMicros = it.userDelayMs * 1_000L
                pipeline.capacityBytes = it.bufferCapacityBytes
            }
        }
        launch { session.publishState() }
        launch { session.forwardAudioEvents() }
        launch { session.watchStarvation() }
        try {
            config.filterNotNull()
                .map { ConnectionKey(it.endpoint, it.deviceName, it.codecPreference) }
                .distinctUntilChanged()
                .collectLatest { key ->
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
            deviceName = key.deviceName,
            playerSupport = PlayerSupport(
                supportedFormats = key.codecs.filter(deps.decoders::supports).flatMap { codec ->
                    SAMPLE_RATES.flatMap { rate -> BIT_DEPTHS.map { depth -> AudioFormatSpec(codec, 2, rate, depth) } }
                },
                bufferCapacity = config.value?.bufferCapacityBytes ?: 0,
                supportedCommands = emptyList(),
            ),
            deviceInfo = EncryptedDeviceInfo(productName = "Mobile Application", manufacturer = "Music Assistant"),
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

        /** Per attempt: clock probes (also liveness) and state reports. */
        suspend fun companion(scope: CoroutineScope, session: NoiseSession) {
            scope.launch { ClockProbe(clockSync, deps.clock) { session.send(it) }.run() }
            scope.launch { reportState(session) }
        }

        private suspend fun reportState(session: NoiseSession) {
            val json = WireCodec.encode(
                ClientStateMessage(
                    payload = ClientStatePayload(PlayerStateObject(PlayerStateValue.SYNCHRONIZED), available = true),
                ),
            )
            var degradedSince: Long? = null
            session.send(json) // first send waits for activation
            while (true) {
                delay(STATE_REPORT_MILLIS)
                if (pipeline.phase == StreamPhase.Playing) session.send(json)
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

    private companion object {
        val SAMPLE_RATES = listOf(44100, 48000, 88200, 96000, 192000)
        val BIT_DEPTHS = listOf(16, 24, 32)
        const val STATE_REPORT_MILLIS = 2_000L
        const val CLOCK_UNSTABLE_MICROS = 60_000_000L
    }
}
