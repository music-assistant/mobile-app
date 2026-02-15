package io.music_assistant.client.webrtc

import co.touchlab.kermit.Logger
import io.music_assistant.client.webrtc.model.IceCandidateData
import io.music_assistant.client.webrtc.model.IceServer
import io.music_assistant.client.webrtc.model.PeerConnectionStateValue
import io.music_assistant.client.webrtc.model.SessionDescription
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow

/**
 * iOS implementation of PeerConnectionWrapper.
 *
 * TODO: Implement using webrtc-kmp iOS support
 */
actual class PeerConnectionWrapper actual constructor() {
    private val logger = Logger.withTag("PeerConnectionWrapper[iOS]")

    actual val iceCandidates: Flow<IceCandidateData> = emptyFlow()

    actual val dataChannels: Flow<DataChannelWrapper> = emptyFlow()

    private val _connectionState = MutableStateFlow(PeerConnectionStateValue.NEW)
    actual val connectionState: StateFlow<PeerConnectionStateValue> = _connectionState.asStateFlow()

    actual suspend fun initialize(iceServers: List<IceServer>) {
        logger.w { "iOS WebRTC not yet implemented" }
        throw NotImplementedError("iOS WebRTC support not yet implemented")
    }

    actual suspend fun createOffer(): SessionDescription {
        throw NotImplementedError("iOS WebRTC support not yet implemented")
    }

    actual suspend fun setRemoteAnswer(answer: SessionDescription) {
        throw NotImplementedError("iOS WebRTC support not yet implemented")
    }

    actual suspend fun addIceCandidate(candidate: IceCandidateData) {
        throw NotImplementedError("iOS WebRTC support not yet implemented")
    }

    actual fun createDataChannel(
        label: String,
        ordered: Boolean,
        maxRetransmits: Int
    ): DataChannelWrapper {
        throw NotImplementedError("iOS WebRTC support not yet implemented")
    }

    actual suspend fun close() {
        // No-op for now
    }
}
