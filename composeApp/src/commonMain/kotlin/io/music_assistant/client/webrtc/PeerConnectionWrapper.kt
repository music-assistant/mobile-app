package io.music_assistant.client.webrtc

import io.music_assistant.client.webrtc.model.IceCandidateData
import io.music_assistant.client.webrtc.model.IceServer
import io.music_assistant.client.webrtc.model.PeerConnectionStateValue
import io.music_assistant.client.webrtc.model.SessionDescription
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Platform-specific WebRTC peer connection wrapper.
 *
 * Abstracts webrtc-kmp library's expect/actual PeerConnection class.
 * This wrapper provides a common interface for WebRTCConnectionManager
 * while delegating to platform-specific WebRTC implementations.
 *
 * Lifecycle:
 * 1. Create instance (no callbacks needed)
 * 2. Call initialize(iceServers) to set up connection
 * 3. Collect flows for events (iceCandidates, dataChannels, connectionState)
 * 4. Call createOffer() to generate SDP offer
 * 5. Exchange offer/answer via signaling
 * 6. Call setRemoteAnswer() with server's SDP answer
 * 7. ICE candidates arrive via iceCandidates flow
 * 8. Server creates data channel → emitted via dataChannels flow
 * 9. Connection state changes → tracked via connectionState flow
 * 10. Call close() when done
 *
 * Example:
 * ```kotlin
 * val pc = PeerConnectionWrapper()
 * pc.initialize(iceServers)
 *
 * // Collect events
 * launch { pc.iceCandidates.collect { candidate -> sendToSignaling(candidate) } }
 * launch { pc.dataChannels.collect { channel -> setupChannel(channel) } }
 * launch {
 *     pc.connectionState.collect { state ->
 *         when (state) {
 *             PeerConnectionStateValue.FAILED -> handleError()
 *             PeerConnectionStateValue.CONNECTED -> handleSuccess()
 *             else -> {}
 *         }
 *     }
 * }
 * ```
 */
expect class PeerConnectionWrapper() {
    /**
     * Flow of ICE candidates discovered during connection establishment.
     * Emit each candidate to the remote peer via signaling.
     */
    val iceCandidates: Flow<IceCandidateData>

    /**
     * Flow of data channels created by remote peer.
     * Emits when the remote peer creates a data channel.
     */
    val dataChannels: Flow<DataChannelWrapper>

    /**
     * Current connection state.
     * Use to monitor connection progress and detect failures.
     */
    val connectionState: StateFlow<PeerConnectionStateValue>

    /**
     * Initialize peer connection with ICE servers from signaling server.
     * Must be called before createOffer().
     *
     * @param iceServers STUN/TURN servers for NAT traversal
     */
    suspend fun initialize(iceServers: List<IceServer>)

    /**
     * Create SDP offer to send to remote peer via signaling.
     *
     * @return SessionDescription with type "offer" and SDP string
     */
    suspend fun createOffer(): SessionDescription

    /**
     * Set remote peer's SDP answer received via signaling.
     *
     * @param answer SessionDescription from remote peer
     */
    suspend fun setRemoteAnswer(answer: SessionDescription)

    /**
     * Add ICE candidate received from remote peer via signaling.
     * Called multiple times as candidates are discovered.
     *
     * @param candidate ICE candidate data
     */
    suspend fun addIceCandidate(candidate: IceCandidateData)

    /**
     * Create outgoing data channel.
     * Note: For Music Assistant, server creates the channel, so this is rarely used.
     *
     * @param label Channel label (e.g., "ma-api", "sendspin")
     * @param ordered If true, messages arrive in order (reliable). If false, unreliable delivery (better for real-time audio)
     * @param maxRetransmits Max retransmission attempts (-1 = unlimited). Use 0 for real-time streams to avoid stalls.
     * @return DataChannelWrapper for the created channel
     */
    fun createDataChannel(
        label: String,
        ordered: Boolean = true,
        maxRetransmits: Int = -1
    ): DataChannelWrapper

    /**
     * Close peer connection and cleanup resources.
     */
    suspend fun close()
}
