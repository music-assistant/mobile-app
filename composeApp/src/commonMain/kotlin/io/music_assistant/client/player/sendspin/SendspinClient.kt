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

    // State reporting
    private var stateReportingJob: Job? = null

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
            _connectionState.update { SendspinConnectionState.Error(e) }
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

            // Monitor WebSocket connection state (for reconnection coordination)
            monitorWebSocketState()

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
            _connectionState.update { SendspinConnectionState.Error(e) }
        }
    }

    // Track streaming state for reconnection coordination
    private var wasStreamingBeforeDisconnect = false

    private fun monitorWebSocketState() {
        launch {
            sendspinWsHandler?.connectionState?.collect { wsState ->
                logger.d { "WebSocket state: $wsState" }
                when (wsState) {
                    WebSocketState.Connected -> {
                        // Check if we were streaming before disconnect
                        if (wasStreamingBeforeDisconnect) {
                            logger.i { "Reconnected while streaming was active - waiting for server to resume" }
                            // Server should auto-send StreamStart when player reconnects
                            // If it doesn't within 5 seconds, we'll timeout
                            launch {
                                delay(5000)
                                if (_playbackState.value == SendspinPlaybackState.Idle && wasStreamingBeforeDisconnect) {
                                    logger.w { "Stream restoration timed out - server didn't resume playback" }
                                    wasStreamingBeforeDisconnect = false
                                }
                            }
                        }
                    }

                    is WebSocketState.Reconnecting -> {
                        // Remember if we're streaming (don't stop playback!)
                        wasStreamingBeforeDisconnect = isCurrentlyStreaming()

                        if (wasStreamingBeforeDisconnect) {
                            Logger.withTag("SendspinClient").e { "🔄 WS RECONNECTING: attempt=${wsState.attempt}, playbackState=${_playbackState.value}, preserving buffer" }
                            // DON'T call stopStream()!
                            // Audio pipeline will keep playing from buffer
                            // Update connection state to show we're reconnecting
                            _connectionState.update {
                                SendspinConnectionState.Error(
                                    Exception("Reconnecting (attempt ${wsState.attempt})...")
                                )
                            }
                        } else {
                            Logger.withTag("SendspinClient").e { "🔄 WS RECONNECTING: attempt=${wsState.attempt}, NOT streaming, nothing to preserve" }
                        }
                    }

                    is WebSocketState.Error -> {
                        logger.e { "WebSocket error: ${wsState.error.message}" }
                        // Only stop if this is a permanent error (max reconnect attempts exceeded)
                        if (wsState.error.message?.contains("Failed to reconnect") == true) {
                            logger.e { "Connection failed permanently after max attempts" }
                            audioPipeline.stopStream()
                            _playbackState.update { SendspinPlaybackState.Idle }
                            wasStreamingBeforeDisconnect = false
                        }
                        _connectionState.update { SendspinConnectionState.Error(wsState.error) }
                    }

                    WebSocketState.Disconnected -> {
                        // Only handle if this is NOT during reconnection
                        if (!wasStreamingBeforeDisconnect) {
                            logger.i { "WebSocket disconnected (explicit)" }
                            _connectionState.update { SendspinConnectionState.Idle }
                        }
                    }

                    WebSocketState.Connecting -> {
                        logger.d { "WebSocket connecting..." }
                    }
                }
            }
        }
    }

    private fun isCurrentlyStreaming(): Boolean {
        val state = _playbackState.value
        return state is SendspinPlaybackState.Buffering ||
                state is SendspinPlaybackState.Synchronized ||
                state is SendspinPlaybackState.Playing
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
                    // Start periodic state reporting
                    startStateReporting()
                }
            }
        }

        launch {
            messageDispatcher?.streamEndEvent?.collect {
                Logger.withTag("SendspinClient").e { "⛔ STREAM END received from server - stopping playback" }
                audioPipeline.stopStream()
                _playbackState.update { SendspinPlaybackState.Idle }
                // Stop periodic state reporting
                stopStateReporting()
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
                stopStateReporting()
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
                        reportState(PlayerStateValue.SYNCHRONIZED)
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
                    reportState(PlayerStateValue.SYNCHRONIZED)
                }
            }

            "mute" -> {
                playerCmd.mute?.let { muted ->
                    logger.i { "Setting mute to $muted" }
                    currentMuted = muted
                    mediaPlayerController.setMuted(muted)
                    reportState(PlayerStateValue.SYNCHRONIZED)
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

    private suspend fun reportState(state: PlayerStateValue) {
        val playerState = PlayerStateObject(
            state = state,
            volume = currentVolume,
            muted = currentMuted
        )
        logger.d { "Reporting state: state=$state, volume=$currentVolume, muted=$currentMuted" }
        messageDispatcher?.sendState(playerState)
    }

    private fun startStateReporting() {
        logger.i { "Starting periodic state reporting" }
        stateReportingJob?.cancel()
        stateReportingJob = launch {
            while (isActive) {
                try {
                    // Wait before reporting (report every 2 seconds)
                    delay(2000)

                    // Only report if we're still streaming and synchronized
                    if (_playbackState.value == SendspinPlaybackState.Synchronized ||
                        _playbackState.value is SendspinPlaybackState.Playing
                    ) {

                        logger.d { "Periodic state report: SYNCHRONIZED" }
                        reportState(PlayerStateValue.SYNCHRONIZED)
                    } else if (_playbackState.value == SendspinPlaybackState.Buffering) {
                        logger.d { "Periodic state report: SYNCHRONIZED (buffering)" }
                        reportState(PlayerStateValue.SYNCHRONIZED)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.e(e) { "Error in state reporting" }
                }
            }
        }
    }

    private fun stopStateReporting() {
        logger.i { "Stopping periodic state reporting" }
        stateReportingJob?.cancel()
        stateReportingJob = null
    }

    suspend fun stop() {
        logger.i { "Stopping Sendspin client" }

        // Stop state reporting
        stopStateReporting()

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
