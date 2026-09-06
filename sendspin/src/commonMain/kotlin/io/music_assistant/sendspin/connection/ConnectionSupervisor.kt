package io.music_assistant.sendspin.connection

import co.touchlab.kermit.Logger
import io.music_assistant.sendspin.api.Endpoint
import io.music_assistant.sendspin.api.MonotonicClock
import io.music_assistant.sendspin.identity.SendspinTrustStore
import io.music_assistant.sendspin.noise.crypto.NoiseCrypto
import io.music_assistant.sendspin.session.Activation
import io.music_assistant.sendspin.session.NoiseSession
import io.music_assistant.sendspin.session.SessionConfig
import io.music_assistant.sendspin.session.SessionHandler
import io.music_assistant.sendspin.session.SessionInfo
import io.music_assistant.sendspin.session.SessionRejected
import io.music_assistant.sendspin.session.TransportLost
import io.music_assistant.sendspin.transport.TransportConnector
import io.music_assistant.sendspin.wire.AudioChunk
import io.music_assistant.sendspin.wire.GoodbyeReason
import io.music_assistant.sendspin.wire.ServerMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.random.Random

/**
 * Owns the connection state machine: one attempt at a time, one reconnect
 * policy, structured so that cancelling [run] tears everything down.
 *
 * Per attempt: connect a transport, run a [NoiseSession] on this coroutine,
 * and run [companion] (clock probes, state reports, liveness) as a sibling
 * for the attempt's lifetime. Any failure in either ends the attempt.
 */
internal class ConnectionSupervisor(
    private val connector: TransportConnector,
    private val trustStore: SendspinTrustStore,
    private val crypto: NoiseCrypto,
    private val online: StateFlow<Boolean>,
    private val clock: MonotonicClock,
    private val pairWebPlayer: suspend (pairingToken: String) -> Unit,
    private val random: Random = Random.Default,
) {
    private val logger = Logger.withTag("ConnectionSupervisor")

    private companion object {
        const val GOODBYE_TIMEOUT_MILLIS = 1_000L
    }
    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val state: StateFlow<ConnectionState> = _state

    /**
     * Runs until cancelled. Restart it for a new [endpoint] or [sessionConfig].
     * On cancellation an active session says goodbye with [goodbyeOnCancel] first.
     */
    suspend fun run(
        endpoint: Endpoint,
        sessionConfig: SessionConfig,
        handler: SessionHandler,
        goodbyeOnCancel: () -> GoodbyeReason = { GoodbyeReason.Restart },
        companion: suspend CoroutineScope.(NoiseSession) -> Unit,
    ): Unit = coroutineScope {
        val silentPairing = SilentPairing(pairWebPlayer, trustStore::pairingToken, this)
        var attempt = 0
        var consecutiveRejections = 0
        var lastReason: DropReason = DropReason.ServerClosed
        try {
            while (true) {
                if (!online.value) {
                    _state.value = ConnectionState.WaitingForNetwork(attempt, lastReason)
                    online.first { it }
                }
                _state.value = ConnectionState.Connecting(attempt)
                var activeSinceMicros: Long? = null
                val reason = runAttempt(endpoint, sessionConfig, companion, handler, silentPairing, goodbyeOnCancel) {
                    activeSinceMicros = activeSinceMicros ?: clock.nowMicros()
                }
                logger.i { "Attempt $attempt ended: $reason" }
                lastReason = reason
                val stableMicros = activeSinceMicros?.let { clock.nowMicros() - it } ?: 0L
                if (stableMicros >= ReconnectPolicy.STABLE_ACTIVE_MILLIS * 1_000) attempt = 0
                consecutiveRejections = if (reason is DropReason.Rejected) consecutiveRejections + 1 else 0

                when (
                    val decision = ReconnectPolicy.next(
                    attempt,
                    reason,
                    online.value,
                    consecutiveRejections,
                    random,
                )
                ) {
                    is ReconnectPolicy.Decision.Retry -> {
                        _state.value = ConnectionState.Backoff(
                            attempt,
                            reason,
                            retryAtMicros = clock.nowMicros() + decision.delayMillis * 1_000,
                        )
                        delay(decision.delayMillis)
                        attempt++
                    }

                    ReconnectPolicy.Decision.WaitForNetwork -> attempt++

                    is ReconnectPolicy.Decision.Fail -> {
                        _state.value = ConnectionState.Failed(decision.cause)
                        awaitCancellation()
                    }
                }
            }
        } finally {
            _state.value = ConnectionState.Idle
        }
    }

    private suspend fun runAttempt(
        endpoint: Endpoint,
        sessionConfig: SessionConfig,
        companion: suspend CoroutineScope.(NoiseSession) -> Unit,
        handler: SessionHandler,
        silentPairing: SilentPairing,
        goodbyeOnCancel: () -> GoodbyeReason,
        onActive: () -> Unit,
    ): DropReason {
        val transport = try {
            connector.connect(endpoint, trustStore.clientId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            return DropReason.ConnectFailed(e)
        }
        val session = NoiseSession(transport, sessionConfig, crypto, trustStore)
        val observing = object : SessionHandler {
            override fun onReady(info: SessionInfo) {
                _state.value = ConnectionState.Active(info, activated = false)
                onActive()
                silentPairing.onReady(info)
                handler.onReady(info)
            }

            override fun onActivated(activation: Activation) {
                (_state.value as? ConnectionState.Active)?.let { _state.value = it.copy(activated = true) }
                handler.onActivated(activation)
            }

            override suspend fun onMessage(message: ServerMessage) = handler.onMessage(message)

            override fun onAudio(chunk: AudioChunk) = handler.onAudio(chunk)
        }
        val reason = try {
            coroutineScope {
                val sibling = launch { companion(session) }
                session.run(observing)
                sibling.cancel()
            }
            DropReason.ServerClosed
        } catch (e: CancellationException) {
            withContext(NonCancellable) {
                withTimeoutOrNull(GOODBYE_TIMEOUT_MILLIS) { session.goodbye(goodbyeOnCancel()) }
            }
            throw e
        } catch (e: SessionRejected) {
            DropReason.Rejected(e.reason)
        } catch (e: TransportLost) {
            DropReason.Lost(e.cause)
        } catch (e: ServerSilentException) {
            logger.i { e.message.orEmpty() }
            DropReason.Silent
        } catch (e: Throwable) {
            DropReason.Protocol(e)
        }
        // The session closes the transport on its own failures; a companion failure
        // reaches it as cancellation, so close here too (idempotent).
        runCatching { transport.close() }
        return reason
    }
}
