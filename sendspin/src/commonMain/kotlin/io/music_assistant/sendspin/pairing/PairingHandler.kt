package io.music_assistant.sendspin.pairing

import co.touchlab.kermit.Logger
import io.music_assistant.sendspin.identity.SendspinTrustStore
import io.music_assistant.sendspin.wire.ClientPairFinalizeMessage
import io.music_assistant.sendspin.wire.ClientPairFinalizePayload
import io.music_assistant.sendspin.wire.PairAbortMessage
import io.music_assistant.sendspin.wire.PairAbortPayload
import io.music_assistant.sendspin.noise.SendspinBase64
import io.music_assistant.sendspin.noise.crypto.NoiseCrypto
import io.music_assistant.sendspin.wire.SendspinJson
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

/**
 * Client half of the Pairing PSK flow: mints a fresh long-term PSK, sends it in
 * `client/pair-finalize`, and persists the record only on `server/pair-finalize`.
 * Cancellation, abort, drop, or timeout discard the attempt with nothing persisted.
 */
class PairingHandler(
    private val crypto: NoiseCrypto,
    private val trustStore: SendspinTrustStore,
) {
    private val logger = Logger.withTag("PairingHandler")

    class PendingAttempt internal constructor(
        internal val longTermPsk: ByteArray,
        internal val serverId: String,
    )

    // Transitions are locked: the attempt timeout fires on a different coroutine
    // than the session driver, and completion must never race an abort.
    private val lock = SynchronizedObject()

    var pending: PendingAttempt? = null
        private set

    /** Starts an attempt; the attempt timeout is the caller's responsibility. */
    suspend fun startAttempt(serverId: String, sendJson: suspend (String) -> Unit): PendingAttempt {
        val psk = crypto.randomBytes(PSK_SIZE)
        val attempt = PendingAttempt(psk, serverId)
        synchronized(lock) { pending = attempt }
        logger.i { "Starting pairing-PSK attempt" }
        sendJson(
            SendspinJson.encodeToString(
                ClientPairFinalizeMessage(
                    payload = ClientPairFinalizePayload(longTermPsk = SendspinBase64.encode(psk)),
                ),
            ),
        )
        return attempt
    }

    /** Persists the pending record; false (ignored) when nothing was pending. */
    fun completeAttempt(): Boolean {
        val attempt = synchronized(lock) { pending.also { pending = null } } ?: return false
        trustStore.recordLongTermPsk(attempt.longTermPsk, attempt.serverId)
        logger.i { "Pairing record persisted" }
        return true
    }

    /** Silently abandons any pending attempt; persists nothing. */
    fun discardAttempt(): Boolean {
        val had = synchronized(lock) { (pending != null).also { pending = null } }
        if (had) logger.i { "Pairing attempt discarded" }
        return had
    }

    /** Aborts [attempt] with [reason]; a superseded or resolved attempt is ignored. */
    suspend fun abortAttempt(
        attempt: PendingAttempt,
        reason: String,
        sendJson: suspend (String) -> Unit,
    ) {
        val won = synchronized(lock) { (pending === attempt).also { if (it) pending = null } }
        if (!won) return
        logger.w { "Aborting pairing attempt: $reason" }
        sendJson(
            SendspinJson.encodeToString(PairAbortMessage(payload = PairAbortPayload(reason = reason))),
        )
    }

    private companion object {
        const val PSK_SIZE = 32
    }
}
