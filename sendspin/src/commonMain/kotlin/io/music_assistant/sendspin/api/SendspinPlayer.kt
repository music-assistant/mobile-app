package io.music_assistant.sendspin.api

import io.ktor.client.HttpClient
import io.music_assistant.sendspin.identity.SendspinKeyStore
import io.music_assistant.sendspin.noise.crypto.NoiseCrypto
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

/** Ports the app provides. */
class SendspinDeps(
    val sink: AudioSink,
    val decoders: DecoderFactory,
    val keyStore: SendspinKeyStore,
    val crypto: NoiseCrypto,
    val httpClient: HttpClient,
    /** Network reachability; `false` pauses reconnect attempts until `true`. */
    val online: StateFlow<Boolean>,
    /**
     * Silent pairing: call MA's `sendspin/pair_web_player` with the pairing
     * token on the app's API connection. Throw on failure; it is non-fatal.
     */
    val pairWebPlayer: suspend (pairingToken: String) -> Unit,
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
