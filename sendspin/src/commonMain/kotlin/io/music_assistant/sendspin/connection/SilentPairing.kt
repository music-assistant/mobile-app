package io.music_assistant.sendspin.connection

import co.touchlab.kermit.Logger
import io.music_assistant.sendspin.noise.PskCategory
import io.music_assistant.sendspin.session.SessionInfo
import io.music_assistant.sendspin.session.TrustLevel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * Requests silent pairing through the app (`sendspin/pair_web_player` on the
 * MA API) when a session comes up unpaired on the sentinel PSK. Triggered on
 * ready rather than activation: a sentinel session's first activate only
 * arrives after this RPC, so waiting on it would deadlock.
 *
 * The RPC runs on [scope], which outlives connection attempts: a sentinel
 * session is often rejected (`pairing_required`) moments after the request
 * starts, and the request is what resolves the rejection. A server-side
 * unpair is therefore undone on reconnect, matching MA's built-in web players.
 */
internal class SilentPairing(
    private val pairWebPlayer: suspend (pairingToken: String) -> Unit,
    private val pairingToken: () -> String,
    private val scope: CoroutineScope,
) {
    private val logger = Logger.withTag("SilentPairing")
    private var rpc: Job? = null

    fun onReady(info: SessionInfo) {
        val unpaired = info.matchedPskCategory == PskCategory.SENTINEL && info.trustLevel == TrustLevel.NONE
        if (unpaired && rpc?.isActive != true) trigger()
    }

    private fun trigger() {
        logger.i { "Requesting silent web-player pairing" }
        rpc = scope.launch {
            try {
                withTimeout(RPC_TIMEOUT_MILLIS) { pairWebPlayer(pairingToken()) }
                logger.i { "Silent pairing request accepted" }
            } catch (e: TimeoutCancellationException) {
                logger.w(e) { "Silent pairing request timed out" }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.w { "Silent pairing request failed: ${e.message}" }
            }
        }
    }

    private companion object {
        /** Matches the pairing attempt window, so an unanswered RPC cannot leak. */
        const val RPC_TIMEOUT_MILLIS = 120_000L
    }
}
