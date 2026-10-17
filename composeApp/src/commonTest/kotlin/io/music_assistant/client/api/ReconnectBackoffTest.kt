package io.music_assistant.client.api

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReconnectBackoffTest {
    @Test
    fun reportsTheAttemptBeforeWaitingForTheNetwork() = runTest {
        val network = MutableStateFlow(false)
        val started = mutableListOf<Int>()
        val tried = mutableListOf<Int>()
        val loop = launch {
            runReconnectionLoop(
                networkAvailable = network,
                onAttemptStarting = { started += it },
                tryConnect = {
                    tried += it
                true
                },
            )
        }
        runCurrent()

        assertEquals(listOf(1), started)
        assertTrue(tried.isEmpty())

        network.value = true
        advanceUntilIdle()

        assertEquals(listOf(1), started)
        assertEquals(listOf(1), tried)
        assertTrue(loop.isCompleted)
    }
}
