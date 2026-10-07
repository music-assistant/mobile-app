package io.music_assistant.client.webrtc

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import io.music_assistant.client.webrtc.model.SignalingMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlin.concurrent.Volatile

/**
 * WebSocket state for signaling server connection.
 */
sealed class SignalingState {
    /** Not connected */
    data object Disconnected : SignalingState()

    /** Connecting to signaling server */
    data object Connecting : SignalingState()

    /** Connected to signaling server */
    data object Connected : SignalingState()

    /** Connection error */
    data class Error(val error: Throwable) : SignalingState()
}

/**
 * WebSocket client for WebRTC signaling server.
 *
 * Connects to wss://signaling.music-assistant.io/ws and routes messages between
 * the client and Music Assistant gateway for WebRTC peer connection establishment.
 *
 * Flow:
 * 1. Client connects to signaling server
 * 2. Client sends Connect message with Remote ID
 * 3. Server responds with SessionReady (includes session ID and ICE servers)
 * 4. Client and gateway exchange SDP offers/answers and ICE candidates via server
 * 5. WebRTC peer connection established directly between client and gateway
 *
 * @param client HttpClient configured with WebSockets (should be shared/injected)
 * @param scope CoroutineScope for managing connection lifecycle
 * @param signalingUrl WebSocket URL of signaling server (default: production server)
 */
