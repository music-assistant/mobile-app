// Reconnection delay constants inline-documented at use site.
@file:Suppress("MagicNumber")

package io.music_assistant.client.webrtc

import co.touchlab.kermit.Logger
import com.shepeliev.webrtckmp.DataChannelState
import io.music_assistant.client.webrtc.model.PeerConnectionStateValue
import io.music_assistant.client.webrtc.model.RemoteId
import io.music_assistant.client.webrtc.model.SignalingMessage
import io.music_assistant.client.webrtc.model.WebRTCConnectionState
import io.music_assistant.client.webrtc.model.WebRTCError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.TimeSource

/**
 * Manages WebRTC connection lifecycle and orchestrates signaling + peer connection.
 *
 * This is the main entry point for WebRTC connections. It coordinates:
 * - SignalingClient: WebSocket connection to signaling server
 * - PeerConnectionWrapper: Native WebRTC peer connection
 * - Data channel management for MA API communication
 *
 * Connection Flow:
 * 1. connect(remoteId) called
 * 2. Connect to signaling server
 * 3. Send Connect message with Remote ID
 * 4. Receive SessionReady (ICE servers, session ID)
 * 5. Initialize peer connection with ICE servers
 * 6. Create SDP offer
 * 7. Send Offer to signaling server
 * 8. Receive Answer from server
 * 9. Set remote answer in peer connection
 * 10. Exchange ICE candidates
 * 11. Data channel "ma-api" opened by server
 * 12. Connected!
 *
 * Usage:
 * ```kotlin
 * val manager = WebRTCConnectionManager(signalingClient, scope)
 *
 * // Observe state
 * manager.connectionState.collect { state ->
 *     when (state) {
 *         is WebRTCConnectionState.Connected -> println("Connected!")
 *         is WebRTCConnectionState.Error -> println("Error: ${state.error}")
 *     }
 * }
 *
 * // Connect
 * manager.connect(RemoteId.parse("PGSVXKGZ-JCFA6-MOH4U-PBH5Q9HY")!!)
 *
 * // Send message over data channel
 * manager.send("""{"type":"command","data":{...}}""")
 *
 * // Disconnect
 * manager.disconnect()
 * ```
 */
