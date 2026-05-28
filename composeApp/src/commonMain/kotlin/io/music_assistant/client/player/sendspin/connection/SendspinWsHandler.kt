package io.music_assistant.client.player.sendspin.connection

import co.touchlab.kermit.Logger
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.pingInterval
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readBytes
import io.ktor.websocket.readReason
import io.ktor.websocket.readText
import io.music_assistant.client.api.DEFAULT_MAX_RECONNECT_ATTEMPTS
import io.music_assistant.client.api.runReconnectionLoop
import io.music_assistant.client.player.sendspin.WebSocketState
import io.music_assistant.client.utils.createPlatformHttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.seconds

class SendspinWsHandler(
    private val serverUrl: String,
    private val networkAvailable: StateFlow<Boolean>? = null,
) : CoroutineScope {
    private val logger = Logger.withTag("SendspinWsHandler")
    private val supervisorJob = SupervisorJob()

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.IO + supervisorJob

    private val client = createPlatformHttpClient {
        install(WebSockets) {
            pingInterval = 5.seconds
        }
    }

    private var session: DefaultClientWebSocketSession? = null
    private var listenerJob: Job? = null

    // Auto-reconnect state
    private var explicitDisconnect = false
    private var reconnectAttempts = 0
    private var reconnectJob: Job? = null
    private val maxReconnectAttempts = DEFAULT_MAX_RECONNECT_ATTEMPTS

    private val _textMessages = MutableSharedFlow<String>(extraBufferCapacity = 50)
    val textMessages: Flow<String> = _textMessages.asSharedFlow()

    private val _binaryMessages = MutableSharedFlow<ByteArray>(extraBufferCapacity = 100)
    val binaryMessages: Flow<ByteArray> = _binaryMessages.asSharedFlow()

    private val _connectionState = MutableStateFlow<WebSocketState>(WebSocketState.Disconnected)
    val connectionState: StateFlow<WebSocketState> = _connectionState.asStateFlow()

    suspend fun connect() {
        if (_connectionState.value is WebSocketState.Connected ||
            _connectionState.value is WebSocketState.Connecting
        ) {
            logger.w { "Already connected or connecting" }
            return
        }

        _connectionState.value = WebSocketState.Connecting
        logger.i { "Connecting to server" }

        try {
            val wsSession = client.webSocketSession(serverUrl)
            session = wsSession

            // Reset auto-reconnect state on successful connection
            reconnectAttempts = 0
            explicitDisconnect = false
            reconnectJob?.cancel()
            reconnectJob = null

            _connectionState.value = WebSocketState.Connected
            logger.i { "Connected to server" }

            startListening(wsSession)
        } catch (e: Exception) {
            logger.e(e) { "Failed to connect to server" }
            _connectionState.value = WebSocketState.Error(e)
            session = null
        }
    }

    // Ktor's Frame is `expect sealed`: the metadata compile can't prove the
    // when below is exhaustive without an `else`, but the platform compiles
    // resolve Frame to a concrete sealed and flag the `else` as redundant.
    // Suppress that warning here so both compiles stay clean.
    @Suppress("REDUNDANT_ELSE_IN_WHEN")
    private fun startListening(wsSession: DefaultClientWebSocketSession) {
        listenerJob?.cancel()
        listenerJob = launch {
            try {
                for (frame in wsSession.incoming) {
                    when (frame) {
                        is Frame.Text -> {
                            val text = frame.readText()
                            logger.d { "Received text message, length: ${text.length}" }
                            _textMessages.emit(text)
                        }

                        is Frame.Binary -> {
                            val data = frame.readBytes()
                            logger.d { "Received binary message: ${data.size} bytes" }
                            _binaryMessages.emit(data)
                        }

                        is Frame.Close -> {
                            logger.i { "WebSocket closed: ${frame.readReason()}" }
                            handleDisconnection()
                        }

                        is Frame.Ping, is Frame.Pong -> {
                            // Handled automatically by Ktor
                        }

                        else -> Unit
                    }
                }
            } catch (e: Exception) {
                if (explicitDisconnect) {
                    logger.i { "Explicit disconnect, not reconnecting" }
                    handleDisconnection()
                    return@launch
                }

                // Network error - auto-reconnect!
                logger.e(e) { "WS error - will auto-reconnect" }
                _connectionState.value = WebSocketState.Reconnecting(reconnectAttempts)

                attemptReconnect()
            } finally {
                if (!explicitDisconnect) {
                    // Only handle disconnection if not already reconnecting
                    if (_connectionState.value !is WebSocketState.Reconnecting) {
                        handleDisconnection()
                    }
                }
            }
        }
    }

    suspend fun sendText(message: String) {
        val currentSession = session
        if (currentSession == null || !currentSession.isActive) {
            error("WebSocket not connected")
        }

        try {
            logger.d { "Sending text message, length: ${message.length}" }
            currentSession.send(Frame.Text(message))
        } catch (e: Exception) {
            logger.e(e) { "Failed to send text message" }
            throw e
        }
    }

    suspend fun sendBinary(data: ByteArray) {
        val currentSession = session
        if (currentSession == null || !currentSession.isActive) {
            error("WebSocket not connected")
        }

        try {
            logger.d { "Sending binary message: ${data.size} bytes" }
            currentSession.send(Frame.Binary(true, data))
        } catch (e: Exception) {
            logger.e(e) { "Failed to send binary message" }
            throw e
        }
    }

    suspend fun disconnect() {
        logger.i { "Disconnecting WebSocket (explicit)" }
        explicitDisconnect = true
        reconnectJob?.cancel()
        reconnectJob = null

        listenerJob?.cancel()
        listenerJob = null

        session?.close(CloseReason(CloseReason.Codes.NORMAL, "Client disconnect"))
        session = null

        _connectionState.value = WebSocketState.Disconnected
    }

    private fun handleDisconnection() {
        if (_connectionState.value !is WebSocketState.Disconnected) {
            logger.i { "WebSocket disconnected" }
            _connectionState.value = WebSocketState.Disconnected
        }
        session = null
    }

    private fun attemptReconnect() {
        reconnectJob?.cancel()
        reconnectJob = launch {
            val reconnected = runReconnectionLoop(
                maxAttempts = maxReconnectAttempts,
                networkAvailable = networkAvailable,
                onAttemptStarting = { attempt ->
                    reconnectAttempts = attempt
                    logger.i { "Reconnect attempt $attempt/$maxReconnectAttempts" }
                    _connectionState.value = WebSocketState.Reconnecting(attempt)
                },
                tryConnect = { attempt ->
                    try {
                        val wsSession = client.webSocketSession(serverUrl)
                        session = wsSession
                        logger.i { "Reconnected successfully after $attempt attempts" }
                        reconnectAttempts = 0
                        _connectionState.value = WebSocketState.Connected
                        startListening(wsSession)
                        true
                    } catch (e: Exception) {
                        logger.w(e) { "Reconnect attempt $attempt failed" }
                        false
                    }
                },
            )
            if (!reconnected) {
                logger.e { "Max reconnect attempts ($maxReconnectAttempts) reached, giving up" }
                session = null
                _connectionState.value = WebSocketState.Error(
                    Exception("Failed to reconnect after $maxReconnectAttempts attempts"),
                )
            }
        }
    }

    fun close() {
        logger.i { "Closing WebSocketHandler" }
        explicitDisconnect = true
        reconnectJob?.cancel()
        supervisorJob.cancel()
        client.close()
    }
}
