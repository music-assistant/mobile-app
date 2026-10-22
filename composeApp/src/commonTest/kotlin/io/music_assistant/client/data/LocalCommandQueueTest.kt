package io.music_assistant.client.data

import io.music_assistant.client.api.Request
import io.music_assistant.client.data.model.client.RepeatMode
import io.music_assistant.client.ui.compose.common.action.PlayerAction
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
        queue.sendOrQueue(PlayerAction.ToggleShuffle(current = false), request("shuffle"))
        assertEquals(listOf("shuffle"), sent)
        failSends = false
        ready.value = false
        ready.value = true
        settle()
        assertEquals(listOf("shuffle", "shuffle"), sent, "the failed send is replayed")
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
    fun offlineDedupKeepsOnlyTheLastPauseAndCancelsToggles() = runTest {
        val queue = queue(backgroundScope)
        ready.value = false
        queue.sendOrQueue(PlayerAction.Pause, request("pause1"))
        queue.sendOrQueue(PlayerAction.Pause, request("pause2"))
        queue.sendOrQueue(PlayerAction.ToggleRepeatMode(RepeatMode.OFF), request("repeat1"))
        queue.sendOrQueue(PlayerAction.ToggleRepeatMode(RepeatMode.ALL), request("repeat2"))
        queue.sendOrQueue(PlayerAction.TogglePlayPause, request("toggle"))
        queue.sendOrQueue(PlayerAction.TogglePlayPause, request("toggle"))
        ready.value = true
        settle()
        assertEquals(listOf("pause2", "repeat2"), sent)
    }

    @Test
    fun playbackCommandsAreSentWithinTheWaitOrDroppedButNeverReplayed() = runTest {
        val queue = queue(backgroundScope)
        ready.value = false
        for (action in listOf(PlayerAction.Play, PlayerAction.Next, PlayerAction.Previous, PlayerAction.SeekTo(5))) {
            queue.sendOrQueue(action, request("move"))
        }
        assertEquals(4, recoveries, "each one asks for the session back")
        ready.value = true
        settle()
        assertEquals(emptyList(), sent, "dropped after the wait: nothing moves playback after an outage")

        failSends = true
        queue.sendOrQueue(PlayerAction.Next, request("next"))
        failSends = false
        ready.value = false
        settle()
        ready.value = true
        settle()
        assertEquals(listOf("next"), sent, "a failed send is not replayed")
    }

    @Test
    fun aPlayDuringARecoveryGoesAfterTheQueuedPause() = runTest {
        val queue = queue(backgroundScope)
        ready.value = false
        queue.sendOrQueue(PlayerAction.Pause, request("pause"))
        var applied = false
        val play = async { queue.sendOrDrop(listOf(request("play"))) { applied = true } }
        runCurrent()
        assertFalse(applied, "nothing changes before the send")
        advanceTimeBy(2_000) // the session comes back within the wait
        ready.value = true
        settle()
        assertTrue(play.await())
        assertTrue(applied)
        assertEquals(listOf("pause", "play"), sent, "the offline pause lands first")
    }

    @Test
    fun aPauseWhileAPlayWaitsDropsThePlay() = runTest {
        val queue = queue(backgroundScope)
        ready.value = false
        val play = async { queue.sendOrDrop(listOf(request("play"))) }
        runCurrent()
        queue.sendOrQueue(PlayerAction.Pause, request("pause"))
        ready.value = true
        settle()
        assertFalse(play.await())
        assertEquals(listOf("pause"), sent, "the later pause wins")
    }

    @Test
    fun aStopWhileAPlayWaitsDropsThePlay() = runTest {
        val queue = queue(backgroundScope)
        ready.value = false
        val play = async { queue.sendOrDrop(listOf(request("play"))) }
        runCurrent()
        queue.sendOrQueue(PlayerAction.Stop, request("stop"))
        ready.value = true
        settle()
        assertFalse(play.await())
        assertEquals(listOf("stop"), sent, "the later stop wins")
    }

    @Test
    fun offlineDedupKeepsOnlyTheLastPauseOrStop() = runTest {
        val queue = queue(backgroundScope)
        ready.value = false
        queue.sendOrQueue(PlayerAction.Pause, request("pause"))
        queue.sendOrQueue(PlayerAction.Stop, request("stop"))
        ready.value = true
        settle()
        assertEquals(listOf("stop"), sent)
    }

    @Test
    fun anAppPauseWhileAPlayWaitsKeepsThePlayLast() = runTest {
        val queue = queue(backgroundScope)
        ready.value = false
        val play = async { queue.sendOrDrop(listOf(request("play"))) }
        runCurrent()
        queue.sendOrQueue(PlayerAction.Pause, request("resync"), userIntent = false)
        ready.value = true
        settle()
        assertTrue(play.await())
        assertEquals(listOf("resync", "play"), sent, "the user's play still wins")
    }

    @Test
    fun aFailedRequestDropsTheRestOfTheSequence() = runTest {
        val queue = queue(backgroundScope)
        failSends = true
        assertFalse(queue.sendOrDrop(listOf(request("pause"), request("play"))))
        assertEquals(listOf("pause"), sent, "no play without the pause before it")
    }

    @Test
    fun anOfflineCommandAsksForTheSessionBackAndDrainsWithoutAForegroundEvent() = runTest {
        val queue = LocalCommandQueue(ready, ::record, backgroundScope) {
            recoveries++
            ready.value = true // the recovery the queue asked for lands
        }
        ready.value = false
        queue.sendOrQueue(PlayerAction.Pause, request("pause"))
        assertEquals(1, recoveries)
        settle()
        assertEquals(listOf("pause"), sent)
    }

    @Test
    fun everyQueuedCommandAsksForRecoveryButAReplayDoesNot() = runTest {
        val queue = queue(backgroundScope)
        ready.value = false
        queue.sendOrQueue(PlayerAction.Pause, request("pause"))
        queue.sendOrQueue(PlayerAction.ToggleShuffle(current = false), request("shuffle"))
        assertEquals(2, recoveries)
        ready.value = true
        settle()
        assertEquals(listOf("pause", "shuffle"), sent)
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
