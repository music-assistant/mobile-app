package io.music_assistant.client.data

import io.music_assistant.client.api.Request
import io.music_assistant.client.ui.compose.common.action.PlayerAction
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class LocalCommandQueueTest {
    private val ready = MutableStateFlow(true)
    private val sent = mutableListOf<String>()
    private var failSends = false
    private var recoveries = 0

    private fun queue(scope: CoroutineScope) =
        LocalCommandQueue(ready, ::record, scope) { recoveries++ }

    private fun record(request: Request): Result<Unit> {
        sent += request.command
        return if (failSends) Result.failure(IllegalStateException("down")) else Result.success(Unit)
    }

    private fun request(command: String) = Request(command = command)

    /** Runs the background collector and any replay spacing (advanceUntilIdle skips background-only work). */
    private fun TestScope.settle() = advanceTimeBy(1_000)

    @Test
    fun commandQueuedDuringAnMaOnlyOutageIsSentOnceWhenReadinessReturns() = runTest {
        // Sendspin stays connected throughout: no activation event ever fires here.
        val queue = queue(backgroundScope)
        ready.value = false
        queue.sendOrQueue(PlayerAction.Pause, request("pause"))
        settle()
        assertEquals(emptyList(), sent)
        ready.value = true
        settle()
        assertEquals(listOf("pause"), sent)
        ready.value = false
        ready.value = true
        settle()
        assertEquals(listOf("pause"), sent, "nothing left to replay")
    }

    @Test
    fun readyCommandsAreSentDirectlyAndFailuresAreQueued() = runTest {
        val queue = queue(backgroundScope)
        failSends = true
        queue.sendOrQueue(PlayerAction.Next, request("next"))
        assertEquals(listOf("next"), sent)
        failSends = false
        ready.value = false
        ready.value = true
        settle()
        assertEquals(listOf("next", "next"), sent, "the failed send is replayed")
    }

    @Test
    fun aSendThatFailsAfterAReadinessBounceIsReplayedWithoutAnotherBounce() = runTest {
        val inFlight = CompletableDeferred<Result<Unit>>()
        var first = true
        val queue = LocalCommandQueue(
            ready,
            { request ->
                sent += request.command
                if (first) {
                    first = false
                    inFlight.await()
                } else {
                    Result.success(Unit)
                }
            },
            backgroundScope,
        ) { recoveries++ }
        val sending = launch { queue.sendOrQueue(PlayerAction.Pause, request("pause")) }
        runCurrent()
        ready.value = false
        ready.value = true // the drain runs against an empty queue
        settle()
        inFlight.complete(Result.failure(IllegalStateException("timed out")))
        sending.join()
        settle()
        assertEquals(listOf("pause", "pause"), sent, "replayed without another readiness change")
        settle()
        assertEquals(2, sent.size, "no retry loop")
    }

    @Test
    fun offlineDedupKeepsOnlyTheLastOfPlayPauseAndSeek() = runTest {
        val queue = queue(backgroundScope)
        ready.value = false
        queue.sendOrQueue(PlayerAction.Play, request("play"))
        queue.sendOrQueue(PlayerAction.Pause, request("pause"))
        queue.sendOrQueue(PlayerAction.SeekTo(10), request("seek10"))
        queue.sendOrQueue(PlayerAction.SeekTo(20), request("seek20"))
        queue.sendOrQueue(PlayerAction.TogglePlayPause, request("toggle"))
        queue.sendOrQueue(PlayerAction.TogglePlayPause, request("toggle"))
        ready.value = true
        settle()
        assertEquals(listOf("pause", "seek20"), sent)
    }

    @Test
    fun anOfflineCommandAsksForTheSessionBackAndDrainsWithoutAForegroundEvent() = runTest {
        val queue = LocalCommandQueue(ready, ::record, backgroundScope) {
            recoveries++
            ready.value = true // the recovery the queue asked for lands
        }
        ready.value = false
        queue.sendOrQueue(PlayerAction.Play, request("play"))
        assertEquals(1, recoveries)
        settle()
        assertEquals(listOf("play"), sent)
    }

    @Test
    fun everyQueuedCommandAsksForRecoveryButAReplayDoesNot() = runTest {
        val queue = queue(backgroundScope)
        ready.value = false
        queue.sendOrQueue(PlayerAction.Pause, request("pause"))
        queue.sendOrQueue(PlayerAction.Next, request("next"))
        assertEquals(2, recoveries)
        ready.value = true
        settle()
        assertEquals(listOf("pause", "next"), sent)
        assertEquals(2, recoveries, "the drain sends through the gate, it does not re-ask")
    }

    @Test
    fun aSendThatFailsWhileReadyDoesNotAskForRecovery() = runTest {
        // sendRequest already holds the session and kicks the gate on its own.
        val queue = queue(backgroundScope)
        failSends = true
        queue.sendOrQueue(PlayerAction.Next, request("next"))
        assertEquals(0, recoveries)
    }

    @Test
    fun clearDropsQueuedCommands() = runTest {
        val queue = queue(backgroundScope)
        ready.value = false
        queue.sendOrQueue(PlayerAction.Pause, request("pause"))
        queue.clear()
        ready.value = true
        settle()
        assertEquals(emptyList(), sent)
    }
}
