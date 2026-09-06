package io.music_assistant.client.data

import io.music_assistant.client.api.Request
import io.music_assistant.client.ui.compose.common.action.PlayerAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class LocalCommandQueueTest {
    private val ready = MutableStateFlow(true)
    private val sent = mutableListOf<String>()
    private var failSends = false

    private fun queue(scope: CoroutineScope) = LocalCommandQueue(ready, ::record, scope)

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
