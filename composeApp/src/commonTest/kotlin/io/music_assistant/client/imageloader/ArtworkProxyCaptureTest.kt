package io.music_assistant.client.imageloader

import io.music_assistant.client.webrtc.WebRTCHttpProxy
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

class ArtworkProxyCaptureTest {
    @Test
    fun proxy_disconnect_then_new_request() = runTest {
        val oldProxy = WebRTCHttpProxy(sender = { _: JsonObject -> })
        val newProxy = WebRTCHttpProxy(sender = { _: JsonObject -> })
        val service = MutableArtworkServiceClient(serverId = "server-a", proxy = oldProxy)
        val transport = InterleavedTransport()
        val repository = ArtworkRepository(
            store = testStore("proxy-capture"),
            transport = transport,
            serviceClient = service,
            now = { 1_000L },
            scope = kotlinx.coroutines.CoroutineScope(Dispatchers.Unconfined),
        )

        val old = async { repository.load(URL, ArtworkReadPolicy.DISABLED) }
        transport.started.await()
        service.disconnect()
        service.reconnect("server-b", newProxy)
        transport.releaseOld.complete(Unit)
        assertEquals("server-a", transport.contexts.first().serverId)
        assertEquals(oldProxy, transport.contexts.first().proxy)
        assertEquals(BYTES.toList(), old.await().bytes.toList())

        repository.load(URL)
        assertEquals("server-b", transport.contexts.last().serverId)
        assertEquals(newProxy, transport.contexts.last().proxy)
    }

    @Test
    fun capture_rejects_server_change_between_identity_reads() = runTest {
        val oldProxy = WebRTCHttpProxy(sender = { _: JsonObject -> })
        val newProxy = WebRTCHttpProxy(sender = { _: JsonObject -> })
        val service = MutableArtworkServiceClient(serverId = "server-a", proxy = oldProxy)
        var reads = 0
        service.onSessionStateRead = {
            if (++reads == 2) service.reconnect("server-b", newProxy)
        }
        val transport = RecordingArtworkTransport()
        val repository = ArtworkRepository(testStore("proxy-capture-unstable"), transport, service, now = { 1_000L })
        assertFails { repository.load(URL, ArtworkReadPolicy.DISABLED) }
        assertTrue(transport.calls.isEmpty())
    }

    @Test
    fun cached_webrtc_serves_without_live_proxy() = runTest {
        val proxy = WebRTCHttpProxy(sender = { _: JsonObject -> })
        val service = MutableArtworkServiceClient(serverId = "server-a", proxy = proxy)
        val transport = RecordingArtworkTransport()
        val repository = ArtworkRepository(testStore("proxy-cache-offline"), transport, service, now = { 1_000L })
        val url = "mawebrtc://imageproxy/cached.png"
        val first = repository.load(url)
        service.webRTCHttpProxy = null
        val second = repository.load(url)
        assertEquals(first.bytes.toList(), second.bytes.toList())
        assertEquals(1, transport.calls.count { it.first == url })
    }

    @Test
    fun disconnected_webrtc_miss_fails_without_persisting() = runTest {
        val service = MutableArtworkServiceClient(serverId = "server-a", proxy = null)
        val transport = object : ArtworkTransport {
            val calls = mutableListOf<String>()
            override suspend fun fetch(url: String, context: ArtworkRequestContext): ArtworkResponse {
                calls += url
                error("WebRTC proxy unavailable")
            }
        }
        val store = testStore("proxy-miss-offline")
        val repository = ArtworkRepository(store, transport, service, now = { 1_000L })
        val url = "mawebrtc://imageproxy/missing.png"
        assertFails { repository.load(url) }
        assertEquals(listOf(url), transport.calls)
        val identity = artworkIdentity("missing")
        assertTrue(store.read(identity, 1_000L) == null)
    }

    private class InterleavedTransport : ArtworkTransport {
        val contexts = mutableListOf<ArtworkRequestContext>()
        val releaseOld = CompletableDeferred<Unit>()
        val started = CompletableDeferred<Unit>()

        override suspend fun fetch(url: String, context: ArtworkRequestContext): ArtworkResponse {
            contexts += context
            started.complete(Unit)
            if (contexts.size == 1) releaseOld.await()
            return response(BYTES, "Cache-Control" to "max-age=60")
        }
    }

    private companion object {
        const val URL = "mawebrtc://imageproxy/art.png"
        val BYTES = byteArrayOf(4, 5, 6)
    }
}
