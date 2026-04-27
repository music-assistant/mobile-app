package io.music_assistant.client.webrtc.model

/**
 * WebRTC peer connection state values.
 * Maps to platform-specific PeerConnectionState enums from webrtc-kmp library.
 *
 * These states track the ICE (Interactive Connectivity Establishment) connection
 * progress between the local peer and remote peer.
 */
enum class PeerConnectionStateValue {
    /** Peer connection is new and not yet started */
    NEW,

    /** ICE connection is in progress */
    CONNECTING,

    /** ICE connection established successfully */
    CONNECTED,

    /** ICE connection was disconnected (may be temporary during network switches) */
    DISCONNECTED,

    /** ICE connection failed permanently */
    FAILED,

    /** Peer connection was closed */
    CLOSED,

    ;

    override fun toString(): String = name.lowercase()
}
