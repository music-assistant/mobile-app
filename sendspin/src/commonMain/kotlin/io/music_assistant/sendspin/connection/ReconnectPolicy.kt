package io.music_assistant.sendspin.connection

import io.music_assistant.sendspin.api.FailureCause
import kotlin.math.min
import kotlin.random.Random

/**
 * The single reconnect policy. Exponential backoff with jitter, unlimited while
 * online. Only repeated goodbye rejections give up: the first rejections are
 * expected (a sentinel session is rejected with `pairing_required` while the
 * silent pairing RPC is still in flight, then admitted on a later attempt).
 */
internal object ReconnectPolicy {
    sealed interface Decision {
        data class Retry(val delayMillis: Long) : Decision
        data object WaitForNetwork : Decision
        data class Fail(val cause: FailureCause) : Decision
    }

    const val BASE_DELAY_MILLIS = 1_000L
    const val MAX_DELAY_MILLIS = 30_000L
    const val JITTER = 0.2
    const val MAX_CONSECUTIVE_REJECTIONS = 5

    /** An attempt that stayed active this long resets the attempt counter. */
    const val STABLE_ACTIVE_MILLIS = 10_000L

    fun next(
        attempt: Int,
        reason: DropReason,
        online: Boolean,
        consecutiveRejections: Int,
        random: Random = Random.Default,
    ): Decision {
        if (reason is DropReason.Rejected && consecutiveRejections >= MAX_CONSECUTIVE_REJECTIONS) {
            return Decision.Fail(failureCause(reason.goodbye))
        }
        if (!online) return Decision.WaitForNetwork
        return Decision.Retry(delayMillis(attempt, random))
    }

    fun delayMillis(attempt: Int, random: Random): Long {
        val exponent = min(attempt, 30)
        val base = min(BASE_DELAY_MILLIS shl exponent, MAX_DELAY_MILLIS)
        val jitter = 1.0 + (random.nextDouble() * 2 - 1) * JITTER
        return (base * jitter).toLong()
    }

    private fun failureCause(goodbye: String): FailureCause = when (goodbye) {
        "unpaired" -> FailureCause.Unpaired
        "unauthorized", "pairing_required" -> FailureCause.Unauthorized
        else -> FailureCause.ServerRejected
    }
}
