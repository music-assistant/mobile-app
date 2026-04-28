// Log-payload truncation lengths and connection delays are inline-documented at use site.
@file:Suppress("MagicNumber")

package io.music_assistant.client.api

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.music_assistant.client.utils.myJson
import io.music_assistant.client.webrtc.DataChannelWrapper
import io.music_assistant.client.webrtc.SignalingClient
import io.music_assistant.client.webrtc.WebRTCConnectionManager
import io.music_assistant.client.webrtc.model.RemoteId
import io.music_assistant.client.webrtc.model.WebRTCConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

class WebRTCTransport(
    private val httpClient: HttpClient,
    private val remoteId: RemoteId,
    private val scope: CoroutineScope,
    private val networkAvailable: StateFlow<Boolean>? = null,
    private val maxReconnectAttempts: Int = DEFAULT_MAX_RECONNECT_ATTEMPTS,
) : Transport {
    private val logger = Logger.withTag("WebRTCTransport")

    private val _state = MutableStateFlow<TransportState>(TransportState.Disconnected)
    override val state = _state.asStateFlow()

    private val _messages = MutableSharedFlow<JsonObject>(extraBufferCapacity = 10)
    override val messages = _messages.asSharedFlow()

    private var manager: WebRTCConnectionManager? = null
    private var connectionJob: Job? = null
    private var messageListenerJob: Job? = null
    private var stateMonitorJob: Job? = null
    private var reconnectionJob: Job? = null

    val sendspinDataChannel: DataChannelWrapper?
        get() = manager?.sendspinDataChannel

    override fun connect() {
        connectionJob?.cancel()
        connectionJob = scope.launch {
            _state.value = TransportState.Connecting
            connectInternal(isReconnect = false)
        }
    }

    private suspend fun connectInternal(isReconnect: Boolean) {
        try {
            // Clean up old manager (does not cancel reconnectionJob — we may be inside it)
            cleanupManager()
            val mgr = createManager()
            manager = mgr
            mgr.connect(remoteId)

            // Wait for terminal WebRTC connection state (Connected or Error)
            val result = mgr.connectionState.first { connState ->
                connState is WebRTCConnectionState.Connected || connState is WebRTCConnectionState.Error
            }

            when (result) {
                is WebRTCConnectionState.Connected -> {
                    _state.value = TransportState.Connected
                    startMessageListener(mgr)
                    startStateMonitor(mgr)
                }

                is WebRTCConnectionState.Error -> {
                    if (!isReconnect) {
                        _state.value = TransportState.Failed(
                            Exception("WebRTC connection failed: ${result.error}"),
                        )
                    }
                }

                else -> {} // unreachable
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            if (!isReconnect) {
                _state.value = TransportState.Failed(e)
            }
        }
    }

    private fun startMessageListener(mgr: WebRTCConnectionManager) {
        messageListenerJob?.cancel()
        messageListenerJob = scope.launch {
            try {
                mgr.incomingMessages.collect { jsonString ->
                    try {
                        val jsonObject = myJson.decodeFromString<JsonObject>(jsonString)
                        _messages.emit(jsonObject)
                    } catch (e: Exception) {
                        logger.e(e) { "Failed to parse incoming WebRTC message: ${jsonString.take(200)}" }
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                logger.d { "WebRTC message listener ended: ${e.message}" }
            }
        }
    }

    private fun startStateMonitor(mgr: WebRTCConnectionManager) {
        stateMonitorJob?.cancel()
        stateMonitorJob = scope.launch {
            // Wait for error state — first() completes when predicate matches
            val errorState = mgr.connectionState.first { it is WebRTCConnectionState.Error }
            logger.w { "WebRTC error detected: $errorState. Starting reconnection..." }
            messageListenerJob?.cancel()
            // Launch reconnection in a separate job so:
            // 1. cleanupManager() can cancel stateMonitorJob without killing reconnection
            // 2. disconnect()/forceReconnect() can cancel reconnectionJob explicitly
            reconnectionJob?.cancel()
            reconnectionJob = scope.launch { startReconnection() }
        }
    }

    private suspend fun startReconnection() {
        val reconnected = runReconnectionLoop(
            maxAttempts = maxReconnectAttempts,
            networkAvailable = networkAvailable,
            onAttemptStarting = { _state.value = TransportState.Reconnecting(it) },
            tryConnect = { attempt ->
                logger.i { "WebRTC reconnect attempt $attempt/$maxReconnectAttempts" }
                connectInternal(isReconnect = true)
                _state.value == TransportState.Connected
            },
        )
        if (!reconnected) {
            _state.value = TransportState.Failed(Exception("Max WebRTC reconnect attempts reached"))
        }
    }

    fun forceReconnect() {
        connectionJob?.cancel()
        reconnectionJob?.cancel()
        connectionJob = scope.launch {
            messageListenerJob?.cancel()
            stateMonitorJob?.cancel()
            cleanupManager()
            delay(1500) // wait for signaling server to process disconnect
            logger.i { "Starting fresh WebRTC connection after forced disconnect" }
            startReconnection()
        }
    }

    override suspend fun send(message: JsonObject) {
        val mgr = manager ?: error("Not connected")
        val jsonString = myJson.encodeToString(JsonObject.serializer(), message)
        mgr.send(jsonString)
    }

    override fun disconnect() {
        connectionJob?.cancel()
        connectionJob = null
        reconnectionJob?.cancel()
        reconnectionJob = null
        messageListenerJob?.cancel()
        messageListenerJob = null
        stateMonitorJob?.cancel()
        stateMonitorJob = null
        val mgr = manager
        manager = null
        if (mgr != null) {
            scope.launch { mgr.disconnect() }
        }
        _state.value = TransportState.Disconnected
    }

    /** Cleans up the current manager and its listener jobs. Does NOT cancel reconnectionJob. */
    private suspend fun cleanupManager() {
        messageListenerJob?.cancel()
        messageListenerJob = null
        stateMonitorJob?.cancel()
        stateMonitorJob = null
        manager?.disconnect()
        manager = null
    }

    private fun createManager(): WebRTCConnectionManager {
        val signalingClient = SignalingClient(httpClient, scope)
        val mgr = WebRTCConnectionManager(signalingClient, scope)
        logger.d { "Created new WebRTC manager [${mgr.hashCode()}]" }
        return mgr
    }
}
