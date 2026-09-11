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
 * Asks the app to approve the pairing token when a session comes up unpaired
 * on the sentinel PSK. Triggered on ready rather than activation: a sentinel
 * session's first activate only arrives after the approval, so waiting on it
 * would deadlock.
 *
 * The approval runs on [scope], which outlives connection attempts: a sentinel
 * session is often rejected (`pairing_required`) moments after the request
 * starts, and the request is what resolves the rejection. A server-side unpair
 * is therefore undone on reconnect.
 */
internal class SilentPairing(
    private val approvePairing: suspend (pairingToken: String) -> Unit,
    private val pairingToken: () -> String,
    private val scope: CoroutineScope,
) {
    private val logger = Logger.withTag("SilentPairing")
    private var approval: Job? = null

    fun onReady(info: SessionInfo) {
        val unpaired = info.matchedPskCategory == PskCategory.SENTINEL && info.trustLevel == TrustLevel.NONE
        if (unpaired && approval?.isActive != true) trigger()
    }

    private fun trigger() {
        logger.i { "Requesting silent pairing approval" }
        approval = scope.launch {
            try {
                withTimeout(APPROVAL_TIMEOUT_MILLIS) { approvePairing(pairingToken()) }
                logger.i { "Silent pairing approved" }
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
        /** Matches the pairing attempt window, so an unanswered approval cannot leak. */
        const val APPROVAL_TIMEOUT_MILLIS = 120_000L
    }
}
