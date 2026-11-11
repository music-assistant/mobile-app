package io.music_assistant.client.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DirectTransportTest {
    // No WebSockets plugin, so every connect attempt fails at once.
    private val client = HttpClient(MockEngine { error("no request expected") })

    // Offline, so the reconnection loop reports its first attempt and parks.
    private val network = MutableStateFlow(false)

    private fun TestScope.transport(retryInitialFailure: Boolean) = DirectTransport(
        clientProvider = { client },
        connectionInfoProvider = { ConnectionInfo("localhost", 8095, isTls = false) },
        parentScope = backgroundScope,
        networkAvailable = network,
        retryInitialFailure = retryInitialFailure,
    )

    @Test
    fun aFreshConnectFailsOnAFailedFirstAttempt() = runTest {
        val transport = transport(retryInitialFailure = false)
        transport.connect()
        runCurrent()

        assertIs<TransportState.Failed>(transport.state.value)
    }

    @Test
    fun aResumedConnectRetriesAFailedFirstAttempt() = runTest {
        val transport = transport(retryInitialFailure = true)
        transport.connect()
        runCurrent()

        assertEquals(TransportState.Reconnecting(1), transport.state.value)
        transport.disconnect()
    }
}
