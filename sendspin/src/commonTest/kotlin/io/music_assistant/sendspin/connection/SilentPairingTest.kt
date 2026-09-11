package io.music_assistant.sendspin.connection

import io.music_assistant.sendspin.noise.PskCategory
import io.music_assistant.sendspin.session.SessionInfo
import io.music_assistant.sendspin.session.TrustLevel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SilentPairingTest {
    private class Rpc {
        val tokens = mutableListOf<String>()
        var gate: CompletableDeferred<Unit>? = null
        var failure: Throwable? = null
        var cancelled = false

        suspend fun pair(token: String) {
            tokens += token
            try {
                gate?.await()
            } catch (e: Exception) {
                cancelled = true
                throw e
            }
            failure?.let { throw it }
        }
    }

    private fun ready(category: PskCategory = PskCategory.SENTINEL, trust: TrustLevel = TrustLevel.NONE) =
        SessionInfo("srv", "Server", category, trust)

    @Test
    fun sentinelReadyTriggersExactlyOneCallPerResolvedRpc() = runTest {
        val rpc = Rpc()
        val pairing = SilentPairing(rpc::pair, { "SP:0TOKEN" }, this)
        rpc.gate = CompletableDeferred()

        pairing.onReady(ready())
        // A re-handshake re-emits ready; the in-flight RPC is not duplicated.
        pairing.onReady(ready(category = PskCategory.PAIRING))
        pairing.onReady(ready())
        runCurrent()
        assertEquals(listOf("SP:0TOKEN"), rpc.tokens)

        rpc.gate!!.complete(Unit)
        advanceUntilIdle()
        pairing.onReady(ready())
        advanceUntilIdle()
        assertEquals(2, rpc.tokens.size, "a resolved RPC allows the next unpaired session to retry")
    }

    @Test
    fun unansweredCallTimesOutAndAllowsRetry() = runTest {
        val rpc = Rpc()
        rpc.gate = CompletableDeferred()
        val pairing = SilentPairing(rpc::pair, { "SP:0TOKEN" }, this)

        pairing.onReady(ready())
        runCurrent()
        assertEquals(1, rpc.tokens.size)

        advanceTimeBy(121_000)
        runCurrent()
        assertTrue(rpc.cancelled, "the RPC is bounded by the pairing window")
        pairing.onReady(ready())
        advanceUntilIdle()
        assertEquals(2, rpc.tokens.size)
    }

    @Test
    fun noCallForPairedOrMidPairingSessions() = runTest {
        val rpc = Rpc()
        val pairing = SilentPairing(rpc::pair, { "SP:0TOKEN" }, this)
        pairing.onReady(ready(category = PskCategory.LONG_TERM_STORED, trust = TrustLevel.USER))
        pairing.onReady(ready(category = PskCategory.LONG_TERM_SHARED, trust = TrustLevel.USER))
        pairing.onReady(ready(category = PskCategory.PAIRING))
        advanceUntilIdle()
        assertTrue(rpc.tokens.isEmpty())
    }

    @Test
    fun rpcFailureIsNonFatal() = runTest {
        val rpc = Rpc()
        rpc.failure = IllegalStateException("server rejected")
        val pairing = SilentPairing(rpc::pair, { "SP:0TOKEN" }, this)
        pairing.onReady(ready())
        advanceUntilIdle()
        assertEquals(1, rpc.tokens.size)
        assertFalse(rpc.cancelled)
    }
}
