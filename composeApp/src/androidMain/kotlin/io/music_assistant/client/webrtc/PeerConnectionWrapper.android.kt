package io.music_assistant.client.webrtc

import co.touchlab.kermit.Logger
import com.shepeliev.webrtckmp.IceCandidate
import com.shepeliev.webrtckmp.IceServer as RtcIceServer
import com.shepeliev.webrtckmp.OfferAnswerOptions
import com.shepeliev.webrtckmp.PeerConnection
import com.shepeliev.webrtckmp.PeerConnectionState
import com.shepeliev.webrtckmp.RtcConfiguration
import com.shepeliev.webrtckmp.SessionDescription
import com.shepeliev.webrtckmp.SessionDescriptionType
import com.shepeliev.webrtckmp.onConnectionStateChange
import com.shepeliev.webrtckmp.onDataChannel
import com.shepeliev.webrtckmp.onIceCandidate
import io.music_assistant.client.webrtc.model.IceCandidateData
import io.music_assistant.client.webrtc.model.IceServer
import io.music_assistant.client.webrtc.model.PeerConnectionStateValue
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference

/**
 * Android implementation of PeerConnectionWrapper using webrtc-kmp library.
 *
 * Uses the com.shepeliev.webrtckmp package which provides suspend function APIs
 * for WebRTC operations.
 */
actual class PeerConnectionWrapper actual constructor() {
    private val logger = Logger.withTag("PeerConnectionWrapper[Android]")
    private val peerConnection = AtomicReference<PeerConnection?>(null)
    private val eventScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _iceCandidates = MutableSharedFlow<IceCandidateData>(extraBufferCapacity = 10)
    actual val iceCandidates: Flow<IceCandidateData> = _iceCandidates.asSharedFlow()

    private val _dataChannels = MutableSharedFlow<DataChannelWrapper>(extraBufferCapacity = 5)
    actual val dataChannels: Flow<DataChannelWrapper> = _dataChannels.asSharedFlow()

    private val _connectionState = MutableStateFlow(PeerConnectionStateValue.NEW)
    actual val connectionState: StateFlow<PeerConnectionStateValue> = _connectionState.asStateFlow()

    actual suspend fun initialize(iceServers: List<IceServer>) {
        logger.i { "Initializing peer connection with ${iceServers.size} ICE servers" }

        try {
            // Convert our IceServer model to webrtc-kmp format
            val rtcIceServers = iceServers.map { server ->
                RtcIceServer(
                    urls = server.urls,
                    username = server.username ?: "",
                    password = server.credential ?: ""
                )
            }

            // Create RTC configuration
            val config = RtcConfiguration(iceServers = rtcIceServers)

            // Create peer connection
            val pc = PeerConnection(config)

            peerConnection.set(pc)

            // Set up event listeners in background
            eventScope.launch {
                try {
                    pc.onIceCandidate.collect { candidate ->
                        logger.d { "ICE candidate gathered" }
                        _iceCandidates.emit(
                            IceCandidateData(
                                candidate = candidate.candidate,
                                sdpMid = candidate.sdpMid,
                                sdpMLineIndex = candidate.sdpMLineIndex
                            )
                        )
                    }
                } catch (e: Exception) {
                    logger.e(e) { "ICE candidate flow failed" }
                }
            }

            eventScope.launch {
                try {
                    pc.onDataChannel.collect { channel ->
                        logger.i { "Data channel received: ${channel.label}" }
                        _dataChannels.emit(DataChannelWrapper(channel))
                    }
                } catch (e: Exception) {
                    logger.e(e) { "Data channel flow failed" }
                }
            }

            eventScope.launch {
                try {
                    pc.onConnectionStateChange
                        .map { state -> state.toCommon() }
                        .collect { state ->
                            logger.d { "Connection state: $state" }
                            _connectionState.value = state
                        }
                } catch (e: Exception) {
                    logger.e(e) { "Connection state flow failed" }
                }
            }

            logger.d { "Peer connection initialized" }
        } catch (e: Exception) {
            logger.e(e) { "Failed to initialize peer connection" }
            eventScope.cancel()
            peerConnection.set(null)
            throw e
        }
    }

    /**
     * Convert platform-specific PeerConnectionState to common enum.
     */
    private fun PeerConnectionState.toCommon(): PeerConnectionStateValue = when (this) {
        PeerConnectionState.New -> PeerConnectionStateValue.NEW
        PeerConnectionState.Connecting -> PeerConnectionStateValue.CONNECTING
        PeerConnectionState.Connected -> PeerConnectionStateValue.CONNECTED
        PeerConnectionState.Disconnected -> PeerConnectionStateValue.DISCONNECTED
        PeerConnectionState.Failed -> PeerConnectionStateValue.FAILED
        PeerConnectionState.Closed -> PeerConnectionStateValue.CLOSED
    }

    actual suspend fun createOffer(): io.music_assistant.client.webrtc.model.SessionDescription {
        val pc = peerConnection.get() ?: throw IllegalStateException("Peer connection not initialized")
        logger.d { "Creating SDP offer" }

        // Create offer with options (no audio/video)
        val options = OfferAnswerOptions(
            offerToReceiveAudio = false,
            offerToReceiveVideo = false
        )

        val offer = pc.createOffer(options)

        // Set local description
        pc.setLocalDescription(offer)

        logger.d { "SDP offer created and set as local description" }

        // Convert to our model
        return io.music_assistant.client.webrtc.model.SessionDescription(
            sdp = offer.sdp,
            type = "offer"
        )
    }

    actual suspend fun setRemoteAnswer(answer: io.music_assistant.client.webrtc.model.SessionDescription) {
        val pc = peerConnection.get() ?: throw IllegalStateException("Peer connection not initialized")
        logger.d { "Setting remote answer" }

        val sdp = SessionDescription(
            type = SessionDescriptionType.Answer,
            sdp = answer.sdp
        )

        pc.setRemoteDescription(sdp)

        logger.d { "Remote answer set successfully" }
    }

    actual suspend fun addIceCandidate(candidate: IceCandidateData) {
        val pc = peerConnection.get() ?: throw IllegalStateException("Peer connection not initialized")
        logger.d { "Adding ICE candidate" }

        val iceCandidate = IceCandidate(
            sdpMid = candidate.sdpMid ?: "",
            sdpMLineIndex = candidate.sdpMLineIndex ?: 0,
            candidate = candidate.candidate
        )

        val success = pc.addIceCandidate(iceCandidate)

        if (success) {
            logger.d { "ICE candidate added successfully" }
        } else {
            logger.w { "Failed to add ICE candidate" }
        }
    }

    actual fun createDataChannel(
        label: String,
        ordered: Boolean,
        maxRetransmits: Int
    ): DataChannelWrapper {
        val pc = peerConnection.get() ?: throw IllegalStateException("Peer connection not initialized")
        logger.d { "Creating data channel: $label (ordered=$ordered, maxRetransmits=$maxRetransmits)" }

        val dataChannel = pc.createDataChannel(
            label = label,
            ordered = ordered,
            maxRetransmits = maxRetransmits
        ) ?: throw IllegalStateException("Failed to create data channel")

        return DataChannelWrapper(dataChannel)
    }

    actual suspend fun close() {
        logger.i { "Closing peer connection" }
        eventScope.cancel()
        peerConnection.getAndSet(null)?.close()
    }
}
