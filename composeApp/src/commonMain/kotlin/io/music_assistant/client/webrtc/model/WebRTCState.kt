package io.music_assistant.client.webrtc.model

/**
 * Overall WebRTC connection state for UI display and logic.
 */
sealed class WebRTCConnectionState {
    /** Initial state, no connection attempt */
    data object Idle : WebRTCConnectionState()

    /** Connecting to signaling server */
    data object ConnectingToSignaling : WebRTCConnectionState()

    /** Negotiating WebRTC peer connection (exchanging SDP offers/answers) */
    data class NegotiatingPeerConnection(val sessionId: String) : WebRTCConnectionState()

    /** Gathering ICE candidates for NAT traversal */
    data class GatheringIceCandidates(val sessionId: String) : WebRTCConnectionState()

    /** WebRTC peer connection established and data channels open */
    data class Connected(
        val sessionId: String,
        val remoteId: RemoteId,
    ) : WebRTCConnectionState()

    /** Connection failed or error occurred */
    data class Error(val error: WebRTCError) : WebRTCConnectionState()

    /** Disconnecting from WebRTC */
    data object Disconnecting : WebRTCConnectionState()
}

/**
 * Categorized errors for WebRTC connections.
 */
sealed class WebRTCError {
    /** Error connecting to or communicating with signaling server */
    data class SignalingError(val message: String, val cause: Throwable? = null) : WebRTCError()

    /** Error during WebRTC peer connection establishment */
    data class PeerConnectionError(val message: String, val cause: Throwable? = null) :
        WebRTCError()

    /** Generic connection error */
    data class ConnectionError(val message: String, val cause: Throwable? = null) : WebRTCError()
}
