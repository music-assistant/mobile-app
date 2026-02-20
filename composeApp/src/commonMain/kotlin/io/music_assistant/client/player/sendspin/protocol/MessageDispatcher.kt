package io.music_assistant.client.player.sendspin.protocol

import co.touchlab.kermit.Logger
import io.music_assistant.client.player.sendspin.ClockSynchronizer
import io.music_assistant.client.player.sendspin.ProtocolState
import io.music_assistant.client.player.sendspin.model.ClientAuthMessage
import io.music_assistant.client.player.sendspin.model.ClientCommandMessage
import io.music_assistant.client.player.sendspin.model.ClientGoodbyeMessage
import io.music_assistant.client.player.sendspin.model.ClientHelloMessage
import io.music_assistant.client.player.sendspin.model.ClientHelloPayload
import io.music_assistant.client.player.sendspin.model.ClientStateMessage
import io.music_assistant.client.player.sendspin.model.ClientStatePayload
import io.music_assistant.client.player.sendspin.model.ClientTimeMessage
import io.music_assistant.client.player.sendspin.model.ClientTimePayload
import io.music_assistant.client.player.sendspin.model.CommandPayload
import io.music_assistant.client.player.sendspin.model.CommandValue
import io.music_assistant.client.player.sendspin.model.GoodbyePayload
import io.music_assistant.client.player.sendspin.model.GroupUpdateMessage
import io.music_assistant.client.player.sendspin.model.PlayerStateObject
import io.music_assistant.client.player.sendspin.model.PlayerStateValue
import io.music_assistant.client.player.sendspin.model.ServerCommandMessage
import io.music_assistant.client.player.sendspin.model.ServerHelloMessage
import io.music_assistant.client.player.sendspin.model.ServerHelloPayload
import io.music_assistant.client.player.sendspin.model.ServerStateMessage
import io.music_assistant.client.player.sendspin.model.ServerTimeMessage
import io.music_assistant.client.player.sendspin.model.SessionUpdateMessage
import io.music_assistant.client.player.sendspin.model.StreamClearMessage
import io.music_assistant.client.player.sendspin.model.StreamEndMessage
import io.music_assistant.client.player.sendspin.model.StreamMetadataMessage
import io.music_assistant.client.player.sendspin.model.StreamMetadataPayload
import io.music_assistant.client.player.sendspin.model.StreamStartMessage
import io.music_assistant.client.player.sendspin.transport.SendspinTransport
import io.music_assistant.client.utils.myJson
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.seconds

