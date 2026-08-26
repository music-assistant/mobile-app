package io.music_assistant.client.player.sendspin

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the single-use semantics of the WebRTC sendspin channel gate the
 * factory relies on: a wrapper instance may host exactly one session attempt
 * (successful or failed), and only a brand-new instance — i.e. a fresh peer
 * negotiation — resets freshness. Retrying an exhausted wrapper must keep
 * reporting exhaustion so the controller replaces it exactly once per
 * failure via a forced WebRTC reconnect.
 */
class SendspinClientFactoryWebRTCTest {
    @Test
    fun freshWrapperIsUsableExactlyOnce() {
        val gate = WebRTCChannelGate()
        val wrapper = Any()
        assertTrue(gate.isFresh(wrapper))
        gate.markUsed(wrapper)
        assertFalse(gate.isFresh(wrapper), "used wrapper reports exhausted")
        assertFalse(gate.isFresh(wrapper), "every retry on the same wrapper stays exhausted")
    }

    @Test
    fun newWrapperInstanceResetsFreshness() {
        val gate = WebRTCChannelGate()
        val first = Any()
        gate.markUsed(first)
        assertFalse(gate.isFresh(first))

        val second = Any()
        assertTrue(gate.isFresh(second), "a fresh peer negotiation produces a usable wrapper")
    }

    @Test
    fun failedAttachExhaustsTheWrapperLikeASuccessfulAttach() {
        val gate = WebRTCChannelGate()
        val wrapper = Any()
        assertTrue(gate.isFresh(wrapper))
        // The factory marks the wrapper used on Failed outcomes too: a failed
        // handshake may have consumed protocol frames on the channel.
        gate.markUsed(wrapper)
        assertFalse(gate.isFresh(wrapper))
    }

    @Test
    fun gateTracksOnlyTheLatestWrapperIdentity() {
        val gate = WebRTCChannelGate()
        val old = Any()
        gate.markUsed(old)
        val new = Any()
        assertTrue(gate.isFresh(new))
        gate.markUsed(new)
        assertFalse(gate.isFresh(new))
        // Single-slot by design: re-observing a forgotten instance resets it to
        // fresh. Safe only because the service client exposes exactly one
        // current wrapper and the factory reserves it (markUsed) before
        // attaching — never rely on the gate to remember older instances.
        assertTrue(gate.isFresh(old))
    }
}
