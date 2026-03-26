package io.music_assistant.client.player.sendspin

import co.touchlab.kermit.Logger
import io.music_assistant.client.player.MediaPlayerController
import io.music_assistant.client.player.sendspin.audio.AudioPipeline
import io.music_assistant.client.player.sendspin.audio.AudioStreamManager
import io.music_assistant.client.player.sendspin.model.CommandValue
import io.music_assistant.client.player.sendspin.model.PlayerStateValue
import io.music_assistant.client.player.sendspin.model.ServerCommandMessage
import io.music_assistant.client.player.sendspin.model.StreamMetadataPayload
import io.music_assistant.client.player.sendspin.protocol.MessageDispatcher
import io.music_assistant.client.player.sendspin.protocol.MessageDispatcherConfig
import io.music_assistant.client.player.sendspin.transport.SendspinTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

class SendspinClient(
    private val config: SendspinConfig,
    private val mediaPlayerController: MediaPlayerController,
    private val externalPipeline: AudioPipeline? = null,
    private val externalClockSynchronizer: ClockSynchronizer? = null,
    private val networkAvailable: StateFlow<Boolean>? = null
) : CoroutineScope {

    private val logger = Logger.withTag("SendspinClient")
    private val supervisorJob = SupervisorJob()

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Default + supervisorJob

    // If external pipeline/clock provided, use them; otherwise own them
    private val ownsAudioPipeline = externalPipeline == null
    private val clockSynchronizer = externalClockSynchronizer ?: ClockSynchronizer()
    private val audioPipeline: AudioPipeline =
        externalPipeline ?: AudioStreamManager(clockSynchronizer, mediaPlayerController)

    // Components
    private var transport: SendspinTransport? = null
    private var messageDispatcher: MessageDispatcher? = null
    private var stateReporter: StateReporter? = null

    // Unified state
    private val _state = MutableStateFlow<SendspinState>(SendspinState.Idle)
    val state: StateFlow<SendspinState> = _state.asStateFlow()

    // Exposed event for when playback stops due to error (e.g., audio output disconnected)
    // MainDataSource should monitor this to pause the MA server player
    private val _playbackStoppedDueToError = MutableStateFlow<Throwable?>(null)
    val playbackStoppedDueToError: StateFlow<Throwable?> = _playbackStoppedDueToError.asStateFlow()

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
                io.music_assistant.client.player.sendspin.transport.WebSocketSendspinTransport(
                    serverUrl,
                    networkAvailable
                )
            connectWithTransport(sendspinTransport)
        } catch (e: Exception) {
            logger.e(e) { "Failed to start Sendspin client" }
            _state.update {
                SendspinState.Error(
                    SendspinError.Permanent(
                        cause = e,
                        userAction = "Check Sendspin settings and server connection"
                    )
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

            // Store transport
            transport = sendspinTransport

            // Create message dispatcher
            val capabilities = SendspinCapabilities.buildClientHello(config, config.codecPreference)
            val dispatcherConfig = MessageDispatcherConfig(
                clientCapabilities = capabilities,
                initialVolume = currentVolume,
                authToken = config.authToken,
                requiresAuth = config.requiresAuth
            )
            val dispatcher = MessageDispatcher(
                transport = sendspinTransport,
                clockSynchronizer = clockSynchronizer,
                config = dispatcherConfig
            )
            messageDispatcher = dispatcher

            // Create state reporter (uses unified state)
            val reporter = StateReporter(
                messageDispatcher = dispatcher,
                volumeProvider = { currentVolume },
                mutedProvider = { currentMuted },
                stateProvider = { _state.value }
            )
            stateReporter = reporter

            // Mark as connecting
            _state.update { SendspinState.Connecting }

            // Connect transport
            sendspinTransport.connect()

            // Start message dispatcher (listens for text messages)
            dispatcher.start()

            // Run the unified state machine
            runStateMachine(sendspinTransport, dispatcher)

        } catch (e: Exception) {
            logger.e(e) { "Failed to connect to server" }
            _state.update {
                SendspinState.Error(
                    SendspinError.Permanent(
                        cause = e,
                        userAction = "Verify server is running and accessible"
                    )
                )
            }
        }
    }

    /**
     * Single state machine that replaces the five former monitor methods.
     *
     * Reacts to:
     *  - transport connection state changes
     *  - server/hello events from MessageDispatcher
     *  - stream start/end/clear events
     *  - binary audio messages
     *  - server commands
     *  - audio pipeline errors
     */
    private fun runStateMachine(
        sendspinTransport: SendspinTransport,
        dispatcher: MessageDispatcher
    ) {
        // --- Transport state ---
        launch {
            sendspinTransport.connectionState.collect { wsState ->
                when (wsState) {
                    WebSocketState.Connected -> {
                        when (_state.value) {
                            is SendspinState.Connecting, is SendspinState.Reconnecting -> {
                                try {
                                    if (config.requiresAuth) {
                                        _state.update { SendspinState.Authenticating }
                                        dispatcher.sendAuth()
                                    } else {
                                        _state.update { SendspinState.Handshaking }
                                        dispatcher.sendHello()
                                    }
                                } catch (e: Exception) {
                                    logger.w { "Failed to send auth/hello (transport closed during handshake): ${e.message}" }
                                }
                            }
                            else -> Unit
                        }
                    }

                    is WebSocketState.Reconnecting -> {
                        val current = _state.value
                        val wasStreaming = current is SendspinState.Buffering ||
                                current is SendspinState.Synchronized
                        // DON'T stop the pipeline — AudioPipeline keeps playing from buffer
                        _state.update { SendspinState.Reconnecting(wasStreaming, wsState.attempt) }
                    }

                    is WebSocketState.Error -> {
                        val isPermanent =
                            wsState.error.message?.contains("Failed to reconnect") == true

                        if (isPermanent) {
                            val current = _state.value
                            val wasStreaming = current is SendspinState.Reconnecting &&
                                    current.wasStreaming
                            if (wasStreaming) {
                                audioPipeline.stopStream()
                                stateReporter?.stop()
                            }
                            _state.update {
                                SendspinState.Error(
                                    SendspinError.Permanent(
                                        cause = wsState.error,
                                        userAction = "Check network connection and server availability"
                                    )
                                )
                            }
                        } else {
                            _state.update {
                                SendspinState.Error(
                                    SendspinError.Transient(
                                        cause = wsState.error,
                                        willRetry = false
                                    )
                                )
                            }
                        }
                    }

                    WebSocketState.Disconnected -> {
                        val current = _state.value
                        if (current !is SendspinState.Reconnecting) {
                            _state.update { SendspinState.Idle }
                        }
                    }

                    WebSocketState.Connecting -> Unit
                }
            }
        }

        // --- server/hello event → Ready ---
        launch {
            dispatcher.serverHelloEvent.collect { payload ->
                val current = _state.value
                if (current is SendspinState.Handshaking ||
                    current is SendspinState.Authenticating
                ) {
                    logger.i { "server/hello received — transitioning to Ready" }
                    _state.update {
                        SendspinState.Ready(
                            serverId = payload.serverId,
                            serverName = payload.name
                        )
                    }
                } else {
                    logger.d { "server/hello received but state is $current — ignoring" }
                }
            }
        }

        // --- Stream events ---
        launch {
            dispatcher.streamStartEvent.collect { event ->
                event.payload.player?.let { playerConfig ->
                    audioPipeline.startStream(playerConfig)
                    _state.update { SendspinState.Buffering }
                    // Start periodic state reporting
                    stateReporter?.start()
                }
            }
        }

        launch {
            dispatcher.streamEndEvent.collect {
                val current = _state.value
                audioPipeline.stopStream()
                if (current is SendspinState.Buffering || current is SendspinState.Synchronized) {
                    val serverInfo = messageDispatcher?.serverInfo?.value
                    val nextState = if (serverInfo != null) {
                        SendspinState.Ready(serverInfo.serverId, serverInfo.name)
                    } else {
                        SendspinState.Idle
                    }
                    _state.update { nextState }
                }
                // Stop periodic state reporting
                stateReporter?.stop()
            }
        }

        launch {
            dispatcher.streamClearEvent.collect {
                audioPipeline.clearStream()
            }
        }

        // --- Binary audio messages ---
        launch {
            sendspinTransport.binaryMessages.collect { data ->
                audioPipeline.processBinaryMessage(data)

                // Update playback state based on sync quality
                if (clockSynchronizer.currentQuality == SyncQuality.GOOD) {
                    if (_state.value is SendspinState.Buffering) {
                        _state.update { SendspinState.Synchronized }
                        stateReporter?.reportNow(PlayerStateValue.SYNCHRONIZED)
                    }
                }
            }
        }

        // --- Server commands ---
        launch {
            dispatcher.serverCommandEvent.collect { command ->
                handleServerCommand(command)
            }
        }

        // --- Audio pipeline errors ---
        launch {
            audioPipeline.streamError.collect { error ->
                val current = _state.value
                val serverInfo = messageDispatcher?.serverInfo?.value
                val nextState = if (serverInfo != null) {
                    SendspinState.Ready(serverInfo.serverId, serverInfo.name)
                } else {
                    SendspinState.Idle
                }
                logger.w(error) { "PIPELINE ERROR: ${error.message}, currentState=$current, nextState=$nextState" }
                _state.update { nextState }
                stateReporter?.stop()
                // Notify that playback stopped due to error
                _playbackStoppedDueToError.update { error }
                delay(100)
                _playbackStoppedDueToError.update { null }
            }
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

    suspend fun stop() {
        val current = _state.value
        // Stop state reporting
        stateReporter?.stop()

        // Send goodbye if connected
        if (current is SendspinState.Ready ||
            current is SendspinState.Buffering ||
            current is SendspinState.Synchronized
        ) {
            try {
                messageDispatcher?.sendGoodbye("shutdown")
                delay(100) // Give it time to send
            } catch (e: Exception) {
                logger.e(e) { "Error sending goodbye" }
            }
        }

        disconnectFromServer()
        _state.update { SendspinState.Idle }
    }

    private suspend fun disconnectFromServer() {
        // Only stop the audio pipeline if we own it.
        // External (shared) pipelines keep playing from their buffer during reconnection.
        if (ownsAudioPipeline) {
            audioPipeline.stopStream()
        }
        stateReporter?.close()
        stateReporter = null
        messageDispatcher?.stop()
        messageDispatcher?.close()
        messageDispatcher = null

        transport?.disconnect()
        transport?.close()
        transport = null

        // Only reset clock sync if we own it.
        if (ownsAudioPipeline) {
            clockSynchronizer.reset()
        }
    }

    fun close() {
        logger.i { "Closing Sendspin client" }
        if (ownsAudioPipeline) {
            audioPipeline.close()
        }
        supervisorJob.cancel()
    }
}
