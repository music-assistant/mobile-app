package io.music_assistant.client.api

import co.touchlab.kermit.Logger
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.delay

private val log = Logger.withTag("SilentReauth")

/** Tuning for [SilentReauth]. */
internal data class ReauthPolicy(
    val maxSilentFailures: Int,
    val roundTripTimeoutMs: Long,
    val retryDelayMs: Long,
)

/** What the caller should do once an auth attempt (or retry run) settles. */
internal sealed interface AuthResolution {
    /** Drop to the login screen with this message. */
    data class Surface(val message: String) : AuthResolution

    /** Server rejected the token/credentials. */
    data class Reject(val message: String) : AuthResolution

    /** Server returned a payload for the caller to parse into a user. */
    data class Authenticated(val answer: Answer) : AuthResolution

    /** The connection went away mid-attempt; nothing to report. */
    data object Aborted : AuthResolution
}

/**
 * Bounded retry engine for per-connection re-auth.
 *
 * A reconnect re-auth that fails to complete (no server response) is retried in
 * place, spaced by [ReauthPolicy.retryDelayMs] and capped by
 * [ReauthPolicy.maxSilentFailures], after which we surface the login screen. The
 * failure count persists across [resolve] calls so a flapping transport (each bounce
 * aborting a run early) still escalates; [reset] clears it at a new connection
 * episode or a successful auth.
 *
 * A user-initiated login ([isAutoLogin] false) never retries — it surfaces on the
 * first failure for immediate feedback.
 */
internal class SilentReauth(private val policy: ReauthPolicy) {
    // Atomic: reset() is called from the connection path (the client's IO context)
    // while resolve() runs on the auth dispatcher.
    private val consecutiveSilentFailures = atomic(0)

    fun reset() {
        consecutiveSilentFailures.value = 0
    }

    suspend fun resolve(
        isAutoLogin: Boolean,
        shouldAttempt: () -> Boolean,
        onAttempt: () -> Unit,
        send: suspend () -> Result<Answer>,
    ): AuthResolution {
        while (shouldAttempt()) {
            onAttempt()
            val response = authRoundTrip(policy.roundTripTimeoutMs, send)
            if (!shouldAttempt()) return AuthResolution.Aborted

            when (
                val outcome = classifyAuthRoundTrip(
                    response,
                    isAutoLogin,
                    priorSilentFailures = consecutiveSilentFailures.value,
                    maxSilentFailures = policy.maxSilentFailures,
                )
            ) {
                is AuthRoundTrip.NoResponse -> {
                    log.e {
                        "re-auth round-trip failed (${outcome.cause}): ${response.exceptionOrNull()}"
                    }
                    if (outcome.surfaceAsFailure) {
                        return AuthResolution.Surface(surfaceMessage(outcome.cause))
                    }
                    consecutiveSilentFailures.incrementAndGet()
                    delay(policy.retryDelayMs)
                }

                is AuthRoundTrip.Rejected -> {
                    consecutiveSilentFailures.value = 0
                    return AuthResolution.Reject(outcome.message)
                }

                is AuthRoundTrip.Responded -> return AuthResolution.Authenticated(outcome.answer)
            }
        }
        return AuthResolution.Aborted
    }

    /**
     * Message for a round-trip that got no response. The two causes must read
     * differently: blaming the server for a request that never left the device sends
     * the user to check the wrong thing.
     */
    private fun surfaceMessage(cause: AuthFailureCause): String = when (cause) {
        AuthFailureCause.NOT_SENT -> "Connection lost before the request was sent"
        AuthFailureCause.NO_REPLY -> "No response from server"
    }
}
