package io.music_assistant.sendspin.api

import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.TimeSource

/** The local Sendspin player. Configured by a config flow; observed through [state] and [events]. */
interface SendspinPlayer {
    val state: StateFlow<PlayerState>

    /** UI-meaningful occurrences only. Buffered; a slow collector loses the oldest. */
    val events: Flow<PlayerEvent>
}

/**
 * Ports the app provides. This and the rest of the `api` package are the whole
 * public surface of the module; the Noise protocol, wire format and identity
 * handling stay internal.
 */
class SendspinDeps(
    val sink: AudioSink,
    val decoders: DecoderFactory,
    val keyStore: SendspinKeyStore,
    val httpClient: HttpClient,
    /** Network reachability; `false` pauses reconnect attempts until `true`. */
    val online: StateFlow<Boolean>,
    /**
     * Silent pairing: get this token approved out of band, over whatever
     * trusted channel the app already holds, so no person approves a pairing
     * code. Throw on failure; it is non-fatal. An app that pairs by the code
     * instead supplies a no-op.
     */
    val approvePairing: suspend (pairingToken: String) -> Unit,
    /** Where the audio loop runs: one thread, high priority where the platform allows. */
    val audioDispatcher: CoroutineDispatcher,
    val clock: MonotonicClock = SystemMonotonicClock,
)

/** Local monotonic time in microseconds. Injected so tests can drive it. */
fun interface MonotonicClock {
    fun nowMicros(): Long
}

object SystemMonotonicClock : MonotonicClock {
    private val origin = TimeSource.Monotonic.markNow()
    override fun nowMicros(): Long = origin.elapsedNow().inWholeMicroseconds
}