class SignalingClient(
    private val client: HttpClient,
    private val scope: CoroutineScope,
    private val signalingUrl: String = DEFAULT_SIGNALING_URL,
    val diagnostics: WebRTCDiagnostics = WebRTCDiagnostics(),
) {
    private val logger = Logger.withTag("SignalingClient")
    private val mutex = Mutex()

    @Volatile
    private var session: io.ktor.client.plugins.websocket.DefaultClientWebSocketSession? = null
    private var receiveJob: Job? = null

    private val _connectionState = MutableStateFlow<SignalingState>(SignalingState.Disconnected)
    val connectionState: StateFlow<SignalingState> = _connectionState.asStateFlow()

    private val _incomingMessages = MutableSharedFlow<SignalingMessage>(extraBufferCapacity = 50)
    val incomingMessages: SharedFlow<SignalingMessage> = _incomingMessages.asSharedFlow()

    /**
     * Connect to the signaling server.
     * Opens WebSocket connection and starts listening for messages.
     * Thread-safe: protected by mutex to prevent concurrent connection attempts.
     */
    suspend fun connect() = mutex.withLock {
        if (_connectionState.value is SignalingState.Connected ||
            _connectionState.value is SignalingState.Connecting
        ) {
            logger.w { "Already connected or connecting to signaling server" }
            return@withLock
        }

        diagnostics.event("signaling open started")
        _connectionState.value = SignalingState.Connecting

        try {
            val wsSession = client.webSocketSession(signalingUrl)
            session = wsSession
            _connectionState.value = SignalingState.Connected
            diagnostics.event("signaling open completed")
            // Observe completion only: never await closeReason or extend cleanup lifetime.
            wsSession.closeReason.invokeOnCompletion { error ->
                if (error != null) {
                    diagnostics.failure("signaling close metadata unavailable", error)
                } else {
                    logCloseReason(wsSession.closeReason)
                }
            }

            // Start listening for incoming messages
            startReceiving()
        } catch (e: Exception) {
            diagnostics.failure("signaling open failed", e)
            _connectionState.value = SignalingState.Error(e)
            session = null
        }
    }

    /**
     * Send a signaling message to the server.
     *
     * @param message The message to send (will be serialized to JSON)
     */
    suspend fun sendMessage(message: SignalingMessage) {
        val currentSession = session
        if (currentSession == null) {
            logger.e { "Cannot send message: not connected to signaling server" }
            error("Not connected to signaling server")
        }

        try {
            // Serialize using the concrete class serializer
            // Note: Can't use polymorphic serialization because "type" field conflicts with discriminator
            val json = when (message) {
                is SignalingMessage.ConnectRequest -> signalingJson.encodeToString(
                    SignalingMessage.ConnectRequest.serializer(),
                    message,
                )

                is SignalingMessage.Connected -> signalingJson.encodeToString(
                    SignalingMessage.Connected.serializer(),
                    message,
                )

                is SignalingMessage.Offer -> signalingJson.encodeToString(
                    SignalingMessage.Offer.serializer(),
                    message,
                )

                is SignalingMessage.Answer -> signalingJson.encodeToString(
                    SignalingMessage.Answer.serializer(),
                    message,
                )

                is SignalingMessage.IceCandidate -> signalingJson.encodeToString(
                    SignalingMessage.IceCandidate.serializer(),
                    message,
                )

                is SignalingMessage.Error -> signalingJson.encodeToString(
                    SignalingMessage.Error.serializer(),
                    message,
                )

                is SignalingMessage.PeerDisconnected -> signalingJson.encodeToString(
                    SignalingMessage.PeerDisconnected.serializer(),
                    message,
                )

                is SignalingMessage.Ping -> signalingJson.encodeToString(
                    SignalingMessage.Ping.serializer(),
                    message,
                )

                is SignalingMessage.Pong -> signalingJson.encodeToString(
                    SignalingMessage.Pong.serializer(),
                    message,
                )

                is SignalingMessage.Unknown -> signalingJson.encodeToString(
                    SignalingMessage.Unknown.serializer(),
                    message,
                )
            }
            logger.d { "Sending signaling message: ${message.type}" }
            currentSession.send(Frame.Text(json))
        } catch (e: Exception) {
            diagnostics.failure("signaling send failed", e)
            throw e
        }
    }

    /**
     * Disconnect from the signaling server.
     * Thread-safe: protected by mutex.
     */
    suspend fun disconnect() = mutex.withLock {
        diagnostics.event("signaling local disconnect requested")

        try {
            receiveJob?.cancelAndJoin()
            receiveJob = null

            session?.close(CloseReason(CloseReason.Codes.NORMAL, "Client disconnect"))
            session = null

            _connectionState.value = SignalingState.Disconnected
            logger.i { "Disconnected from signaling server" }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.e(e) { "Error during disconnect" }
        }
    }

    /**
     * Close the signaling client and cleanup resources.
     * Should be called when the client is no longer needed.
     * Note: Does not close the HttpClient as it's injected and may be shared.
     */
    suspend fun close() {
        logger.i { "Closing signaling client" }
        disconnect()
    }

    /**
     * Start listening for incoming messages from the signaling server.
     */
    private fun startReceiving() {
        receiveJob = scope.launch {
            val currentSession = session ?: return@launch

            try {
                for (frame in currentSession.incoming) {
                    when (frame) {
                        is Frame.Text -> {
                            handleTextFrame(frame)
                        }

                        is Frame.Close -> {
                            logger.i { "Signaling server closed connection" }
                            _connectionState.value = SignalingState.Disconnected
                            break
                        }

                        else -> {
                            // Ignore binary frames, ping/pong
                        }
                    }
                }
            } catch (e: Exception) {
                diagnostics.failure("signaling receive ended active=$isActive", e)
                if (isActive) {
                    _connectionState.value = SignalingState.Error(e)
                }
            } finally {
                val closeMetadataReady = currentSession.closeReason.isCompleted
                diagnostics.event("signaling receive terminated active=$isActive closeMetadataReady=$closeMetadataReady")
                session = null
                if (_connectionState.value is SignalingState.Connected) {
                    _connectionState.value = SignalingState.Disconnected
                }
            }
        }
    }

    /**
     * Handle incoming text frame (JSON signaling message).
     */
    private suspend fun handleTextFrame(frame: Frame.Text) {
        try {
            val text = frame.readText()
            val message = signalingJson.decodeFromString(SignalingMessageSerializer, text)
            // Keepalives stay at Debug: at Info they would flood the shared crash-report
            // ring buffer (InMemoryLogWriter) on long-lived connections.
            when (message) {
                is SignalingMessage.Ping, is SignalingMessage.Pong ->
                    logger.d { "Signaling keepalive: ${message.type}" }
                else -> diagnostics.event("signaling message received type=${message::class.simpleName}")
            }

            // Handle ping at transport level — respond immediately, don't emit to consumers
            if (message is SignalingMessage.Ping) {
                logger.d { "Responding to signaling ping with pong" }
                sendMessage(SignalingMessage.Pong())
                return
            }

            _incomingMessages.emit(message)
        } catch (e: Exception) {
            diagnostics.failure("signaling parse/dispatch failed", e)
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun logCloseReason(reason: kotlinx.coroutines.Deferred<CloseReason?>) {
        diagnostics.event("signaling closed ${safeCloseSummary(reason.getCompleted())}")
    }

    companion object {
        /**
         * Default signaling server URL (Music Assistant production server).
         */
        const val DEFAULT_SIGNALING_URL = "wss://signaling.music-assistant.io/ws"

        /**
         * Json instance configured for signaling message serialization/deserialization.
         *
         * NOTE: We can't use standard polymorphic serialization because the protocol's
         * "type" field conflicts with kotlinx.serialization's class discriminator.
         *
         * For deserialization: Uses JsonContentPolymorphicSerializer (type-based routing)
         * For serialization: Directly serializes concrete class (via message::class.serializer())
         */
        private val signalingJson = Json {
            ignoreUnknownKeys = true
            isLenient = false
            encodeDefaults = true
        }
    }
}
