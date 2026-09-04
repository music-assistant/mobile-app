package io.music_assistant.client.player.sendspin

import co.touchlab.kermit.Logger
import io.music_assistant.client.player.MediaPlayerController
import io.music_assistant.client.player.sendspin.audio.AudioPipeline
import io.music_assistant.client.player.sendspin.identity.SendspinTrustStore
import io.music_assistant.client.player.sendspin.model.ClientAuthMessage
import io.music_assistant.client.player.sendspin.model.ClientHelloMessage
import io.music_assistant.client.player.sendspin.model.CommandValue
import io.music_assistant.client.player.sendspin.model.EncryptedDeviceInfo
import io.music_assistant.client.player.sendspin.model.GoodbyeReason
import io.music_assistant.client.player.sendspin.model.PlayerStateValue
import io.music_assistant.client.player.sendspin.model.ServerCommandMessage
import io.music_assistant.client.player.sendspin.model.StreamMetadataPayload
import io.music_assistant.client.player.sendspin.noise.crypto.NoiseCrypto
import io.music_assistant.client.player.sendspin.protocol.MessageDispatcher
import io.music_assistant.client.player.sendspin.protocol.StreamLifecycleEvent
import io.music_assistant.client.player.sendspin.session.EncryptedSession
import io.music_assistant.client.player.sendspin.session.EncryptedSessionConfig
import io.music_assistant.client.player.sendspin.session.LegacySession
import io.music_assistant.client.player.sendspin.session.LegacySessionConfig
import io.music_assistant.client.player.sendspin.session.SendspinProtocolSession
import io.music_assistant.client.player.sendspin.session.SessionEvent
import io.music_assistant.client.player.sendspin.session.SessionOutcome
import io.music_assistant.client.player.sendspin.transport.SendspinTransport
import io.music_assistant.client.player.sendspin.transport.WebSocketSendspinTransport
import io.music_assistant.client.utils.myJson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.CoroutineContext

