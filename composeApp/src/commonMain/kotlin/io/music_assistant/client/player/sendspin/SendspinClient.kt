package io.music_assistant.client.player.sendspin

import co.touchlab.kermit.Logger
import io.music_assistant.client.player.MediaPlayerController
import io.music_assistant.client.player.sendspin.audio.AudioPipeline
import io.music_assistant.client.player.sendspin.audio.AudioStreamManager
import io.music_assistant.client.player.sendspin.connection.SendspinWsHandler
import io.music_assistant.client.player.sendspin.model.CommandValue
import io.music_assistant.client.player.sendspin.model.PlayerStateObject
import io.music_assistant.client.player.sendspin.model.PlayerStateValue
import io.music_assistant.client.player.sendspin.model.ServerCommandMessage
import io.music_assistant.client.player.sendspin.model.StreamMetadataPayload
import io.music_assistant.client.player.sendspin.protocol.MessageDispatcher
import io.music_assistant.client.player.sendspin.protocol.MessageDispatcherConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

class SendspinClient(
    private val config: SendspinConfig,
    private val mediaPlayerController: MediaPlayerController
) : CoroutineScope {

    private val logger = Logger.withTag("SendspinClient")
    private val supervisorJob = SupervisorJob()

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Default + supervisorJob

    // Components
    private var sendspinWsHandler: SendspinWsHandler? = null
    private var messageDispatcher: MessageDispatcher? = null
    private var stateReporter: StateReporter? = null
    private var reconnectionCoordinator: ReconnectionCoordinator? = null
    private val clockSynchronizer = ClockSynchronizer()
    private val audioPipeline: AudioPipeline = AudioStreamManager(clockSynchronizer, mediaPlayerController)

    // State flows
    private val _connectionState =
        MutableStateFlow<SendspinConnectionState>(SendspinConnectionState.Idle)
    val connectionState: StateFlow<SendspinConnectionState> = _connectionState.asStateFlow()

    private val _playbackState = MutableStateFlow<SendspinPlaybackState>(SendspinPlaybackState.Idle)

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
            logger.w { "Sendspin config invalid: enabled=${config.enabled}, host=${config.serverHost}, device=${config.deviceName}" }
            return
        }

        logger.i { "Starting Sendspin client: ${config.deviceName}" }

        try {
            val serverUrl = config.buildServerUrl()
            connectToServer(serverUrl)

        } catch (e: Exception) {
            logger.e(e) { "Failed to start Sendspin client" }
            _connectionState.update {
                SendspinConnectionState.Error(
                    SendspinError.Permanent(
                        cause = e,
                        userAction = "Check Sendspin settings and server connection"
                    )
                )
            }
        }
    }

    private suspend fun connectToServer(serverUrl: String) {
        logger.i { "Connecting to Sendspin server: $serverUrl" }

        try {
            // Clean up existing connection
            disconnectFromServer()

            // Update current volume from system right before connecting
            // (in case it changed since construction)
            currentVolume = mediaPlayerController.getCurrentSystemVolume()
            logger.i { "Initializing with system volume: $currentVolume%" }

            // Create WebSocket handler
            val wsHandler = SendspinWsHandler(serverUrl)
            sendspinWsHandler = wsHandler

            // Create message dispatcher
            val capabilities = SendspinCapabilities.buildClientHello(config, config.codecPreference)
            val dispatcherConfig = MessageDispatcherConfig(
                clientCapabilities = capabilities,
                initialVolume = currentVolume,
                authToken = config.authToken,
                requiresAuth = config.requiresAuth
            )
            val dispatcher = MessageDispatcher(
                sendspinWsHandler = wsHandler,
                clockSynchronizer = clockSynchronizer,
                config = dispatcherConfig
            )
            messageDispatcher = dispatcher

            // Create state reporter
            val reporter = StateReporter(
                messageDispatcher = dispatcher,
                volumeProvider = { currentVolume },
                mutedProvider = { currentMuted },
                playbackStateProvider = { _playbackState.value }
            )
            stateReporter = reporter

            // Create reconnection coordinator
            val coordinator = ReconnectionCoordinator(
                wsHandler = wsHandler,
                audioPipeline = audioPipeline,
                playbackStateProvider = { _playbackState.value }
            )
            reconnectionCoordinator = coordinator

            // Connect WebSocket
            wsHandler.connect()

            // Start message dispatcher
            dispatcher.start()

            // Send auth (proxy mode) or hello (direct mode)
            if (config.requiresAuth) {
                logger.i { "Using proxy mode - sending auth first" }
                dispatcher.sendAuth()
            } else {
                logger.i { "Using direct mode - sending hello" }
                dispatcher.sendHello()
            }

            // Start reconnection coordinator (monitors WebSocket state for recovery)
            coordinator.start()

            // Monitor WebSocket connection state (for connection status updates)
            monitorWebSocketConnectionState()

            // Monitor protocol state
            monitorProtocolState()

            // Monitor stream events
            monitorStreamEvents()

            // Monitor binary messages for audio
            monitorBinaryMessages()

            // Monitor server commands
            monitorServerCommands()

        } catch (e: Exception) {
            logger.e(e) { "Failed to connect to server" }
            _connectionState.update {
                SendspinConnectionState.Error(
                    SendspinError.Permanent(
                        cause = e,
                        userAction = "Verify server is running and accessible"
                    )
                )
            }
        }
    }

    /**
     * Monitor WebSocket state for connection status updates.
     * ReconnectionCoordinator handles recovery logic; this just updates connection state.
     */
    private fun monitorWebSocketConnectionState() {
        launch {
            sendspinWsHandler?.connectionState?.collect { wsState ->
                logger.d { "WebSocket state: $wsState" }
                when (wsState) {
                    is WebSocketState.Reconnecting -> {
                        _connectionState.update {
                            SendspinConnectionState.Error(
                                SendspinError.Transient(
                                    cause = Exception("Network reconnection in progress (attempt ${wsState.attempt})"),
                                    willRetry = true
                                )
                            )
                        }
                    }

                    is WebSocketState.Error -> {
                        logger.e { "WebSocket error: ${wsState.error.message}" }
                        val isPermanent = wsState.error.message?.contains("Failed to reconnect") == true

                        _connectionState.update {
                            if (isPermanent) {
                                SendspinConnectionState.Error(
                                    SendspinError.Permanent(
                                        cause = wsState.error,
                                        userAction = "Check network connection and server availability"
                                    )
                                )
                            } else {
                                SendspinConnectionState.Error(
                                    SendspinError.Transient(
                                        cause = wsState.error,
                                        willRetry = false
                                    )
                                )
                            }
                        }
                    }

                    WebSocketState.Disconnected -> {
                        logger.i { "WebSocket disconnected" }
                        _connectionState.update { SendspinConnectionState.Idle }
                    }

                    WebSocketState.Connecting,
                    WebSocketState.Connected -> {
                        // Protocol state will update connection state when ready
                        logger.d { "WebSocket state: $wsState" }
                    }
                }
            }
        }
    }

    private fun monitorProtocolState() {
        launch {
            messageDispatcher?.protocolState?.collect { state ->
                Logger.withTag("SendspinClient").e { "📡 PROTOCOL STATE: $state" }
                when (state) {
                    is ProtocolState.Ready -> {
                        val serverInfo = messageDispatcher?.serverInfo?.value
                        if (serverInfo != null) {
                            _connectionState.update {
                                SendspinConnectionState.Connected(
                                    serverId = serverInfo.serverId,
                                    serverName = serverInfo.name,
                                    connectionReason = serverInfo.connectionReason
                                )
                            }
                        }
                    }

                    is ProtocolState.Streaming -> {
                        _playbackState.update { SendspinPlaybackState.Buffering }
                    }

                    ProtocolState.Disconnected -> {
                        Logger.withTag("SendspinClient").e { "📡 PROTOCOL DISCONNECTED - setting connectionState to Idle" }
                        _connectionState.update { SendspinConnectionState.Idle }
                    }

                    else -> {}
                }
            }
        }
    }

    private fun monitorStreamEvents() {
        launch {
            messageDispatcher?.streamStartEvent?.collect { event ->
                Logger.withTag("SendspinClient").e { "🎵 STREAM START received" }
                event.payload.player?.let { playerConfig ->
                    audioPipeline.startStream(playerConfig)
                    _playbackState.update { SendspinPlaybackState.Buffering }
                    // Notify coordinator that stream started (for recovery tracking)
                    reconnectionCoordinator?.onStreamStarted()
                    // Start periodic state reporting
                    stateReporter?.start()
                }
            }
        }

        launch {
            messageDispatcher?.streamEndEvent?.collect {
                Logger.withTag("SendspinClient").e { "⛔ STREAM END received from server - stopping playback" }
                audioPipeline.stopStream()
                _playbackState.update { SendspinPlaybackState.Idle }
                // Notify coordinator that stream stopped
                reconnectionCoordinator?.onStreamStopped()
                // Stop periodic state reporting
                stateReporter?.stop()
                // Clear Now Playing from Control Center / Lock Screen
                // mediaPlayerController.clearNowPlaying() // DISABLED: Keep metadata visible so user can resume
            }
        }

        launch {
            messageDispatcher?.streamClearEvent?.collect {
                Logger.withTag("SendspinClient").e { "🗑️ STREAM CLEAR received from server" }
                audioPipeline.clearStream()
            }
        }

        // Monitor audio pipeline for errors (e.g., audio output disconnected)
        launch {
            audioPipeline.streamError.filterNotNull().collect { error ->
                logger.w(error) { "Audio pipeline error - stopping playback" }
                // Update playback state to Idle so UI reflects stopped state
                _playbackState.update { SendspinPlaybackState.Idle }
                // Stop periodic state reporting
                stateReporter?.stop()
                // Notify that playback stopped due to error (so MainDataSource can pause the MA server)
                _playbackStoppedDueToError.update { error }
                // Clear the error after handling
                delay(100)
                _playbackStoppedDueToError.update { null }
            }
        }
    }

    private fun monitorBinaryMessages() {
        launch {
            sendspinWsHandler?.binaryMessages?.collect { data ->
                audioPipeline.processBinaryMessage(data)

                // Update playback state based on sync quality
                if (clockSynchronizer.currentQuality == SyncQuality.GOOD) {
                    if (_playbackState.value != SendspinPlaybackState.Synchronized) {
                        _playbackState.update { SendspinPlaybackState.Synchronized }
                        stateReporter?.reportNow(PlayerStateValue.SYNCHRONIZED)
                    }
                }
            }
        }
    }

    private fun monitorServerCommands() {
        launch {
            messageDispatcher?.serverCommandEvent?.collect { command ->
                handleServerCommand(command)
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
        logger.i { "Stopping Sendspin client" }

        // Stop state reporting
        stateReporter?.stop()

        // Send goodbye if connected
        if (_connectionState.value is SendspinConnectionState.Connected) {
            try {
                messageDispatcher?.sendGoodbye("shutdown")
                delay(100) // Give it time to send
            } catch (e: Exception) {
                logger.e(e) { "Error sending goodbye" }
            }
        }

        disconnectFromServer()

        _connectionState.update { SendspinConnectionState.Idle }
        _playbackState.update { SendspinPlaybackState.Idle }
    }

    private suspend fun disconnectFromServer() {
        audioPipeline.stopStream()
        reconnectionCoordinator?.stop()
        reconnectionCoordinator = null
        stateReporter?.close()
        stateReporter = null
        messageDispatcher?.stop()
        messageDispatcher?.close()
        messageDispatcher = null

        sendspinWsHandler?.disconnect()
        sendspinWsHandler?.close()
        sendspinWsHandler = null

        clockSynchronizer.reset()
    }

    fun close() {
        logger.i { "Closing Sendspin client" }
        // Note: stop() should be called before close() to properly clean up connections
        // close() only performs synchronous cleanup
        audioPipeline.close()
        supervisorJob.cancel()
    }
}
