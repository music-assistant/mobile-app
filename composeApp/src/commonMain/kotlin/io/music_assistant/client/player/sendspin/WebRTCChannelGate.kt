package io.music_assistant.client.player.sendspin

/**
 * Freshness tracking for the single-use WebRTC sendspin channel. The wrapper
 * instance is the channel identity (a new one exists only after a new peer
 * negotiation); once a session has run on it — or died trying — every further
 * attempt is refused. Nothing but a new instance resets the flag.
 */
internal class WebRTCChannelGate {
    private var lastObserved: Any? = null
    private var used = false

    /** Returns true when [channel] may host a fresh session. */
    fun isFresh(channel: Any): Boolean {
        if (lastObserved !== channel) {
            lastObserved = channel
            used = false
        }
        return !used
    }

    /** Marks [channel] as consumed (successfully attached or dead). */
    fun markUsed(channel: Any) {
        if (lastObserved !== channel) {
            lastObserved = channel
        }
        used = true
    }
}
