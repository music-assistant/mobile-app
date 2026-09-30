package io.music_assistant.sendspin.transport

import io.music_assistant.sendspin.fakes.FakeTransport
import kotlinx.coroutines.async
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ProxyAuthTest {
    private val transport = FakeTransport()

    @Test
    fun sendsAuthFirstAndAcceptsAuthOk() = runTest {
        val auth = async { ProxyAuth.authenticate(transport, token = "tok", clientId = "cid") }
        runCurrent()
        assertEquals(listOf("""{"type":"auth","token":"tok","client_id":"cid"}"""), transport.sentTexts)
        transport.serverSends("""{"type":"auth_ok"}""")
        auth.await()
        assertTrue(transport.inbound.isEmpty)
    }

    @Test
    fun rejectsAnyOtherReply() = runTest {
        transport.serverSends("""{"type":"server/init","payload":{"server_id":"s","version":1}}""")
        assertFailsWith<ProxyAuthException> { ProxyAuth.authenticate(transport, "tok", "cid") }
    }

    @Test
    fun rejectsBinaryReply() = runTest {
        transport.serverSends(byteArrayOf(4, 0, 0, 0, 0, 0, 0, 0, 0))
        assertFailsWith<ProxyAuthException> { ProxyAuth.authenticate(transport, "tok", "cid") }
    }

    @Test
    fun failsWhenTheConnectionClosesFirst() = runTest {
        transport.serverDrops()
        assertFailsWith<ProxyAuthException> { ProxyAuth.authenticate(transport, "tok", "cid") }
    }

    @Test
    fun timesOutWithoutReply() = runTest {
        assertFailsWith<ProxyAuthException> {
            ProxyAuth.authenticate(transport, "tok", "cid", timeoutMillis = 1_000)
        }
        assertEquals(1_000, currentTime)
    }
}