class MessageDispatcher(
    private val transport: SendspinTransport,
    private val clockSynchronizer: ClockSynchronizer,
    private val config: MessageDispatcherConfig
) : CoroutineScope {

    // Convenience accessors for config properties
    private val clientCapabilities: ClientHelloPayload get() = config.clientCapabilities
    private val initialVolume: Int get() = config.initialVolume
    private val authToken: String? get() = config.authToken
    private val requiresAuth: Boolean get() = config.requiresAuth

    private val logger = Logger.withTag("MessageDispatcher")
    private val supervisorJob = SupervisorJob()

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Default + supervisorJob

    private var messageListenerJob: Job? = null
    private var clockSyncJob: Job? = null

    private val _protocolState = MutableStateFlow<ProtocolState>(ProtocolState.Disconnected)
    val protocolState: StateFlow<ProtocolState> = _protocolState.asStateFlow()

    private val _serverInfo = MutableStateFlow<ServerHelloPayload?>(null)
    val serverInfo: StateFlow<ServerHelloPayload?> = _serverInfo.asStateFlow()

    private val _streamMetadata = MutableStateFlow<StreamMetadataPayload?>(null)
    val streamMetadata: StateFlow<StreamMetadataPayload?> = _streamMetadata.asStateFlow()

    private val _streamStartEvent = MutableSharedFlow<StreamStartMessage>(extraBufferCapacity = 1)
    val streamStartEvent: Flow<StreamStartMessage> = _streamStartEvent.asSharedFlow()

    private val _streamEndEvent = MutableSharedFlow<StreamEndMessage>(extraBufferCapacity = 1)
    val streamEndEvent: Flow<StreamEndMessage> = _streamEndEvent.asSharedFlow()

    private val _streamClearEvent = MutableSharedFlow<StreamClearMessage>(extraBufferCapacity = 1)
    val streamClearEvent: Flow<StreamClearMessage> = _streamClearEvent.asSharedFlow()

    private val _serverCommandEvent =
        MutableSharedFlow<ServerCommandMessage>(extraBufferCapacity = 5)
    val serverCommandEvent: Flow<ServerCommandMessage> = _serverCommandEvent.asSharedFlow()

    fun start() {
        logger.i { "Starting MessageDispatcher" }
        startMessageListener()
    }

    fun stop() {
        Logger.withTag("MessageDispatcher")
            .e { "🛑 STOP called - setting protocolState to Disconnected" }
        messageListenerJob?.cancel()
        clockSyncJob?.cancel()
        _protocolState.value = ProtocolState.Disconnected
    }

    private fun startMessageListener() {
        messageListenerJob?.cancel()
        messageListenerJob = launch {
            try {
                transport.textMessages.collect { text ->
                    try {
                        handleTextMessage(text)
                    } catch (e: Exception) {
                        logger.e(e) { "Error handling text message: $text" }
                    }
                }
            } catch (e: CancellationException) {
                logger.d { "Message listener cancelled" }
                throw e
            } catch (e: Exception) {
                logger.e(e) { "Message listener error" }
            } finally {
                // Stop clock sync when message listener stops
                clockSyncJob?.cancel()
            }
        }
    }

    private suspend fun handleTextMessage(text: String) {
        logger.d { "Handling message: ${text.take(200)}" }

        try {
            val json = myJson.parseToJsonElement(text).jsonObject
            val type = json["type"]?.jsonPrimitive?.content
                ?: throw IllegalArgumentException("Message missing 'type' field")

            when (type) {
                "auth_ok" -> {
                    handleAuthOk()
                }

                "server/hello" -> {
                    val message = myJson.decodeFromJsonElement<ServerHelloMessage>(json)
                    handleServerHello(message)
                }

                "server/time" -> {
                    val message = myJson.decodeFromJsonElement<ServerTimeMessage>(json)
                    handleServerTime(message)
                }

                "stream/start" -> {
                    val message = myJson.decodeFromJsonElement<StreamStartMessage>(json)
                    handleStreamStart(message)
                }

                "stream/end" -> {
                    val message = myJson.decodeFromJsonElement<StreamEndMessage>(json)
                    handleStreamEnd(message)
                }

                "stream/clear" -> {
                    val message = myJson.decodeFromJsonElement<StreamClearMessage>(json)
                    handleStreamClear(message)
                }

                "stream/metadata" -> {
                    val message = myJson.decodeFromJsonElement<StreamMetadataMessage>(json)
                    handleStreamMetadata(message)
                }

                "session/update" -> {
                    val message = myJson.decodeFromJsonElement<SessionUpdateMessage>(json)
                    handleSessionUpdate(message)
                }

                "server/command" -> {
                    val message = myJson.decodeFromJsonElement<ServerCommandMessage>(json)
                    handleServerCommand(message)
                }

                "group/update" -> {
                    val message = myJson.decodeFromJsonElement<GroupUpdateMessage>(json)
                    handleGroupUpdate(message)
                }

                "server/state" -> {
                    val message = myJson.decodeFromJsonElement<ServerStateMessage>(json)
                    handleServerState(message)
                }

                else -> {
                    logger.w { "Unknown message type: $type" }
                }
            }
        } catch (e: Exception) {
            logger.e(e) { "Failed to parse message: $text" }
        }
    }

    // Outgoing messages

    suspend fun sendAuth() {
        val token = authToken
        if (!requiresAuth || token == null) {
            logger.w { "sendAuth called but auth not required or token missing" }
            return
        }

        logger.i { "Sending auth message (proxy mode)" }
        _protocolState.value = ProtocolState.AwaitingAuth

        val message = ClientAuthMessage(
            token = token,
            clientId = clientCapabilities.clientId
        )
        val json = myJson.encodeToString(message)
        transport.sendText(json)
    }

    suspend fun sendHello() {
        logger.i { "Sending client/hello" }
        _protocolState.value = ProtocolState.AwaitingServerHello

        val message = ClientHelloMessage(payload = clientCapabilities)
        val json = myJson.encodeToString(message)
        transport.sendText(json)
    }

    suspend fun sendTime() {
        val clientTransmitted = getCurrentTimeMicros()
        val message = ClientTimeMessage(
            payload = ClientTimePayload(clientTransmitted = clientTransmitted)
        )
        val json = myJson.encodeToString(message)
        transport.sendText(json)
    }

    suspend fun sendState(state: PlayerStateObject) {
        val message = ClientStateMessage(
            payload = ClientStatePayload(player = state)
        )
        val json = myJson.encodeToString(message)
        logger.d { "Sending client/state: $json" }
        transport.sendText(json)
    }

    suspend fun sendGoodbye(reason: String) {
        logger.i { "Sending client/goodbye: $reason" }
        val message = ClientGoodbyeMessage(
            payload = GoodbyePayload(reason = reason)
        )
        val json = myJson.encodeToString(message)
        transport.sendText(json)
    }

    suspend fun sendCommand(command: String, value: CommandValue?) {
        logger.d { "Sending client/command: $command" }
        val message = ClientCommandMessage(
            payload = CommandPayload(command = command, value = value)
        )
        val json = myJson.encodeToString(message)
        transport.sendText(json)
    }

    // Message handlers

    private suspend fun handleAuthOk() {
        logger.i { "Received auth_ok - authentication successful" }
        // Auth successful, now send hello
        sendHello()
    }

    private suspend fun handleServerHello(message: ServerHelloMessage) {
        logger.i { "Received server/hello from ${message.payload.name}" }
        _serverInfo.value = message.payload
        _protocolState.value = ProtocolState.Ready(message.payload.activeRoles)

        // Send initial state (required by spec)
        sendInitialState()

        // Start clock synchronization
        startClockSync()
    }

    private suspend fun sendInitialState() {
        // Send initial player state as SYNCHRONIZED with current system volume
        val initialState = PlayerStateObject(
            state = PlayerStateValue.SYNCHRONIZED,
            volume = initialVolume,
            muted = false
        )
        sendState(initialState)
    }

    private fun startClockSync() {
        clockSyncJob?.cancel()
        clockSyncJob = launch {
            while (isActive) {
                try {
                    sendTime()
                    delay(1.seconds)
                } catch (_: IllegalStateException) {
                    // Transport not connected, stop clock sync
                    logger.w { "Clock sync stopped: Transport not connected" }
                    break
                } catch (e: Exception) {
                    logger.e(e) { "Error in clock sync" }
                    // Stop on any error to prevent spam
                    break
                }
            }
        }
    }

    private fun handleServerTime(message: ServerTimeMessage) {
        val clientReceived = getCurrentTimeMicros()
        val payload = message.payload

        clockSynchronizer.processServerTime(
            clientTransmitted = payload.clientTransmitted,
            serverReceived = payload.serverReceived,
            serverTransmitted = payload.serverTransmitted,
            clientReceived = clientReceived
        )

        logger.d { "Clock sync: offset=${clockSynchronizer.currentOffset}μs, quality=${clockSynchronizer.currentQuality}" }
    }

    private suspend fun handleStreamStart(message: StreamStartMessage) {
        logger.i { "Received stream/start" }
        _protocolState.value = ProtocolState.Streaming
        _streamStartEvent.emit(message)
    }

    private suspend fun handleStreamEnd(message: StreamEndMessage) {
        logger.i { "Received stream/end" }
        val currentState = _protocolState.value
        if (currentState is ProtocolState.Ready) {
            // Already ready, keep the state
        } else {
            _protocolState.value = ProtocolState.Ready(
                _serverInfo.value?.activeRoles ?: emptyList()
            )
        }
        _streamEndEvent.emit(message)
    }

    private suspend fun handleStreamClear(message: StreamClearMessage) {
        logger.i { "Received stream/clear" }
        _streamClearEvent.emit(message)
    }

    private fun handleStreamMetadata(message: StreamMetadataMessage) {
        logger.i { "Received stream/metadata: ${message.payload.title}" }
        _streamMetadata.value = message.payload
    }

    private fun handleSessionUpdate(message: SessionUpdateMessage) {
        logger.d { "Received session/update: ${message.payload.metadata?.title}" }
        // Update metadata if provided (duration/elapsed come from MainDataSource via regular API)
        message.payload.metadata?.let { metadata ->
            _streamMetadata.value = StreamMetadataPayload(
                title = metadata.title,
                artist = metadata.artist,
                album = metadata.album,
                artworkUrl = metadata.artworkUrl
            )
        }
    }

    private suspend fun handleServerCommand(message: ServerCommandMessage) {
        logger.d { "Received server/command: ${message.payload.player.command}" }
        _serverCommandEvent.emit(message)
    }

    private fun handleGroupUpdate(message: GroupUpdateMessage) {
        logger.d { "Received group/update: ${message.payload.groupName}" }
        // Store group info if needed later
    }

    private fun handleServerState(message: ServerStateMessage) {
        logger.d { "Received server/state: ${message.payload}" }

        // Extract metadata from server/state payload if present
        // Note: duration/elapsed come from MainDataSource via regular API
        message.payload?.let { payload ->
            try {
                val metadataElement = payload.jsonObject["metadata"]
                if (metadataElement != null) {
                    val metadata = metadataElement.jsonObject
                    val title = metadata["title"]?.jsonPrimitive?.contentOrNull
                    val artist = metadata["artist"]?.jsonPrimitive?.contentOrNull
                    val album = metadata["album"]?.jsonPrimitive?.contentOrNull
                    val artworkUrl = metadata["artwork_url"]?.jsonPrimitive?.contentOrNull

                    // Only update if we have at least title or artist
                    if (title != null || artist != null) {
                        _streamMetadata.value = StreamMetadataPayload(
                            title = title,
                            artist = artist,
                            album = album,
                            artworkUrl = artworkUrl
                        )
                        logger.d { "Updated stream metadata from server/state: $title by $artist" }
                    }
                }
            } catch (e: Exception) {
                logger.w { "Failed to parse server/state metadata: ${e.message}" }
            }
        }
    }

    // Use monotonic time for clock sync instead of wall clock time
    // Delegate to ClockSynchronizer so all timestamps share the same monotonic epoch.
    private fun getCurrentTimeMicros(): Long = clockSynchronizer.getCurrentTimeMicros()

    fun close() {
        logger.i { "Closing MessageDispatcher" }
        stop()
        supervisorJob.cancel()
    }
}
