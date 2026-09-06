package io.music_assistant.sendspin.connection

import io.music_assistant.sendspin.api.FailureCause
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ReconnectPolicyTest {
    private val lost = DropReason.Lost(null)

    @Test
    fun backoffDoublesWithJitterAndCaps() {
        val random = Random(7)
        repeat(4) { attempt ->
            val base = 1_000L shl attempt
            val delay = ReconnectPolicy.delayMillis(attempt, random)
            assertTrue(delay in (base * 0.8).toLong()..(base * 1.2).toLong(), "attempt $attempt: $delay")
        }
        repeat(20) {
            val delay = ReconnectPolicy.delayMillis(attempt = 10 + it, random = random)
            assertTrue(delay in 24_000L..36_000L, "capped: $delay")
        }
    }

    @Test
    fun offlineWaitsForNetworkForEveryRetriableReason() {
        for (reason in listOf(lost, DropReason.ServerClosed, DropReason.Silent, DropReason.ConnectFailed(Exception()))) {
            assertEquals(ReconnectPolicy.Decision.WaitForNetwork, ReconnectPolicy.next(3, reason, online = false, consecutiveRejections = 0))
        }
    }

    @Test
    fun rejectionsRetryUntilTheCapThenFailWithTheMappedCause() {
        val rejected = DropReason.Rejected("pairing_required")
        assertIs<ReconnectPolicy.Decision.Retry>(ReconnectPolicy.next(0, rejected, online = true, consecutiveRejections = 4))
        assertEquals(
            ReconnectPolicy.Decision.Fail(FailureCause.Unauthorized),
            ReconnectPolicy.next(0, rejected, online = true, consecutiveRejections = 5),
        )
        assertEquals(
            ReconnectPolicy.Decision.Fail(FailureCause.Unpaired),
            ReconnectPolicy.next(0, DropReason.Rejected("unpaired"), online = true, consecutiveRejections = 5),
        )
        assertEquals(
            ReconnectPolicy.Decision.Fail(FailureCause.ServerRejected),
            ReconnectPolicy.next(0, DropReason.Rejected("weird"), online = true, consecutiveRejections = 5),
        )
    }

    @Test
    fun failureBeatsOffline() {
        assertIs<ReconnectPolicy.Decision.Fail>(
            ReconnectPolicy.next(0, DropReason.Rejected("unauthorized"), online = false, consecutiveRejections = 5),
        )
    }
}
