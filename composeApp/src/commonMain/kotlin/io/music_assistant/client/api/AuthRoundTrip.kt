package io.music_assistant.client.api

import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Outcome of an auth round-trip (`client/auth` or `auth/login`).
 *
 * The server authenticates per WebSocket connection, so every reconnect re-runs
 * `client/auth`. A round-trip that never completes (`Result.failure`) is a
 * connectivity failure, not a rejection: the token is still valid, so treating it
 * as one would bounce the user to login for no reason.
 */
/**
 * Why an auth round-trip produced no server response.
 *
 * The distinction is not cosmetic: [NOT_SENT] means the server never saw the request,
 * so a report of "the server did not answer" sends the user (and the maintainer)
 * hunting the wrong side of the wire.
 */
internal enum class AuthFailureCause {
    /** The send itself failed — no transport, or the socket died before the write. */
    NOT_SENT,

    /** The request went out, but no reply arrived within the timeout. */
    NO_REPLY,
}

internal sealed interface AuthRoundTrip {
    /**
     * No server response — connectivity, not rejection.
     * [surfaceAsFailure] is true when the caller should drop to the login screen.
     * [cause] separates "the request never left the device" from "the server stayed
     * silent", which are different faults with different user-facing advice.
     */
    data class NoResponse(
        val surfaceAsFailure: Boolean,
        val cause: AuthFailureCause,
    ) : AuthRoundTrip

    /** Server returned an `error_code` — the token/credentials were rejected. */
    data class Rejected(val message: String) : AuthRoundTrip

    /** Server returned a payload for the caller to parse. */
    data class Responded(val answer: Answer) : AuthRoundTrip
}

/** The auth round-trip was sent but no response arrived within the timeout. */
internal class AuthRoundTripTimeout(timeoutMs: Long) :
    Exception("Auth round-trip timed out after ${timeoutMs}ms")

/**
 * Run an auth round-trip under a timeout. The reply travels the same WebSocket as
 * the request, so a socket that dies after the send (but before the reply) would
 * otherwise leave [send] suspended forever — wedging the auth flow. The timeout
 * converts that into a completed [Result.failure], which the budget then counts.
 */
internal suspend fun authRoundTrip(
    timeoutMs: Long,
    send: suspend () -> Result<Answer>,
): Result<Answer> =
    withTimeoutOrNull(timeoutMs) { send() } ?: Result.failure(AuthRoundTripTimeout(timeoutMs))

/**
 * Classify an auth round-trip. A non-response surfaces to the login screen only for
 * a user-initiated login ([isAutoLogin] false) or once the silent-failure budget is
 * reached — a silent reconnect re-auth below budget is tolerated so the reconnect
 * cycle can retry.
 *
 * @param priorSilentFailures consecutive silent re-auth failures before this one;
 *   resets to 0 on any successful auth.
 */
internal fun classifyAuthRoundTrip(
    response: Result<Answer>,
    isAutoLogin: Boolean,
    priorSilentFailures: Int,
    maxSilentFailures: Int,
): AuthRoundTrip = response.fold(
    onSuccess = { answer ->
        if (answer.json.containsKey("error_code")) {
            AuthRoundTrip.Rejected(
                answer.json["error"]?.jsonPrimitive?.contentOrNull ?: "Authentication failed",
            )
        } else {
            AuthRoundTrip.Responded(answer)
        }
    },
    onFailure = { error ->
        val budgetReached = priorSilentFailures + 1 >= maxSilentFailures
        AuthRoundTrip.NoResponse(
            surfaceAsFailure = !isAutoLogin || budgetReached,
            cause = when (error) {
                is AuthRoundTripTimeout -> AuthFailureCause.NO_REPLY
                else -> AuthFailureCause.NOT_SENT
            },
        )
    },
)
