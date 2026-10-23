package io.music_assistant.sendspin.api

import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.TimeSource

/** The local Sendspin player. Configured by a config flow; observed through [state] and [events]. */
public interface SendspinPlayer {
    public val state: StateFlow<PlayerState>

    /** UI-meaningful occurrences only. Buffered; a slow collector loses the oldest. */
    public val events: Flow<PlayerEvent>

    /**
     * Local pause, with no server round trip: while held the player makes no
     * sound and discards the audio it gets. Stream messages still apply, so a
     * release plays the next audio the server sends. The hold outlives
     * reconnects and enable cycles; only the app releases it.
     */
    public fun hold(held: Boolean)
}

/**
 * Ports the app provides. This and the rest of the `api` package are the whole
 * public surface of the module; the Noise protocol, wire format and identity
 * handling stay internal.
 */
public class SendspinDeps(
    public val sink: AudioSink,
    public val decoders: DecoderFactory,
    public val keyStore: SendspinKeyStore,
    public val httpClient: HttpClient,
    /** Network reachability; `false` pauses reconnect attempts until `true`. */
    public val online: StateFlow<Boolean>,
    /**
     * Silent pairing: get this token approved out of band, over whatever
     * trusted channel the app already holds, so no person approves a pairing
     * code. Throw on failure; it is non-fatal. An app that pairs by the code
     * instead supplies a no-op.
     */
    public val approvePairing: suspend (pairingToken: String) -> Unit,
    /** Where the audio loop runs: one thread, high priority where the platform allows. */
    public val audioDispatcher: CoroutineDispatcher,
    /** Reported as `device_info` in `client/hello`. */
    public val deviceInfo: DeviceInfo,
    public val clock: MonotonicClock = SystemMonotonicClock,
)

/**
 * Local monotonic time in microseconds. Injected so tests can drive it. A production clock
 * must tick through device sleep: the server offset is learned against it, and a clock that
 * pauses in a doze is off by the whole sleep when playback resumes.
 */
public fun interface MonotonicClock {
    public fun nowMicros(): Long
}

public object SystemMonotonicClock : MonotonicClock {
    private val origin = TimeSource.Monotonic.markNow()
    override fun nowMicros(): Long = origin.elapsedNow().inWholeMicroseconds
}
