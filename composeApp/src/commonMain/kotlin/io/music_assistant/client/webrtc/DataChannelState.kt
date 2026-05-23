package io.music_assistant.client.webrtc

/**
 * Lifecycle state of a WebRTC data channel.
 *
 * Mirrors libwebrtc's RTCDataChannelState. Defined locally so the wrapper API isn't
 * coupled to any specific WebRTC binding.
 */
enum class DataChannelState {
    Connecting,
    Open,
    Closing,
    Closed,
}
