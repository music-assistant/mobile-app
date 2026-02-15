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
    private val scope: CoroutineScope
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
    private var currentSessionId: String? = null
    private var currentRemoteId: RemoteId? = null

    private val _connectionState =
        MutableStateFlow<WebRTCConnectionState>(WebRTCConnectionState.Idle)
    val connectionState: StateFlow<WebRTCConnectionState> = _connectionState.asStateFlow()

    private val _incomingMessages = MutableSharedFlow<String>(extraBufferCapacity = 100)
    val incomingMessages: SharedFlow<String> = _incomingMessages.asSharedFlow()

    /**
     * Sendspin data channel for audio streaming.
     * Created during WebRTC peer connection setup, available when connected.
     * Returns null when not connected via WebRTC.
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

        logger.i { "Starting WebRTC connection to Remote ID: $remoteId" }
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
                WebRTCError.SignalingError("Failed to connect to signaling server", e)
            )
            cleanup()
        }
    }

    /**
     * Disconnect from WebRTC connection and cleanup resources.
     */
    suspend fun disconnect() = mutex.withLock {
        logger.i { "Disconnecting WebRTC connection" }
        _connectionState.value = WebRTCConnectionState.Disconnecting
        cleanup()
        _connectionState.value = WebRTCConnectionState.Idle
    }

    /**
     * Send message over WebRTC data channel.
     * Channel must be open (state is Connected).
     *
     * @param message JSON string to send
     */
    fun send(message: String) {
        dataChannel?.also { channel ->
            logger.e { "🔵 SEND REQUEST - Channel: ${channel.label}, State: ${channel.state.value}, Length: ${message.length}" }
            logger.e { "🔵 SEND CONTENT: ${message.take(300)}" }
        }?.send(message)

    }

    /**
     * Listen for incoming signaling messages and handle WebRTC setup.
     */
    private fun startListeningToSignaling() {
        signalingMessageListenerJob = scope.launch {
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
                    WebRTCError.ConnectionError("Connection timeout")
                )
                cleanup()
            }
        }
    }

    /**
     * Handle Connected: Initialize peer connection and create offer.
     */
    private suspend fun handleConnected(message: SignalingMessage.Connected) {
        logger.i { "Connected. Session ID: ${message.sessionId}, ICE servers: ${message.iceServers.size}" }
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
                                data = candidate
                            )
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
                    pc.connectionState.collect { state ->
                        logger.d { "Peer connection state: $state" }
                        when (state) {
                            PeerConnectionStateValue.FAILED -> {
                                logger.e { "ICE connection failed" }
                                _connectionState.value = WebRTCConnectionState.Error(
                                    WebRTCError.ConnectionError("ICE connection failed")
                                )
                                cleanup()
                            }

                            PeerConnectionStateValue.DISCONNECTED -> {
                                logger.w { "ICE connection disconnected" }
                                // Treat disconnected as immediate failure to enable fast reconnection
                                // Even if temporary, reconnection will succeed quickly
                                _connectionState.value = WebRTCConnectionState.Error(
                                    WebRTCError.ConnectionError("ICE connection disconnected")
                                )
                                cleanup()
                            }

                            PeerConnectionStateValue.CLOSED -> {
                                logger.i { "ICE connection closed" }
                                if (_connectionState.value !is WebRTCConnectionState.Idle) {
                                    _connectionState.value = WebRTCConnectionState.Error(
                                        WebRTCError.ConnectionError("ICE connection closed")
                                    )
                                    cleanup()
                                }
                            }

                            else -> {
                                // Normal states (NEW, CONNECTING, CONNECTED)
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
                maxRetransmits = -1  // unlimited retransmits for reliability
            )
            setupDataChannel(channel, message.sessionId ?: "")

            logger.d { "Creating sendspin data channel" }
            // sendspin: unreliable unordered delivery for real-time audio streaming
            // Better to skip a lost chunk than stall the entire stream on cellular networks
            val sendspinChannel = pc.createDataChannel(
                label = "sendspin",
                ordered = false,       // don't wait for lost packets
                maxRetransmits = 0     // don't retransmit - move on
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
                    data = offer
                )
            )

            _connectionState.value =
                WebRTCConnectionState.GatheringIceCandidates(message.sessionId ?: "")

        } catch (e: Exception) {
            logger.e(e) { "Failed to initialize peer connection" }
            _connectionState.value = WebRTCConnectionState.Error(
                WebRTCError.PeerConnectionError("Failed to initialize peer connection", e)
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
                WebRTCError.PeerConnectionError("Failed to set remote answer", e)
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
            WebRTCError.SignalingError(message.error)
        )
        scope.launch { cleanup() }
    }

    /**
     * Handle peer disconnected notification.
     */
    private fun handlePeerDisconnected(message: SignalingMessage.PeerDisconnected) {
        logger.w { "Remote peer disconnected: ${message.sessionId}" }
        _connectionState.value = WebRTCConnectionState.Error(
            WebRTCError.ConnectionError("Remote peer disconnected")
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
                            remoteId = currentRemoteId!!
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

        dataChannel?.close()
        dataChannel = null

        sendspinDataChannelInternal?.close()
        sendspinDataChannelInternal = null

        peerConnection?.close()
        peerConnection = null

        signalingClient.disconnect()

        currentSessionId = null
    }
}
