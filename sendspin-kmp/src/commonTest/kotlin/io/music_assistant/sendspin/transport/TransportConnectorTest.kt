package io.music_assistant.sendspin.transport

import io.music_assistant.sendspin.api.Endpoint
import io.music_assistant.sendspin.api.WireVersion
import io.music_assistant.sendspin.fakes.FakeTransport
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class TransportConnectorTest {
    private val transport = FakeTransport()
    private val openedUrls = mutableListOf<String>()
    private val connector = TransportConnector(openWebSocket = { url -> transport.also { openedUrls += url } })

    @Test
    fun webSocketWithoutTokenSendsNoAuth() = runTest {
        val result = connector.connect(Endpoint.WebSocket(URL, WireVersion.SPEC_1_0), clientId = "cid")

        assertSame(transport, result)
        assertEquals(listOf(URL), openedUrls)
        assertTrue(transport.sent.isEmpty())
    }

    @Test
    fun webSocketWithTokenSendsAuthFirst() = runTest {
        val result = async {
            connector.connect(Endpoint.WebSocket(URL, WireVersion.SPEC_1_0, authToken = "tok"), clientId = "cid")
        }
        runCurrent()
        assertEquals(listOf("""{"type":"auth","token":"tok","client_id":"cid"}"""), transport.sentTexts)

        transport.serverSends("""{"type":"auth_ok"}""")

        assertSame(transport, result.await())
    }

    @Test
    fun failedAuthClosesTheSocket() = runTest {
        transport.serverDrops()

        assertFailsWith<ProxyAuthException> {
            connector.connect(Endpoint.WebSocket(URL, WireVersion.SPEC_1_0, authToken = "tok"), clientId = "cid")
        }
        assertTrue(transport.closed)
    }

    private companion object {
        const val URL = "ws://server/sendspin"
    }
}