class WebRTCConnectionManager(
    private val signalingClient: SignalingClient,
    private val scope: CoroutineScope,
) {
    private val logger = Logger.withTag("WebRTCConnectionManager")
    private val mutex = Mutex()

    private var peerConnection: PeerConnectionWrapper? = null
    private var dataChannel: DataChannelWrapper? = null
    private var sendspinDataChannelInternal: DataChannelWrapper? = null
    private var signalingMessageListenerJob: Job? = null
    private var iceCandidateJob: Job? = null
    private var dataChannelListenerJob: Job? = null
    private var connectionStateJob: Job? = null
    private var messageListenerJob: Job? = null
    private var dataChannelStateJob: Job? = null
    private var connectionTimeoutJob: Job? = null
    private var recoveryTimeoutJob: Job? = null
    private var currentSessionId: String? = null
    private var currentRemoteId: RemoteId? = null

    private val _connectionState =
        MutableStateFlow<WebRTCConnectionState>(WebRTCConnectionState.Idle)
    val connectionState: StateFlow<WebRTCConnectionState> = _connectionState.asStateFlow()

    private val _incomingMessages = MutableSharedFlow<String>(extraBufferCapacity = 100)
    val incomingMessages: SharedFlow<String> = _incomingMessages.asSharedFlow()

    /**
     * Sendspin data channel for audio streaming.
     * Created during SDP negotiation, cannot be recreated dynamically.
     * Null when not connected via WebRTC.
     */
    val sendspinDataChannel: DataChannelWrapper?
        get() = sendspinDataChannelInternal

    /**
     * Connect to Music Assistant server via WebRTC.
     *
     * @param remoteId Remote ID of the Music Assistant server
     */
    suspend fun connect(remoteId: RemoteId) = mutex.withLock {
        if (_connectionState.value !is WebRTCConnectionState.Idle &&
            _connectionState.value !is WebRTCConnectionState.Error
        ) {
            logger.w { "Already connecting or connected" }
            return@withLock
        }

        logger.i { "Starting WebRTC connection" }
        currentRemoteId = remoteId
        _connectionState.value = WebRTCConnectionState.ConnectingToSignaling

        try {
            // Step 1: Connect to signaling server
            signalingClient.connect()

            // Step 2: Listen for signaling messages
            startListeningToSignaling()

            // Step 3: Send ConnectRequest message
            logger.d { "Sending ConnectRequest message" }
            signalingClient.sendMessage(SignalingMessage.ConnectRequest(remoteId = remoteId.rawId))

            // Step 4: Start timeout timer (30s like web client)
            startConnectionTimeout()

            // Subsequent steps handled in signaling message handlers
        } catch (e: Exception) {
            logger.e(e) { "Failed to connect to signaling server" }
            _connectionState.value = WebRTCConnectionState.Error(
                WebRTCError.SignalingError("Failed to connect to signaling server", e),
            )
            cleanup()
        }
    }

    /**
     * Disconnect from WebRTC connection and cleanup resources.
     *
     * @param closeSignaling if true, also closes the shared SignalingClient WebSocket.
     *   Pass false when the signaling session is owned by an outer scope and should
     *   be reused across reconnect attempts.
     */
    suspend fun disconnect(closeSignaling: Boolean = true) = mutex.withLock {
        logger.i { "Disconnecting WebRTC connection (closeSignaling=$closeSignaling)" }
        _connectionState.value = WebRTCConnectionState.Disconnecting
        cleanup()
        if (closeSignaling) signalingClient.disconnect()
        _connectionState.value = WebRTCConnectionState.Idle
    }

    /**
     * Send message over WebRTC data channel.
     * Channel must be open (state is Connected).
     *
     * @param message JSON string to send
     */
    fun send(message: String) {
        dataChannel?.send(message)
    }

    /**
     * Listen for incoming signaling messages and handle WebRTC setup.
     */
    private fun startListeningToSignaling() {
        signalingMessageListenerJob = scope.launch {
            // Mirror signaling connection state to logs for reconnection diagnostics.
            launch {
                signalingClient.connectionState.collect { sigState ->
                    logger.i { "Signaling state: $sigState" }
                }
            }
            signalingClient.incomingMessages.collect { message ->
                handleSignalingMessage(message)
            }
        }
    }

    /**
     * Handle incoming signaling messages.
     */
    private suspend fun handleSignalingMessage(message: SignalingMessage) {
        logger.d { "Received signaling message: ${message.type}" }

        // The SignalingClient WebSocket is reused across reconnect attempts, so stale
        // session-bearing messages from a previous session may arrive at a new manager
        // before its own Connected response. Drop them by sessionId.
        val incomingSession = when (message) {
            is SignalingMessage.Answer -> message.sessionId
            is SignalingMessage.IceCandidate -> message.sessionId
            is SignalingMessage.Error -> message.sessionId
            is SignalingMessage.PeerDisconnected -> message.sessionId
            else -> null
        }
        if (incomingSession != null && currentSessionId != null && incomingSession != currentSessionId) {
            logger.d { "Dropping stale ${message.type} for session $incomingSession (current=$currentSessionId)" }
            return
        }
        if (incomingSession != null && currentSessionId == null && message !is SignalingMessage.Connected) {
            logger.d { "Dropping pre-session ${message.type} (no current session yet)" }
            return
        }

        when (message) {
            is SignalingMessage.Connected -> handleConnected(message)
            is SignalingMessage.Answer -> handleAnswer(message)
            is SignalingMessage.IceCandidate -> handleIceCandidate(message)
            is SignalingMessage.Error -> handleSignalingError(message)
            is SignalingMessage.PeerDisconnected -> handlePeerDisconnected(message)
            is SignalingMessage.Unknown -> logger.w { "Received unknown message type: ${message.type}" }
            else -> logger.d { "Ignoring message type: ${message.type}" }
        }
    }

    /**
     * Start timeout timer for connection. If not connected within 30s, fail.
     */
    private fun startConnectionTimeout() {
        connectionTimeoutJob = scope.launch {
            delay(30_000) // 30 seconds
            if (_connectionState.value !is WebRTCConnectionState.Connected) {
                logger.e { "Connection timeout: failed to establish WebRTC connection within 30s" }
                _connectionState.value = WebRTCConnectionState.Error(
                    WebRTCError.ConnectionError("Connection timeout"),
                )
                cleanup()
            }
        }
    }

    /**
     * Handle Connected: Initialize peer connection and create offer.
     */
    private suspend fun handleConnected(message: SignalingMessage.Connected) {
        logger.i { "Connected. ICE servers: ${message.iceServers.size}" }
        currentSessionId = message.sessionId
        _connectionState.value =
            WebRTCConnectionState.NegotiatingPeerConnection(message.sessionId ?: "")

        // Cancel timeout - we got the connected message
        connectionTimeoutJob?.cancel()
        connectionTimeoutJob = null

        try {
            // Create peer connection (no callbacks needed with flow-based API)
            val pc = PeerConnectionWrapper()
            peerConnection = pc

            // Initialize with ICE servers
            pc.initialize(message.iceServers)

            // Set up flow collectors for peer connection events
            // Collect ICE candidates and send to signaling server
            iceCandidateJob = scope.launch {
                try {
                    pc.iceCandidates.collect { candidate ->
                        logger.d { "ICE candidate gathered, sending to signaling server" }
                        signalingClient.sendMessage(
                            SignalingMessage.IceCandidate(
                                remoteId = currentRemoteId!!.rawId,
                                sessionId = message.sessionId ?: "",
                                data = candidate,
                            ),
                        )
                    }
                } catch (e: Exception) {
                    logger.e(e) { "Error collecting ICE candidates" }
                }
            }

            // Monitor data channels from remote peer
            dataChannelListenerJob = scope.launch {
                try {
                    pc.dataChannels.collect { channel ->
                        logger.i { "Remote data channel received: ${channel.label}" }
                        // If server creates "ma-api" channel, use it (replaces client-created one)
                        if (channel.label == "ma-api") {
                            logger.i { "Server created ma-api channel - using it for communication" }
                            setupDataChannel(channel, message.sessionId ?: "")
                        }
                    }
                } catch (e: Exception) {
                    logger.e(e) { "Error collecting data channels" }
                }
            }

            // Monitor connection state for failures
            connectionStateJob = scope.launch {
                try {
                    var lastTransition = TimeSource.Monotonic.markNow()
                    var prevState: PeerConnectionStateValue? = null
                    pc.connectionState.collect { state ->
                        val deltaMs = lastTransition.elapsedNow().inWholeMilliseconds
                        logger.i { "PC state: ${prevState ?: "<initial>"} → $state (+${deltaMs}ms)" }
                        lastTransition = TimeSource.Monotonic.markNow()
                        prevState = state
                        when (state) {
                            PeerConnectionStateValue.FAILED -> {
                                logger.w { "ICE FAILED — attempting ICE restart before giving up" }
                                tryIceRestartOrFail("ICE connection failed")
                            }

                            PeerConnectionStateValue.DISCONNECTED -> {
                                logger.w { "ICE DISCONNECTED — entering recovery (grace=${ICE_RECOVERY_GRACE_MS}ms, will attempt ICE restart)" }
                                tryIceRestartOrFail("ICE connection disconnected")
                            }

                            PeerConnectionStateValue.CONNECTED -> {
                                // Recovered (either passively or via ICE restart) before the grace window expired
                                if (recoveryTimeoutJob != null) {
                                    logger.i { "ICE recovered — cancelling grace window" }
                                    recoveryTimeoutJob?.cancel()
                                    recoveryTimeoutJob = null
                                    val remote = currentRemoteId
                                    val session = currentSessionId
                                    if (remote != null && session != null) {
                                        _connectionState.value = WebRTCConnectionState.Connected(
                                            sessionId = session,
                                            remoteId = remote,
                                        )
                                    }
                                }
                            }

                            PeerConnectionStateValue.CLOSED -> {
                                logger.i { "PC CLOSED" }
                                recoveryTimeoutJob?.cancel()
                                recoveryTimeoutJob = null
                                if (_connectionState.value !is WebRTCConnectionState.Idle) {
                                    _connectionState.value = WebRTCConnectionState.Error(
                                        WebRTCError.ConnectionError("ICE connection closed"),
                                    )
                                    cleanup()
                                }
                            }

                            else -> {
                                // Normal states (NEW, CONNECTING)
                            }
                        }
                    }
                } catch (e: Exception) {
                    logger.e(e) { "Error collecting connection state" }
                }
            }

            // Create data channels BEFORE offer (required: adds m=application to SDP)
            logger.d { "Creating ma-api data channel" }
            // ma-api: reliable ordered delivery for JSON commands
            val channel = pc.createDataChannel(
                label = "ma-api",
                ordered = true,
                maxRetransmits = -1,  // unlimited retransmits for reliability
            )
            setupDataChannel(channel, message.sessionId ?: "")

            logger.d { "Creating sendspin data channel" }
            // sendspin: fully reliable + ordered (same as ma-api)
            // Previously used maxRetransmits=0 which caused 15% packet loss and constant
            // audio distortion. SIGSEGV with retransmission was likely caused by native
            // SCTP buffer reuse while SharedFlow held a reference — fixed by copying
            // ByteArray in DataChannelWrapper before emitting.
            val sendspinChannel = pc.createDataChannel(
                label = "sendspin",
                ordered = true,
            )
            setupSendspinDataChannel(sendspinChannel)

            // Create SDP offer (now includes m=application section)
            logger.d { "Creating SDP offer" }
            val offer = pc.createOffer()

            // Send offer to signaling server
            logger.d { "Sending SDP offer" }
            signalingClient.sendMessage(
                SignalingMessage.Offer(
                    remoteId = currentRemoteId!!.rawId,
                    sessionId = message.sessionId ?: "",
                    data = offer,
                ),
            )

            _connectionState.value =
                WebRTCConnectionState.GatheringIceCandidates(message.sessionId ?: "")
        } catch (e: Exception) {
            logger.e(e) { "Failed to initialize peer connection" }
            _connectionState.value = WebRTCConnectionState.Error(
                WebRTCError.PeerConnectionError("Failed to initialize peer connection", e),
            )
            cleanup()
        }
    }

    /**
     * Handle Answer: Set remote description.
     */
    private suspend fun handleAnswer(message: SignalingMessage.Answer) {
        logger.i { "Received SDP answer" }
        val pc = peerConnection

        if (pc == null) {
            logger.e { "Received answer but no peer connection exists" }
            return
        }

        try {
            pc.setRemoteAnswer(message.data)
            logger.d { "Remote answer set successfully" }
        } catch (e: Exception) {
            logger.e(e) { "Failed to set remote answer" }
            _connectionState.value = WebRTCConnectionState.Error(
                WebRTCError.PeerConnectionError("Failed to set remote answer", e),
            )
            cleanup()
        }
    }

    /**
     * Handle ICE candidate from remote peer.
     */
    private suspend fun handleIceCandidate(message: SignalingMessage.IceCandidate) {
        logger.d { "Received ICE candidate" }
        peerConnection?.let {
            try {
                it.addIceCandidate(message.data)
            } catch (e: Exception) {
                logger.e(e) { "Failed to add ICE candidate" }
            }
        } ?: run {
            logger.e { "Received ICE candidate but no peer connection exists" }
        }
    }

    /**
     * Handle signaling error.
     */
    private fun handleSignalingError(message: SignalingMessage.Error) {
        logger.e { "Signaling error: ${message.error}" }
        _connectionState.value = WebRTCConnectionState.Error(
            WebRTCError.SignalingError(message.error),
        )
        scope.launch { cleanup() }
    }

    /**
     * Handle peer disconnected notification.
     */
    private fun handlePeerDisconnected(message: SignalingMessage.PeerDisconnected) {
        logger.w { "Remote peer disconnected: ${message.sessionId}" }
        _connectionState.value = WebRTCConnectionState.Error(
            WebRTCError.ConnectionError("Remote peer disconnected"),
        )
        scope.launch { cleanup() }
    }

    /**
     * Set up the ma-api data channel: message listener and state monitoring.
     */
    private fun setupDataChannel(channel: DataChannelWrapper, sessionId: String) {
        // Cleanup previous channel if exists (reconnection edge case)
        messageListenerJob?.cancel()
        dataChannelStateJob?.cancel()
        val oldChannel = dataChannel
        if (oldChannel != null) {
            scope.launch { oldChannel.close() }
        }

        dataChannel = channel

        // Collect incoming messages from the flow
        messageListenerJob = scope.launch {
            try {
                channel.messages.collect { msg ->
                    _incomingMessages.emit(msg)
                }
            } catch (e: Exception) {
                logger.e(e) { "Error receiving messages from data channel" }
            }
        }

        // Monitor state changes
        dataChannelStateJob = scope.launch {
            try {
                channel.state.collect { state ->
                    if (state == DataChannelState.Open) {
                        _connectionState.value = WebRTCConnectionState.Connected(
                            sessionId = sessionId,
                            remoteId = currentRemoteId!!,
                        )
                    }
                }
            } catch (e: Exception) {
                logger.e(e) { "Error monitoring data channel state" }
            }
        }
    }

    /**
     * Set up the sendspin data channel.
     *
     * The channel is stored and monitored, but message handling is delegated to SendspinClient
     * via the SendspinTransport abstraction. This method just ensures the channel is available
     * and logs its state changes.
     */
    private fun setupSendspinDataChannel(channel: DataChannelWrapper) {
        // Close previous sendspin channel if exists (reconnection edge case)
        val oldChannel = sendspinDataChannelInternal
        if (oldChannel != null) {
            scope.launch { oldChannel.close() }
        }

        sendspinDataChannelInternal = channel

        // Monitor state changes for logging
        scope.launch {
            try {
                channel.state.collect { state ->
                    logger.d { "Sendspin data channel state: $state" }
                    if (state == DataChannelState.Open) {
                        logger.i { "Sendspin data channel ready for use" }
                    }
                }
            } catch (e: Exception) {
                logger.e(e) { "Error monitoring sendspin data channel state" }
            }
        }
    }

    /**
     * Cleanup resources.
     */
    private suspend fun cleanup() {
        signalingMessageListenerJob?.cancel()
        signalingMessageListenerJob = null

        iceCandidateJob?.cancel()
        iceCandidateJob = null

        dataChannelListenerJob?.cancel()
        dataChannelListenerJob = null

        connectionStateJob?.cancel()
        connectionStateJob = null

        messageListenerJob?.cancel()
        messageListenerJob = null

        dataChannelStateJob?.cancel()
        dataChannelStateJob = null

        connectionTimeoutJob?.cancel()
        connectionTimeoutJob = null

        recoveryTimeoutJob?.cancel()
        recoveryTimeoutJob = null

        dataChannel?.close()
        dataChannel = null

        sendspinDataChannelInternal?.close()
        sendspinDataChannelInternal = null

        peerConnection?.close()
        peerConnection = null

        // Signaling lifecycle is owned by the outer Transport so it can survive PC failures
        // and be reused across reconnect attempts. Use disconnect(closeSignaling=true) to
        // tear it down explicitly.
        currentSessionId = null
    }

    /**
     * Attempt to recover the existing peer connection via ICE restart over the still-alive
     * signaling session. Falls through to full failure (and cleanup) if the recovery window
     * expires without reaching CONNECTED.
     *
     * Same path is used for both DISCONNECTED and FAILED — DISCONNECTED is just an earlier
     * signal; FAILED can also recover via ICE restart if the underlying signaling is alive.
     */
    private fun tryIceRestartOrFail(errorMessage: String) {
        if (recoveryTimeoutJob != null) {
            logger.d { "Recovery already in progress — skipping duplicate trigger" }
            return
        }
        val remote = currentRemoteId
        val session = currentSessionId
        val pc = peerConnection
        if (remote == null || session == null || pc == null) {
            logger.w { "No active session/PC to recover — failing immediately" }
            _connectionState.value = WebRTCConnectionState.Error(
                WebRTCError.ConnectionError(errorMessage),
            )
            scope.launch { cleanup() }
            return
        }

        _connectionState.value = WebRTCConnectionState.Recovering(
            sessionId = session,
            remoteId = remote,
        )

        val recoveryStart = TimeSource.Monotonic.markNow()
        recoveryTimeoutJob = scope.launch {
            // Fire ICE restart attempt. The signaling client may be dead (network blip) —
            // we still set up the timeout and let the restart fail loudly. If the network
            // returns within the window and the connection recovers naturally, we'll see
            // CONNECTED and cancel this timeout.
            launch {
                try {
                    if (!signalingClient.isConnected) {
                        logger.w { "Cannot send ICE restart offer: signaling not connected" }
                        return@launch
                    }
                    logger.i { "Sending ICE restart offer for session=$session" }
                    val offer = pc.createOffer(iceRestart = true)
                    signalingClient.sendMessage(
                        SignalingMessage.Offer(
                            remoteId = remote.rawId,
                            sessionId = session,
                            data = offer,
                        ),
                    )
                    logger.i { "ICE restart offer sent — awaiting server answer + new ICE pairing" }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    logger.e(e) { "ICE restart attempt threw — will rely on grace window" }
                }
            }

            delay(ICE_RECOVERY_GRACE_MS)
            recoveryTimeoutJob = null
            if (_connectionState.value is WebRTCConnectionState.Recovering) {
                logger.w {
                    "Recovery window expired (${recoveryStart.elapsedNow().inWholeMilliseconds}ms) — failing connection"
                }
                _connectionState.value = WebRTCConnectionState.Error(
                    WebRTCError.ConnectionError(errorMessage),
                )
                cleanup()
            }
        }
    }

    companion object {
        /**
         * Recovery window: ICE restart is attempted at the start, and ICE must reach
         * CONNECTED within this window or we give up and full-reconnect. Sized to cover
         * offer/answer round-trip + new ICE gathering + connectivity checks on the new
         * network interface (e.g. WiFi → mobile handoff).
         */
        private const val ICE_RECOVERY_GRACE_MS = 7000L
    }
}
