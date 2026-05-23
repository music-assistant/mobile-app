package io.music_assistant.client.webrtc

import co.touchlab.kermit.Logger
import io.ktor.client.webrtc.DataChannelEvent
import io.ktor.client.webrtc.WebRtc
import io.ktor.client.webrtc.WebRtcClient
import io.ktor.client.webrtc.WebRtcPeerConnection
import io.ktor.utils.io.ExperimentalKtorApi
import io.music_assistant.client.webrtc.model.IceCandidateData
import io.music_assistant.client.webrtc.model.IceServer
import io.music_assistant.client.webrtc.model.PeerConnectionStateValue
import io.music_assistant.client.webrtc.model.SessionDescription
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * WebRTC peer connection wrapper backed by `io.ktor:ktor-client-webrtc`.
 *
 * Lifecycle:
 *  1. Create instance — pulls `WebRtcClient` (the Ktor engine, app-singleton) from Koin.
 *  2. `initialize(iceServers)` — opens the native peer connection.
 *  3. Collect [iceCandidates], [dataChannels], [connectionState].
 *  4. `createOffer()` / `setRemoteAnswer()` for SDP negotiation.
 *  5. `addIceCandidate()` for each remote ICE candidate received via signaling.
 *  6. `close()` when done.
 */
@OptIn(ExperimentalKtorApi::class)
class PeerConnectionWrapper : KoinComponent {
    private val logger = Logger.withTag("PeerConnectionWrapper")
    private val webRtcClient: WebRtcClient by inject()

    private var peerConnection: WebRtcPeerConnection? = null
    private val eventScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Tracks channels created locally so the `dataChannelEvents` collector only surfaces
    // REMOTE-created channels on the `dataChannels` Flow — matching the prior webrtc-kmp
    // `onDataChannel` semantics that this wrapper exposed.
    //
    // Concurrency note: written by createDataChannel, read by the events collector. In
    // practice no race fires because Open events arrive only after SDP negotiation
    // completes (seconds after createDataChannel returns), so the channel is already
    // in the set by then.
    private val locallyCreatedChannels = mutableSetOf<io.ktor.client.webrtc.WebRtcDataChannel>()

    private val _iceCandidates = MutableSharedFlow<IceCandidateData>(extraBufferCapacity = 10)
    val iceCandidates: Flow<IceCandidateData> = _iceCandidates.asSharedFlow()

    private val _dataChannels = MutableSharedFlow<DataChannelWrapper>(extraBufferCapacity = 5)
    val dataChannels: Flow<DataChannelWrapper> = _dataChannels.asSharedFlow()

    private val _connectionState = MutableStateFlow(PeerConnectionStateValue.NEW)
    val connectionState: StateFlow<PeerConnectionStateValue> = _connectionState.asStateFlow()

    suspend fun initialize(iceServers: List<IceServer>) {
        logger.i { "Initializing peer connection with ${iceServers.size} ICE servers" }
        try {
            val pc = webRtcClient.createPeerConnection {
                this.iceServers = iceServers.map { server ->
                    WebRtc.IceServer(
                        urls = server.urls,
                        username = server.username,
                        credential = server.credential,
                    )
                }
            }
            peerConnection = pc

            eventScope.launch {
                try {
                    pc.iceCandidates.collect { candidate ->
                        logger.d { "ICE candidate gathered" }
                        _iceCandidates.emit(
                            IceCandidateData(
                                candidate = candidate.candidate,
                                sdpMid = candidate.sdpMid,
                                sdpMLineIndex = candidate.sdpMLineIndex,
                            ),
                        )
                    }
                } catch (e: Exception) {
                    logger.e(e) { "ICE candidate flow failed" }
                }
            }

            eventScope.launch {
                try {
                    pc.dataChannelEvents.collect { event ->
                        if (event is DataChannelEvent.Open) {
                            val ch = event.channel
                            if (!locallyCreatedChannels.contains(ch)) {
                                logger.i { "Remote data channel received: ${ch.label}" }
                                _dataChannels.emit(DataChannelWrapper(ch, pc.dataChannelEvents))
                            }
                        }
                    }
                } catch (e: Exception) {
                    logger.e(e) { "Data channel events flow failed" }
                }
            }

            eventScope.launch {
                try {
                    pc.state.collect { state ->
                        val mapped = state.toCommon()
                        logger.d { "Connection state: $state -> $mapped" }
                        _connectionState.value = mapped
                    }
                } catch (e: Exception) {
                    logger.e(e) { "Connection state flow failed" }
                }
            }

            logger.d { "Peer connection initialized" }
        } catch (e: Exception) {
            logger.e(e) { "Failed to initialize peer connection" }
            eventScope.cancel()
            peerConnection = null
            throw e
        }
    }

    suspend fun createOffer(): SessionDescription {
        val pc = peerConnection ?: error("Peer connection not initialized")
        logger.d { "Creating SDP offer" }
        val offer = pc.createOffer()
        pc.setLocalDescription(offer)
        return SessionDescription(sdp = offer.sdp, type = "offer")
    }

    suspend fun setRemoteAnswer(answer: SessionDescription) {
        val pc = peerConnection ?: error("Peer connection not initialized")
        logger.d { "Setting remote answer" }
        pc.setRemoteDescription(
            WebRtc.SessionDescription(
                type = WebRtc.SessionDescriptionType.ANSWER,
                sdp = answer.sdp,
            ),
        )
    }

    suspend fun addIceCandidate(candidate: IceCandidateData) {
        val pc = peerConnection ?: error("Peer connection not initialized")
        pc.addIceCandidate(
            WebRtc.IceCandidate(
                candidate = candidate.candidate,
                sdpMid = candidate.sdpMid ?: "",
                sdpMLineIndex = candidate.sdpMLineIndex ?: 0,
            ),
        )
    }

    suspend fun createDataChannel(
        label: String,
        ordered: Boolean = true,
        maxRetransmits: Int = -1,
    ): DataChannelWrapper {
        val pc = peerConnection ?: error("Peer connection not initialized")
        logger.d { "Creating data channel: $label (ordered=$ordered, maxRetransmits=$maxRetransmits)" }
        val ch = pc.createDataChannel(label) {
            this.ordered = ordered
            // Ktor uses Int? — -1 from our common API means "unlimited" (null for Ktor).
            this.maxRetransmits = if (maxRetransmits < 0) null else maxRetransmits
        }
        locallyCreatedChannels.add(ch)
        return DataChannelWrapper(ch, pc.dataChannelEvents)
    }

    fun close() {
        logger.i { "Closing peer connection" }
        eventScope.cancel()
        peerConnection?.close()
        peerConnection = null
        locallyCreatedChannels.clear()
    }
}

private fun WebRtc.ConnectionState.toCommon(): PeerConnectionStateValue = when (this) {
    WebRtc.ConnectionState.NEW -> PeerConnectionStateValue.NEW
    WebRtc.ConnectionState.CONNECTING -> PeerConnectionStateValue.CONNECTING
    WebRtc.ConnectionState.CONNECTED -> PeerConnectionStateValue.CONNECTED
    WebRtc.ConnectionState.DISCONNECTED -> PeerConnectionStateValue.DISCONNECTED
    WebRtc.ConnectionState.FAILED -> PeerConnectionStateValue.FAILED
    WebRtc.ConnectionState.CLOSED -> PeerConnectionStateValue.CLOSED
}
