package io.music_assistant.sendspin.clock

import io.music_assistant.sendspin.api.MonotonicClock
import io.music_assistant.sendspin.connection.ServerSilentException
import io.music_assistant.sendspin.wire.ClientTimeMessage
import io.music_assistant.sendspin.wire.ClientTimePayload
import io.music_assistant.sendspin.wire.WireCodec
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Sends `client/time` probe bursts for the life of a connection attempt and
 * doubles as the liveness watchdog: a server that answers no probe for
 * [SILENT_BURSTS_LIMIT] consecutive bursts is declared silent, which ends the
 * attempt. Works over any transport, since it needs only the protocol.
 *
 * Spec burst rules: each probe goes out after the reply to the previous one,
 * a burst has [PROBES_PER_BURST] probes, and bursts start at most 10 s apart.
 * A probe unanswered for [PROBE_TIMEOUT_MILLIS] ends its burst early: the link
 * is stalled, and samples taken through a stall are the worst of the burst.
 *
 * [send] is the session's gated send, so probing starts once activated.
 */
internal class ClockProbe(
    private val sync: ClockSync,
    private val clock: MonotonicClock,
    private val send: suspend (json: String) -> Unit,
) {
    suspend fun run(): Nothing {
        val startMicros = clock.nowMicros()
        var silentBursts = 0
        while (true) {
            val burstStartMicros = clock.nowMicros()
            (1..PROBES_PER_BURST).all { probe() } // stops at the first unanswered probe
            silentBursts = if (sync.endBurst() == 0) silentBursts + 1 else 0
            if (silentBursts >= SILENT_BURSTS_LIMIT) throw ServerSilentException()
            val warmingUp = clock.nowMicros() - startMicros < WARMUP_MICROS
            val intervalMillis = if (warmingUp) WARMUP_BURST_INTERVAL_MILLIS else BURST_INTERVAL_MILLIS
            delay(intervalMillis - (clock.nowMicros() - burstStartMicros) / MICROS_PER_MILLI)
        }
    }

    /** Sends one probe and waits for a reply; false on timeout. */
    private suspend fun probe(): Boolean {
        val before = sync.replies.value
        send(WireCodec.encode(ClientTimeMessage(payload = ClientTimePayload(clock.nowMicros()))))
        return withTimeoutOrNull(PROBE_TIMEOUT_MILLIS) { sync.replies.first { it > before } } != null
    }

    companion object {
        const val PROBES_PER_BURST = 8

        /** Only a stalled link takes longer; a slow remote link (WebRTC over cellular) stays alive. */
        const val PROBE_TIMEOUT_MILLIS = 1_500L

        /** Start to start; a margin under the spec's 10 s bound absorbs timer lateness. */
        const val BURST_INTERVAL_MILLIS = 9_000L
        const val WARMUP_BURST_INTERVAL_MILLIS = 2_000L
        const val WARMUP_MICROS = 30_000_000L
        const val SILENT_BURSTS_LIMIT = 3
        private const val MICROS_PER_MILLI = 1_000L
    }
}
