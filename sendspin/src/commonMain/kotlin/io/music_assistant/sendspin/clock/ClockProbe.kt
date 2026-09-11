package io.music_assistant.sendspin.clock

import io.music_assistant.sendspin.api.MonotonicClock
import io.music_assistant.sendspin.connection.ServerSilentException
import io.music_assistant.sendspin.wire.ClientTimeMessage
import io.music_assistant.sendspin.wire.ClientTimePayload
import io.music_assistant.sendspin.wire.WireCodec
import kotlinx.coroutines.delay

/**
 * Sends `client/time` probe bursts for the life of a connection attempt and
 * doubles as the liveness watchdog: a server that answers no probe for
 * [SILENT_BURSTS_LIMIT] consecutive bursts is declared silent, which ends the
 * attempt. Works over any transport, since it needs only the protocol.
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
            repeat(PROBES_PER_BURST) {
                send(WireCodec.encode(ClientTimeMessage(payload = ClientTimePayload(clock.nowMicros()))))
                delay(PROBE_INTERVAL_MILLIS)
            }
            delay(BURST_SETTLE_MILLIS)
            silentBursts = if (sync.endBurst() == 0) silentBursts + 1 else 0
            if (silentBursts >= SILENT_BURSTS_LIMIT) throw ServerSilentException()
            val warmingUp = clock.nowMicros() - startMicros < WARMUP_MICROS
            delay(if (warmingUp) WARMUP_BURST_INTERVAL_MILLIS else BURST_INTERVAL_MILLIS)
        }
    }

    companion object {
        const val PROBES_PER_BURST = 8
        const val PROBE_INTERVAL_MILLIS = 100L
        const val BURST_SETTLE_MILLIS = 500L
        const val BURST_INTERVAL_MILLIS = 10_000L
        const val WARMUP_BURST_INTERVAL_MILLIS = 2_000L
        const val WARMUP_MICROS = 30_000_000L
        const val SILENT_BURSTS_LIMIT = 3
    }
}
