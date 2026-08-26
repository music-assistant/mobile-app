package io.music_assistant.client.player.sendspin.pairing

import co.touchlab.kermit.Logger
import io.music_assistant.client.api.Answer
import io.music_assistant.client.api.Request
import io.music_assistant.client.player.sendspin.noise.PskCategory
import io.music_assistant.client.player.sendspin.session.SessionEvent
import io.music_assistant.client.player.sendspin.session.TrustLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * Requests silent pairing (`sendspin/pair_web_player`) when an encrypted session
 * comes up unpaired on the sentinel PSK. Triggered on ProtocolReady rather than
 * activation — a sentinel session's first activate only arrives after this RPC,
 * so waiting on it would deadlock. Failures are non-fatal.
 *
 * The RPC is never cancelled on session failure: a sentinel session is often
 * rejected (`pairing_required`) moments after this request starts, and the
 * request is what resolves the rejection — the server pairs the client's next
 * connection. Consequently a server-side unpair is undone on reconnect; that
 * is intended, matching MA's built-in web players.
 */
class SilentPairingCoordinator(
    private val sendRequest: suspend (Request) -> Result<Answer>,
    private val pairingToken: () -> String,
    private val scope: CoroutineScope,
) {
    private val logger = Logger.withTag("SilentPairingCoordinator")

    private var rpcJob: Job? = null

    fun onSessionEvent(event: SessionEvent) {
        if (event !is SessionEvent.ProtocolReady) return
        val shouldPair = event.matchedPskCategory == PskCategory.SENTINEL &&
            event.trustLevel == TrustLevel.NONE
        if (shouldPair && rpcJob?.isActive != true) {
            trigger()
        }
    }

    private fun trigger() {
        logger.i { "Requesting silent web-player pairing" }
        rpcJob = scope.launch {
            val result = try {
                withTimeout(RPC_TIMEOUT_MILLIS) {
                    sendRequest(
                        Request(
                            command = PAIR_WEB_PLAYER_COMMAND,
                            args = buildJsonObject {
                                put("pairing_token", JsonPrimitive(pairingToken()))
                            },
                        ),
                    )
                }
            } catch (_: TimeoutCancellationException) {
                logger.w { "Silent pairing request timed out" }
                return@launch
            }
            result.fold(
                onSuccess = { answer ->
                    // Server-reported errors arrive as a successful Answer with error_code.
                    if (answer.json.containsKey("error_code")) {
                        logger.w {
                            "Silent pairing request failed " +
                                "(error_code=${answer.json["error_code"]})"
                        }
                    } else {
                        logger.i { "Silent pairing request accepted" }
                    }
                },
                onFailure = { logger.w { "Silent pairing request failed: ${it.message}" } },
            )
        }
    }

    private companion object {
        const val PAIR_WEB_PLAYER_COMMAND = "sendspin/pair_web_player"

        /** Matches the pairing attempt window, so an unanswered RPC cannot leak. */
        const val RPC_TIMEOUT_MILLIS = 120_000L
    }
}
