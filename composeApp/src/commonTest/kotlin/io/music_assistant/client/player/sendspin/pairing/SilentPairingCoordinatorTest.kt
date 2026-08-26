package io.music_assistant.client.player.sendspin.pairing

import io.music_assistant.client.api.Answer
import io.music_assistant.client.api.Request
import io.music_assistant.client.player.sendspin.noise.PskCategory
import io.music_assistant.client.player.sendspin.session.SessionEvent
import io.music_assistant.client.player.sendspin.session.TrustLevel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SilentPairingCoordinatorTest {
    private class RpcRecorder {
        val requests = mutableListOf<Request>()
        var result: Result<Answer> = Result.success(Answer(buildJsonObject { }))
        var gate: CompletableDeferred<Unit>? = null
        var cancelled = false

        suspend fun send(request: Request): Result<Answer> {
            requests.add(request)
            try {
                gate?.await()
            } catch (e: Exception) {
                cancelled = true
                throw e
            }
            return result
        }
    }

    private fun ready(
        category: PskCategory? = PskCategory.SENTINEL,
        trust: TrustLevel = TrustLevel.NONE,
        reconnect: Boolean = false,
    ) = SessionEvent.ProtocolReady(
        serverId = "srv",
        serverName = "Server",
        matchedPskCategory = category,
        trustLevel = trust,
        isReconnectEpoch = reconnect,
    )

    @Test
    fun sentinelReadyTriggersExactlyOnePairWebPlayerCall() = runTest {
        val rpc = RpcRecorder()
        val coordinator = SilentPairingCoordinator(rpc::send, { "SP:0TOKEN" }, this)

        coordinator.onSessionEvent(ready())
        // Re-handshake within the same epoch re-emits ProtocolReady; still one call.
        coordinator.onSessionEvent(ready(category = PskCategory.PAIRING))
        coordinator.onSessionEvent(ready())
        advanceUntilIdle()

        assertEquals(1, rpc.requests.size)
        val request = rpc.requests.single()
        assertEquals("sendspin/pair_web_player", request.command)
        assertEquals(
            "SP:0TOKEN",
            request.args?.getValue("pairing_token")?.jsonPrimitive?.content,
        )
    }

    @Test
    fun newEpochAfterDisconnectTriggersAgain() = runTest {
        val rpc = RpcRecorder()
        val coordinator = SilentPairingCoordinator(rpc::send, { "SP:0TOKEN" }, this)

        coordinator.onSessionEvent(ready())
        advanceUntilIdle()
        coordinator.onSessionEvent(SessionEvent.Disconnected)
        coordinator.onSessionEvent(ready(reconnect = true))
        advanceUntilIdle()

        assertEquals(2, rpc.requests.size)
    }

    @Test
    fun inFlightCallSurvivesSessionFailureWithoutDuplication() = runTest {
        val rpc = RpcRecorder()
        rpc.gate = CompletableDeferred()
        val coordinator = SilentPairingCoordinator(rpc::send, { "SP:0TOKEN" }, this)

        coordinator.onSessionEvent(ready())
        runCurrent()
        assertEquals(1, rpc.requests.size)

        // A sentinel session may be rejected (pairing_required) before the RPC
        // lands; the RPC rides the independent MA API connection and must keep
        // going — it is what resolves the rejection.
        coordinator.onSessionEvent(
            SessionEvent.Failed(IllegalStateException("pairing_required"), permanent = false),
        )
        coordinator.onSessionEvent(SessionEvent.Disconnected)
        coordinator.onSessionEvent(ready(reconnect = true))
        runCurrent()
        assertEquals(1, rpc.requests.size, "in-flight RPC is not duplicated on the next epoch")
        assertTrue(!rpc.cancelled, "session failure must not cancel the RPC")

        rpc.gate?.complete(Unit)
        advanceUntilIdle()
        coordinator.onSessionEvent(ready(reconnect = true))
        advanceUntilIdle()
        assertEquals(2, rpc.requests.size, "a resolved RPC allows the next unpaired epoch to retry")
    }

    @Test
    fun unansweredCallTimesOutAndAllowsRetry() = runTest {
        val rpc = RpcRecorder()
        rpc.gate = CompletableDeferred()
        val coordinator = SilentPairingCoordinator(rpc::send, { "SP:0TOKEN" }, this)

        coordinator.onSessionEvent(ready())
        runCurrent()
        assertEquals(1, rpc.requests.size)

        // The RPC is bounded by the pairing window; after it expires a later
        // unpaired epoch may start a fresh one.
        testScheduler.advanceTimeBy(121_000)
        runCurrent()
        coordinator.onSessionEvent(ready(reconnect = true))
        advanceUntilIdle()
        assertEquals(2, rpc.requests.size)
    }

    @Test
    fun noCallForNonSentinelOrUserTrustOrLegacySessions() = runTest {
        val rpc = RpcRecorder()
        val coordinator = SilentPairingCoordinator(rpc::send, { "SP:0TOKEN" }, this)

        // Legacy sessions carry no matched PSK category.
        coordinator.onSessionEvent(ready(category = null))
        // Paired (user-trust) sessions need no pairing.
        coordinator.onSessionEvent(
            ready(category = PskCategory.LONG_TERM_STORED, trust = TrustLevel.USER),
        )
        // Pairing-PSK sessions are already mid-pairing.
        coordinator.onSessionEvent(ready(category = PskCategory.PAIRING))
        advanceUntilIdle()

        assertTrue(rpc.requests.isEmpty())
    }

    @Test
    fun rpcFailureIsNonFatalAndDoesNotThrow() = runTest {
        val rpc = RpcRecorder()
        rpc.result = Result.failure(IllegalStateException("server rejected"))
        val coordinator = SilentPairingCoordinator(rpc::send, { "SP:0TOKEN" }, this)

        coordinator.onSessionEvent(ready())
        advanceUntilIdle()
        assertEquals(1, rpc.requests.size)
    }
}
