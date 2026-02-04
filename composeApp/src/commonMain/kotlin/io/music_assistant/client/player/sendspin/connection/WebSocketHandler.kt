package io.music_assistant.client.player.sendspin.connection

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
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
import io.music_assistant.client.player.sendspin.WebSocketState
import io.music_assistant.client.utils.createPlatformHttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
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
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.seconds

class WebSocketHandler(
    private val serverUrl: String
) : CoroutineScope {

    private val logger = Logger.withTag("WebSocketHandler")
    private val supervisorJob = SupervisorJob()

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.IO + supervisorJob

    private val client = createPlatformHttpClient {
        install(WebSockets) {
            pingInterval = 5.seconds  // More aggressive keepalive (was 30s)
            maxFrameSize = Long.MAX_VALUE
        }
    }

    private var session: DefaultClientWebSocketSession? = null
    private var listenerJob: Job? = null

    // Auto-reconnect state
    private var explicitDisconnect = false
    private var reconnectAttempts = 0
    private var reconnectJob: Job? = null
    private val maxReconnectAttempts = 10

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
        logger.i { "Connecting to $serverUrl" }

        try {
            val wsSession = client.webSocketSession(serverUrl)
            session = wsSession

            // Reset auto-reconnect state on successful connection
            reconnectAttempts = 0
            explicitDisconnect = false
            reconnectJob?.cancel()
            reconnectJob = null

            _connectionState.value = WebSocketState.Connected
            logger.i { "Connected to $serverUrl" }

            startListening(wsSession)
        } catch (e: Exception) {
            logger.e(e) { "Failed to connect to $serverUrl" }
            _connectionState.value = WebSocketState.Error(e)
            session = null
        }
    }

    private fun startListening(wsSession: DefaultClientWebSocketSession) {
        listenerJob?.cancel()
        listenerJob = launch {
            try {
                for (frame in wsSession.incoming) {
                    when (frame) {
                        is Frame.Text -> {
                            val text = frame.readText()
                            logger.d { "Received text message: ${text.take(100)}" }
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
                        
                        else -> {
                            // Ignore other frame types
                        }
                    }
                }
            } catch (e: Exception) {
                if (explicitDisconnect) {
                    logger.i { "Explicit disconnect, not reconnecting" }
                    handleDisconnection()
                    return@launch
                }

                // Network error - auto-reconnect!
                Logger.withTag("WebSocketHandler").e { "❌ WS ERROR: ${e.message} - will auto-reconnect" }
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
            throw IllegalStateException("WebSocket not connected")
        }

        try {
            logger.d { "Sending text message: ${message.take(100)}" }
            currentSession.send(Frame.Text(message))
        } catch (e: Exception) {
            logger.e(e) { "Failed to send text message" }
            throw e
        }
    }

    suspend fun sendBinary(data: ByteArray) {
        val currentSession = session
        if (currentSession == null || !currentSession.isActive) {
            throw IllegalStateException("WebSocket not connected")
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
            while (reconnectAttempts < maxReconnectAttempts && !explicitDisconnect) {
                val delayMs = calculateBackoff()
                logger.i { "Reconnect attempt ${reconnectAttempts + 1}/$maxReconnectAttempts in ${delayMs}ms" }
                _connectionState.value = WebSocketState.Reconnecting(reconnectAttempts)

                delay(delayMs)

                try {
                    reconnectAttempts++
                    logger.i { "Attempting reconnection..." }

                    // Try to reconnect
                    val wsSession = client.webSocketSession(serverUrl)
                    session = wsSession

                    // Success!
                    Logger.withTag("WebSocketHandler").e { "✅ RECONNECTED successfully after $reconnectAttempts attempts" }
                    reconnectAttempts = 0
                    _connectionState.value = WebSocketState.Connected

                    // Resume listening
                    startListening(wsSession)
                    return@launch

                } catch (e: Exception) {
                    logger.w(e) { "Reconnect attempt $reconnectAttempts failed" }
                    if (reconnectAttempts >= maxReconnectAttempts) {
                        logger.e { "Max reconnect attempts ($maxReconnectAttempts) reached, giving up" }
                        _connectionState.value = WebSocketState.Error(
                            Exception("Failed to reconnect after $maxReconnectAttempts attempts")
                        )
                        handleDisconnection()
                        return@launch
                    }
                }
            }
        }
    }

    private fun calculateBackoff(): Long {
        // Exponential backoff: 500ms, 1s, 2s, 5s, 10s
        return when (reconnectAttempts) {
            0 -> 500L
            1 -> 1000L
            2 -> 2000L
            3 -> 5000L
            else -> 10000L
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
