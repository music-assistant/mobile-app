package io.music_assistant.client.imageloader

import io.music_assistant.client.webrtc.WebRTCHttpProxy
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class ArtworkRepositoryKeyTest {
    @Test
    fun url_query_variants_do_not_collide() = runTest {
        val transport = RecordingArtworkTransport()
        val repository = repository(transport)
        repository.load("https://example.test/art.png?size=1#one")
        repository.load("https://example.test/art.png?size=2#one")
        repository.load("https://example.test/art.png?size=1#two")
        assertEquals(3, transport.calls.size)
        assertNotEquals(
            transport.calls[0].first,
            transport.calls[1].first,
        )
    }

    @Test
    fun uppercase_owned_urls_keep_raw_url_key_identity() = runTest {
        val transport = RecordingArtworkTransport()
        val repository = repository(transport)
        val lowercase = repository.load("https://example.test/art.png")
        val uppercase = repository.load("HTTPS://example.test/art.png")
        assertEquals(
            listOf("https://example.test/art.png", "HTTPS://example.test/art.png"),
            transport.calls.map { it.first },
        )
        assertNotEquals(lowercase.token.identity.key, uppercase.token.identity.key)
    }

    @Test
    fun webrtc_server_keys_and_captured_proxy() = runTest {
        val transport = CapturingContextTransport()
        val httpProxy = WebRTCHttpProxy(sender = { _: JsonObject -> })
        val proxy = MutableArtworkServiceClient(serverId = "server-a", proxy = httpProxy)
        val repository = ArtworkRepository(
            store = testStore("webrtc-keys"),
            transport = transport,
            serviceClient = proxy,
            now = { 1_000L },
        )
        repository.load("mawebrtc://imageproxy/path?x=1")
        assertEquals("server-a", transport.contexts.single().serverId)
        proxy.reconnect("server-b", proxy.webRTCHttpProxy ?: error("proxy absent"))
        repository.load("mawebrtc://imageproxy/path?x=1")
        assertEquals("server-b", transport.contexts.last().serverId)
        assertNotEquals(transport.contexts[0].serverId, transport.contexts[1].serverId)
    }

    @Test
    fun absent_server_id_refuses_persist() = runTest {
        val transport = RecordingArtworkTransport()
        val store = testStore("absent-server")
        val repository = ArtworkRepository(
            store = store,
            transport = transport,
            serviceClient = MutableArtworkServiceClient(),
            now = { 1_000L },
        )
        assertFails { repository.load("mawebrtc://imageproxy/path") }
        assertEquals(0, transport.calls.size)
        assertNull(transport.calls.firstOrNull())
    }

    private fun repository(transport: ArtworkTransport): ArtworkRepository = ArtworkRepository(
        store = testStore("keys"),
        transport = transport,
        serviceClient = MutableArtworkServiceClient(),
        now = { 1_000L },
    )

    private class CapturingContextTransport : ArtworkTransport {
        val contexts = mutableListOf<ArtworkRequestContext>()
        override suspend fun fetch(url: String, context: ArtworkRequestContext): ArtworkResponse {
            contexts += context
            return response(url.encodeToByteArray(), "Cache-Control" to "max-age=60")
        }
    }
}
