package io.music_assistant.client.api

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.receiveDeserialized
import io.ktor.client.plugins.websocket.sendSerialized
import io.ktor.client.plugins.websocket.ws
import io.ktor.client.plugins.websocket.wss
import io.ktor.http.HttpMethod
import io.ktor.websocket.close
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlin.concurrent.Volatile

class DirectTransport(
    private val clientProvider: () -> HttpClient,
    private val connectionInfoProvider: () -> ConnectionInfo,
    parentScope: CoroutineScope,
    private val networkAvailable: StateFlow<Boolean>? = null,
) : Transport {
    private val logger = Logger.withTag("DirectTransport")

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        logger.e(throwable) { "Uncaught exception in DirectTransport scope" }
        when (_state.value) {
            TransportState.Connected -> disconnect()
            TransportState.Connecting, is TransportState.Reconnecting ->
                _state.value = TransportState.Failed(
                    Exception("Recovery machinery died: ${throwable.message}", throwable),
                )
            TransportState.Disconnected, is TransportState.Failed -> Unit
        }
    }

    private val scope: CoroutineScope = CoroutineScope(
        parentScope.coroutineContext +
            SupervisorJob(parentScope.coroutineContext[Job]) +
            exceptionHandler,
    )

    private val _state = MutableStateFlow<TransportState>(TransportState.Disconnected)
    override val state = _state.asStateFlow()

    private val _messages = MutableSharedFlow<JsonObject>(extraBufferCapacity = 10)
    override val messages = _messages.asSharedFlow()

    private var connectionJob: Job? = null

    @Volatile
    private var session: DefaultClientWebSocketSession? = null
    private var wasConnected = false

    @Volatile
    private var messageCounter = 0L

    override fun connect() {
        connectionJob?.cancel()
        wasConnected = false
        connectionJob = scope.launch {
            _state.value = TransportState.Connecting
            try {
                openWebSocket(connectionInfoProvider())
                // Returned normally = was connected, then connection dropped
                if (wasConnected) startReconnection()
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

    private suspend fun openWebSocket(
        info: ConnectionInfo,
        onConnected: () -> Unit = {},
    ) {
        val block: suspend DefaultClientWebSocketSession.() -> Unit = {
            session = this
            wasConnected = true
            _state.value = TransportState.Connected
            onConnected()
            try {
                while (true) {
                    val message: JsonObject = receiveDeserialized()
                    messageCounter++
                    _messages.emit(message)
                }
            } catch (@Suppress("SwallowedException") e: ClosedReceiveChannelException) {
                // Expected on graceful close — the channel-closed exception type IS the signal.
                logger.i { "WebSocket connection closed" }
            } finally {
                session = null
            }
        }
        if (info.isTls) {
            clientProvider().wss(HttpMethod.Get, info.host, info.port, "/ws", block = block)
        } else {
            clientProvider().ws(HttpMethod.Get, info.host, info.port, "/ws", block = block)
        }
    }

    private suspend fun startReconnection() {
        // Outer loop: each successful-then-dropped connection gets a fresh attempt cycle.
        while (true) {
            val reconnected = runReconnectionLoop(
                maxAttempts = DEFAULT_MAX_RECONNECT_ATTEMPTS,
                networkAvailable = networkAvailable,
                onAttemptStarting = { _state.value = TransportState.Reconnecting(it) },
                tryConnect = { attempt ->
                    var connectedThisAttempt = false
                    try {
                        logger.i { "Reconnect attempt $attempt" }
                        openWebSocket(connectionInfoProvider()) { connectedThisAttempt = true }
                        // Returned normally = was connected, then dropped — signal success for fresh cycle
                        true
                    } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
                        logger.w { "Reconnect attempt $attempt failed: ${e.message}" }
                        // If we reached Connected during this attempt, treat the drop as a
                        // fresh-cycle trigger so the next reconnect starts at attempt 0.
                        connectedThisAttempt
                    }
                },
            )
            if (!reconnected) {
                _state.value = TransportState.Failed(Exception("Max reconnect attempts reached"))
                return
            }
            // Was reconnected but connection dropped again — start fresh cycle
        }
    }

    override suspend fun send(message: JsonObject) {
        val s = session ?: error("Not connected")
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

    override fun close() {
        scope.cancel()
    }
}
