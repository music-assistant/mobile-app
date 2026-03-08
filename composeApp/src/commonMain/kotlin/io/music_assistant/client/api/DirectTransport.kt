package io.music_assistant.client.api

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.receiveDeserialized
import io.ktor.client.plugins.websocket.sendSerialized
import io.ktor.client.plugins.websocket.ws
import io.ktor.client.plugins.websocket.wss
import io.ktor.http.HttpMethod
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

private fun backoffMs(attempt: Int): Long = when (attempt) {
    0 -> 0L
    1 -> 500L
    2 -> 1000L
    3 -> 2000L
    else -> 3000L
}

class DirectTransport(
    private val client: HttpClient,
    private val connectionInfoProvider: () -> ConnectionInfo,
    private val scope: CoroutineScope,
    private val maxReconnectAttempts: Int = 5
) : Transport {

    private val logger = Logger.withTag("DirectTransport")

    private val _state = MutableStateFlow<TransportState>(TransportState.Disconnected)
    override val state = _state.asStateFlow()

    private val _messages = MutableSharedFlow<JsonObject>(extraBufferCapacity = 10)
    override val messages = _messages.asSharedFlow()

    private var connectionJob: Job? = null
    private var session: DefaultClientWebSocketSession? = null
    private var wasConnected = false
    private var messageCounter = 0L

    override fun connect() {
        connectionJob?.cancel()
        wasConnected = false
        connectionJob = scope.launch {
            _state.value = TransportState.Connecting
            try {
                openWebSocket(connectionInfoProvider())
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (!wasConnected) {
                    logger.w { "Initial connection failed: ${e.message}" }
                    _state.value = TransportState.Failed(e)
                } else {
                    startReconnection()
                }
            }
        }
    }

    private suspend fun openWebSocket(info: ConnectionInfo) {
        val block: suspend DefaultClientWebSocketSession.() -> Unit = {
            session = this
            wasConnected = true
            _state.value = TransportState.Connected
            try {
                while (true) {
                    val message: JsonObject = receiveDeserialized()
                    messageCounter++
                    _messages.emit(message)
                }
            } catch (e: ClosedReceiveChannelException) {
                logger.d { "WebSocket connection closed" }
            } finally {
                session = null
            }
        }
        if (info.isTls) {
            client.wss(HttpMethod.Get, info.host, info.port, "/ws", block = block)
        } else {
            client.ws(HttpMethod.Get, info.host, info.port, "/ws", block = block)
        }
        // Block returned = connection dropped. If wasConnected, auto-reconnect.
        if (wasConnected) startReconnection()
    }

    private suspend fun startReconnection() {
        for (attempt in 0 until maxReconnectAttempts) {
            _state.value = TransportState.Reconnecting(attempt + 1)
            delay(backoffMs(attempt))
            try {
                logger.i { "Reconnect attempt ${attempt + 1}/$maxReconnectAttempts" }
                openWebSocket(connectionInfoProvider())
                return // openWebSocket stayed alive then dropped again — loop re-entered via recursive startReconnection
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                logger.w { "Reconnect attempt ${attempt + 1} failed: ${e.message}" }
            }
        }
        _state.value = TransportState.Failed(Exception("Max reconnect attempts reached"))
    }

    override fun verifyConnection(timeoutMs: Long) {
        val s = session ?: return
        val countBefore = messageCounter
        scope.launch {
            val sendOk = try {
                s.send(Frame.Ping(byteArrayOf()))
                true
            } catch (e: Exception) {
                logger.i { "Connection probe failed immediately: ${e.message}" }
                false
            }

            if (!sendOk) {
                initiateReconnect()
                return@launch
            }

            delay(timeoutMs)

            // If no messages arrived and session unchanged — connection is dead
            if (messageCounter == countBefore && session === s && _state.value == TransportState.Connected) {
                logger.i { "No activity within ${timeoutMs}ms after probe — reconnecting" }
                initiateReconnect()
            }
        }
    }

    override suspend fun send(message: JsonObject) {
        val s = session ?: throw IllegalStateException("Not connected")
        s.sendSerialized(message)
    }

    override fun disconnect() {
        connectionJob?.cancel()
        connectionJob = null
        val s = session
        session = null
        if (s != null) {
            scope.launch { s.close() }
        }
        _state.value = TransportState.Disconnected
    }

    /**
     * Transition to Reconnecting BEFORE nulling the session, so any concurrent
     * sendRequest sees a non-Connected transport state and skips disconnect(Error).
     */
    private fun initiateReconnect() {
        _state.value = TransportState.Reconnecting(0)
        connectionJob?.cancel()
        val s = session
        session = null
        if (s != null) {
            scope.launch { s.close() }
        }
        connectionJob = scope.launch {
            startReconnection()
        }
    }
}