class SendspinClient(
    private val config: SendspinConfig,
    private val mediaPlayerController: MediaPlayerController,
    private val audioPipeline: AudioPipeline,
    private val clockSynchronizer: ClockSynchronizer,
    private val networkAvailable: StateFlow<Boolean>? = null,
    // Encrypted connections only.
    private val trustStore: SendspinTrustStore? = null,
    private val noiseCrypto: NoiseCrypto? = null,
) : CoroutineScope {
    private val logger = Logger.withTag("SendspinClient")
    private val supervisorJob = SupervisorJob()

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Default + supervisorJob

    companion object {
        /** Attempt 9 ≈ 4 minutes of backoff; longer outages skip auto-resume. */
        private const val RECONNECT_AUTO_RESUME_MAX_ATTEMPTS = 9

        private const val GOODBYE_TIMEOUT_MILLIS = 1_000L
        private const val GOODBYE_FLUSH_MILLIS = 100L
    }

    private var session: SendspinProtocolSession? = null
    private val stateMachineJobs = mutableListOf<Job>()
    private var messageDispatcher: MessageDispatcher? = null
    private var stateReporter: StateReporter? = null

    private var readyOnActivate = false
    private var terminalTransport = false

    /** Invoked for every session event, in order, before internal handling. */
    var sessionEventListener: ((SessionEvent) -> Unit)? = null

    private var lastServerId: String? = null
    private var lastServerName: String? = null

    // Captured while the state still says Reconnecting; consumed at post-reconnect readiness.
    private var pendingResumeWasStreaming = false
    private var pendingResumeAttempt = 0

    private val _state = MutableStateFlow<SendspinState>(SendspinState.Idle)
    val state: StateFlow<SendspinState> = _state.asStateFlow()

    // Exposed event for when playback stops due to error (e.g., audio output disconnected)
    // MainDataSource should monitor this to pause the MA server player
    private val _playbackStoppedDueToError = MutableStateFlow<Throwable?>(null)
    val playbackStoppedDueToError: StateFlow<Throwable?> = _playbackStoppedDueToError.asStateFlow()

    // Reactive buffer-starvation state from the pipeline. The owner composes this with transport
    // and play state to decide on teardown — see LocalPlayerController.
    val isStarved: StateFlow<Boolean> get() = audioPipeline.isStarved

    // Reactive buffer fill (µs of audio queued ahead of the playhead) for the UI's
    // buffered-progress indicator. Local player only — remote players expose no buffer.
    val bufferState: StateFlow<BufferState> get() = audioPipeline.bufferState

    /** Stop the audio stream (release the sink), leaving the client/transport intact. */
    suspend fun stopStream() = audioPipeline.stopStream()

    // Track current volume/mute state
    // Initialize with current system volume (not hardcoded 100)
    private var currentVolume: Int = mediaPlayerController.getCurrentSystemVolume()
    private var currentMuted: Boolean = false

    val metadata: StateFlow<StreamMetadataPayload?>
        get() = messageDispatcher?.streamMetadata ?: MutableStateFlow(null)

    suspend fun start() {
        if (!config.isValid) {
            logger.w { "Sendspin config invalid: enabled=${config.enabled}" }
            return
        }

        logger.i { "Starting Sendspin client: ${config.deviceName}" }

        try {
            val serverUrl = config.buildServerUrl()
            val sendspinTransport =
                WebSocketSendspinTransport(
                    serverUrl,
                    networkAvailable,
                )
            connectWithTransport(sendspinTransport)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.e(e) { "Failed to start Sendspin client" }
            _state.update {
                SendspinState.Error(
                    SendspinError.Permanent(
                        cause = e,
                        userAction = "Check Sendspin settings and server connection",
                    ),
                )
            }
        }
    }

    suspend fun connectWithTransport(sendspinTransport: SendspinTransport) {
        logger.i { "Connecting to Sendspin with transport" }

        try {
            // Clean up existing connection
            disconnectFromServer()

            // Update current volume from system right before connecting
            // (in case it changed since construction)
            currentVolume = mediaPlayerController.getCurrentSystemVolume()
            logger.i { "Initializing with system volume: $currentVolume%" }

            terminalTransport = sendspinTransport.isSingleUse
            val protocolSession = createSession(sendspinTransport)
            session = protocolSession

            val dispatcher = MessageDispatcher(
                inbound = protocolSession.applicationMessages,
                sender = protocolSession.sender,
                clockSynchronizer = clockSynchronizer,
                deferServerHelloSideEffects = readyOnActivate,
            )
            messageDispatcher = dispatcher

            // Create state reporter (uses unified state)
            val reporter = StateReporter(
                messageDispatcher = dispatcher,
                stateProvider = { _state.value },
            )
            stateReporter = reporter

            // Mark as connecting
            _state.update { SendspinState.Connecting }

            // Collectors first, so the session's earliest events aren't missed.
            runStateMachine(protocolSession, dispatcher)
            dispatcher.start()
            protocolSession.start()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.e(e) { "Failed to connect to server" }
            _state.update {
                SendspinState.Error(
                    SendspinError.Permanent(
                        cause = e,
                        userAction = "Verify server is running and accessible",
                    ),
                )
            }
        }
    }

    private fun createSession(transport: SendspinTransport): SendspinProtocolSession {
        val capabilities = SendspinCapabilities.buildClientHello(config, config.codecPreference)
        val authJson = config.authToken?.let { token ->
            myJson.encodeToString(ClientAuthMessage(token = token, clientId = config.clientId))
        }

        val store = trustStore
        val crypto = noiseCrypto
        if (config.encryptionMode == SendspinEncryptionMode.ENCRYPTED &&
            store != null && crypto != null
        ) {
            readyOnActivate = true
            val legacyDeviceInfo = capabilities.deviceInfo
            return EncryptedSession(
                transport = transport,
                config = EncryptedSessionConfig(
                    requiresAuth = config.requiresAuth,
                    authJson = authJson,
                    deviceName = config.deviceName,
                    supportedRoles = capabilities.supportedRoles,
                    playerSupport = capabilities.playerV1Support,
                    deviceInfo = legacyDeviceInfo?.let {
                        EncryptedDeviceInfo(
                            productName = it.model,
                            manufacturer = it.manufacturer,
                            softwareVersion = it.softwareVersion,
                        )
                    },
                ),
                crypto = crypto,
                trustStore = store,
            )
        }

        readyOnActivate = false
        return LegacySession(
            transport = transport,
            config = LegacySessionConfig(
                requiresAuth = config.requiresAuth,
                authJson = authJson,
                helloJson = myJson.encodeToString(ClientHelloMessage(payload = capabilities)),
            ),
        )
    }

    private fun runStateMachine(
        protocolSession: SendspinProtocolSession,
        dispatcher: MessageDispatcher,
    ) {
        // --- Session lifecycle ---
        stateMachineJobs += launch {
            protocolSession.events.collect { event ->
                handleSessionEvent(event, dispatcher)
            }
        }

        // --- Stream lifecycle (coalesced) ---
        // collectLatest: a rapid skip burst cancels intermediate stream setups, so only the
        // final track materializes; start/end/clear ordering holds because they share one flow.
        stateMachineJobs += launch {
            dispatcher.streamLifecycleEvent.collectLatest { event ->
                handleStreamLifecycle(event)
            }
        }

        // --- Demuxed audio frames ---
        stateMachineJobs += launch {
            protocolSession.audioFrames.collect { data ->
                audioPipeline.processBinaryMessage(data)

                // Update playback state based on sync quality
                if (clockSynchronizer.currentQuality == SyncQuality.GOOD) {
                    if (_state.value is SendspinState.Buffering) {
                        val stats = clockSynchronizer.getStats()
                        logger.i { "Playback synchronized (offset=${stats.offsetMs}ms, rtt=${stats.rttMs}ms)" }
                        _state.update { SendspinState.Synchronized }
                        stateReporter?.reportNow(PlayerStateValue.SYNCHRONIZED)
                    }
                }
            }
        }

        // --- Server commands ---
        stateMachineJobs += launch {
            dispatcher.serverCommandEvent.collect { command ->
                handleServerCommand(command)
            }
        }

        // --- Audio pipeline errors ---
        stateMachineJobs += launch {
            audioPipeline.streamError.collect { error ->
                val current = _state.value
                val nextState = readyStateOrIdle()
                logger.w(error) {
                    "PIPELINE ERROR: ${error.message}, " +
                            "currentState=${current::class.simpleName}, " +
                            "nextState=${nextState::class.simpleName}"
                }
                _state.update { nextState }
                stateReporter?.stop()
                // Notify that playback stopped due to error
                _playbackStoppedDueToError.update { error }
                delay(100)
                _playbackStoppedDueToError.update { null }
            }
        }
    }

    /**
     * Completes with the first ProtocolReady or terminal failure of the
     * current session's initial attach (bounded by the handshake timeout).
     */
    suspend fun awaitInitialOutcome(): SessionOutcome =
        session?.awaitInitialOutcome()
            ?: SessionOutcome.Failed(IllegalStateException("no active session"))

    private suspend fun handleSessionEvent(event: SessionEvent, dispatcher: MessageDispatcher) {
        sessionEventListener?.invoke(event)
        when (event) {
            is SessionEvent.Negotiating -> {
                val reconnecting = _state.value as? SendspinState.Reconnecting
                pendingResumeWasStreaming = reconnecting?.wasStreaming ?: false
                pendingResumeAttempt = reconnecting?.attempt ?: 0

                when (_state.value) {
                    is SendspinState.Connecting,
                    is SendspinState.Reconnecting,
                    is SendspinState.Error,
                    -> _state.update {
                        if (event.authenticating) {
                            SendspinState.Authenticating
                        } else {
                            SendspinState.Handshaking
                        }
                    }

                    else -> Unit
                }
            }

            is SessionEvent.ProtocolReady -> {
                logger.i { "Protocol ready (server=${event.serverName}, trust=${event.trustLevel})" }
                lastServerId = event.serverId
                lastServerName = event.serverName
                if (!readyOnActivate) {
                    transitionToReady()
                    if (event.isReconnectEpoch) maybeAutoResume()
                }
            }

            is SessionEvent.Activated -> {
                logger.i { "Activated: activities=${event.activities} roles=${event.activeRoles}" }
                // Stay quiet through a pairing activation: any non-pairing client
                // message would abort the attempt server-side.
                val pairingActivity = event.activities.contains("pairing")
                if (readyOnActivate && !pairingActivity) {
                    dispatcher.startActivatedReporting()
                    transitionToReady()
                    if (event.isReconnectEpoch) maybeAutoResume()
                }
            }

            is SessionEvent.Reconnecting -> {
                val current = _state.value
                val wasStreaming = current is SendspinState.Buffering ||
                        current is SendspinState.Synchronized ||
                        (current as? SendspinState.Reconnecting)?.wasStreaming == true
                // DON'T stop the pipeline — AudioPipeline keeps playing from buffer
                _state.update { SendspinState.Reconnecting(wasStreaming, event.attempt) }
            }

            SessionEvent.Disconnected -> {
                val current = _state.value
                when {
                    // A single-use transport never opens a second epoch, so a
                    // disconnect IS exhaustion — Idle would strand the player with
                    // nobody to renegotiate the channel.
                    terminalTransport -> enterChannelExhausted()
                    current !is SendspinState.Reconnecting -> _state.update { SendspinState.Idle }
                }
            }

            SessionEvent.RehandshakeCompleted -> {
                logger.i { "Session re-handshake completed" }
            }

            is SessionEvent.Failed -> {
                if (terminalTransport) {
                    logger.w(event.cause) { "Session failed on terminal transport — channel exhausted" }
                    enterChannelExhausted()
                } else if (event.permanent) {
                    // Keep the pipeline draining its buffer; a quick reconnect resumes into it.
                    stateReporter?.stop()
                    _state.update {
                        SendspinState.Error(
                            SendspinError.Permanent(
                                cause = event.cause,
                                userAction = "Check network connection and server availability",
                            ),
                        )
                    }
                } else {
                    _state.update {
                        SendspinState.Error(
                            SendspinError.Transient(
                                cause = event.cause,
                                willRetry = false,
                            ),
                        )
                    }
                }
            }
        }
    }

    /**
     * The single-use transport is spent: surface it as a permanent error so
     * [io.music_assistant.client.data.LocalPlayerController] negotiates a fresh channel.
     */
    private fun enterChannelExhausted() {
        stateReporter?.stop()
        _state.update {
            SendspinState.Error(
                SendspinError.Permanent(
                    cause = WebRTCSendspinChannelExhausted(),
                    userAction = "Reconnecting remote channel",
                ),
            )
        }
    }

    private fun transitionToReady() {
        val serverId = lastServerId ?: return
        val serverName = lastServerName ?: return
        when (_state.value) {
            is SendspinState.Connecting,
            is SendspinState.Authenticating,
            is SendspinState.Handshaking,
            is SendspinState.Reconnecting,
            is SendspinState.Error,
            -> {
                logger.i { "Protocol established — transitioning to Ready" }
                _state.update { SendspinState.Ready(serverId, serverName) }
            }

            else -> logger.d { "Ready signal in state ${_state.value} — ignoring" }
        }
    }

    private suspend fun maybeAutoResume() {
        val wasStreaming = pendingResumeWasStreaming
        val attempt = pendingResumeAttempt
        pendingResumeWasStreaming = false
        pendingResumeAttempt = 0
        if (!wasStreaming) return
        if (attempt < RECONNECT_AUTO_RESUME_MAX_ATTEMPTS) {
            try {
                mediaPlayerController.resume()
                logger.i { "Auto-resumed playback after reconnect (attempt $attempt)" }
            } catch (e: Exception) {
                logger.w(e) { "Auto-resume failed" }
            }
        } else {
            logger.i {
                "Skipped auto-resume after $attempt attempts (max=$RECONNECT_AUTO_RESUME_MAX_ATTEMPTS)"
            }
        }
    }

    private fun readyStateOrIdle(): SendspinState {
        val serverId = lastServerId
        val serverName = lastServerName
        return if (serverId != null && serverName != null) {
            SendspinState.Ready(serverId, serverName)
        } else {
            SendspinState.Idle
        }
    }

    // Dropping an intermediate End/Clear under collectLatest is safe: the next Start
    // fully re-initializes decoder, sink, queue and consumer.
    private suspend fun handleStreamLifecycle(event: StreamLifecycleEvent) {
        when (event) {
            is StreamLifecycleEvent.Start -> event.message.payload.player?.let { playerConfig ->
                audioPipeline.startStream(playerConfig)
                _state.update { SendspinState.Buffering }
                stateReporter?.start()
            }

            StreamLifecycleEvent.End -> {
                val current = _state.value
                audioPipeline.stopStream()
                if (current is SendspinState.Buffering || current is SendspinState.Synchronized) {
                    _state.update { readyStateOrIdle() }
                }
                stateReporter?.stop()
            }

            StreamLifecycleEvent.Clear -> audioPipeline.clearStream()
        }
    }

    private suspend fun handleServerCommand(command: ServerCommandMessage) {
        val playerCmd = command.payload.player
        logger.i { "Handling server command: ${playerCmd.command}" }

        when (playerCmd.command) {
            "volume" -> {
                playerCmd.volume?.let { volume ->
                    logger.i { "Setting volume to $volume" }
                    currentVolume = volume
                    mediaPlayerController.setVolume(volume)
                    stateReporter?.reportNow(PlayerStateValue.SYNCHRONIZED)
                }
            }

            "mute" -> {
                playerCmd.mute?.let { muted ->
                    logger.i { "Setting mute to $muted" }
                    currentMuted = muted
                    mediaPlayerController.setMuted(muted)
                    stateReporter?.reportNow(PlayerStateValue.SYNCHRONIZED)
                }
            }

            else -> {
                logger.w { "Unknown server command: ${playerCmd.command}" }
            }
        }
    }

    suspend fun sendCommand(command: String, value: CommandValue?) {
        messageDispatcher?.sendCommand(command, value)
    }

    suspend fun stop(reason: GoodbyeReason) {
        val current = _state.value
        stateReporter?.stop()

        if (current is SendspinState.Ready ||
            current is SendspinState.Buffering ||
            current is SendspinState.Synchronized
        ) {
            try {
                // Bounded: the encrypted outbound gate may be closed (re-handshake,
                // pairing) and teardown must not wedge behind it.
                withTimeoutOrNull(GOODBYE_TIMEOUT_MILLIS) {
                    messageDispatcher?.sendGoodbye(reason)
                    delay(GOODBYE_FLUSH_MILLIS)
                }
            } catch (e: Exception) {
                logger.e(e) { "Error sending goodbye" }
            }
        }

        disconnectFromServer()
        _state.update { SendspinState.Idle }
    }

    private suspend fun disconnectFromServer() {
        // Cancel the previous connection's collectors; the shared-flow ones
        // (commands, pipeline errors) never complete on their own.
        stateMachineJobs.forEach { it.cancel() }
        stateMachineJobs.clear()
        stateReporter?.close()
        stateReporter = null
        messageDispatcher?.stop()
        messageDispatcher?.close()
        messageDispatcher = null

        session?.stop()
        session?.close()
        session = null
        lastServerId = null
        lastServerName = null
    }

    fun close() {
        logger.i { "Closing Sendspin client" }
        // Defensive: callers pair stop()+close(), but close() alone must not
        // leak a live session driver and transport.
        session?.close()
        session = null
        supervisorJob.cancel()
    }
}
