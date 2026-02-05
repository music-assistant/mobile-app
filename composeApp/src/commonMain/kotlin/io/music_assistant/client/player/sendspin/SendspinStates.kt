package io.music_assistant.client.player.sendspin

import io.music_assistant.client.player.sendspin.model.ConnectionReason
import io.music_assistant.client.player.sendspin.model.VersionedRole

sealed class SendspinConnectionState {
    object Idle : SendspinConnectionState()
    object Advertising : SendspinConnectionState()
    data class Connected(
        val serverId: String,
        val serverName: String,
        val connectionReason: ConnectionReason
    ) : SendspinConnectionState()

    /**
     * Error state with categorized error information.
     * Use SendspinError to distinguish transient, permanent, and degraded states.
     */
    data class Error(val error: SendspinError) : SendspinConnectionState()
}

sealed class SendspinPlaybackState {
    object Idle : SendspinPlaybackState()
    object Buffering : SendspinPlaybackState()
    data class Playing(val timestamp: Long) : SendspinPlaybackState()
    object Synchronized : SendspinPlaybackState()
    data class Error(val reason: String) : SendspinPlaybackState()
}

sealed class ProtocolState {
    object Disconnected : ProtocolState()
    object AwaitingAuth : ProtocolState()
    object AwaitingServerHello : ProtocolState()
    data class Ready(val activeRoles: List<VersionedRole>) : ProtocolState()
    object Streaming : ProtocolState()
}

sealed class WebSocketState {
    object Disconnected : WebSocketState()
    object Connecting : WebSocketState()
    data class Reconnecting(val attempt: Int) : WebSocketState()
    object Connected : WebSocketState()
    data class Error(val error: Throwable) : WebSocketState()
}

data class BufferState(
    // Existing metrics
    val bufferedDuration: Long, // microseconds
    val isUnderrun: Boolean,
    val droppedChunks: Int,

    // Adaptive buffering metrics
    val targetBufferDuration: Long = 0L, // Target buffer size in microseconds
    val currentPrebufferThreshold: Long = 0L, // Current prebuffer threshold
    val smoothedRTT: Double = 0.0, // Smoothed RTT in microseconds
    val jitter: Double = 0.0, // Jitter (RTT std dev) in microseconds
    val dropRate: Double = 0.0 // Recent chunk drop rate [0.0, 1.0]
)
