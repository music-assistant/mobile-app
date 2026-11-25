package io.music_assistant.client.wear.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WearServerConfigTest {
    private val config = WearServerConfig(
        host = "ma.local",
        port = 8095,
        isTls = false,
        token = "token",
    )

    @Test
    fun `builds the websocket and web urls from host port and tls`() {
        assertEquals("ws://ma.local:8095/ws", config.wsUrl)
        assertEquals("http://ma.local:8095", config.webUrl)
        val secure = config.copy(isTls = true, port = 443)
        assertEquals("wss://ma.local:443/ws", secure.wsUrl)
        assertEquals("https://ma.local:443", secure.webUrl)
    }

    @Test
    fun `puts the base path of a reverse proxy before the websocket path`() {
        val proxied = config.copy(basePath = "/ma")
        assertEquals("ws://ma.local:8095/ma/ws", proxied.wsUrl)
        assertEquals("http://ma.local:8095/ma", proxied.webUrl)
        assertEquals("ws://ma.local:8095/ma/ws", config.copy(basePath = "ma/").wsUrl)
    }

    @Test
    fun `fetches a publicly reachable image straight from the provider`() {
        val image = ServerImageDto(
            path = "https://cdn.example/cover.jpg",
            provider = "spotify",
            remotelyAccessible = true,
        )
        assertEquals("https://cdn.example/cover.jpg", config.artworkUrl(image, schemaVersion = 96))
    }

    @Test
    fun `uses the opaque imageproxy endpoint from schema 31`() {
        val image = ServerImageDto(path = "any", provider = "library", proxyId = "abc123")
        assertEquals(
            "http://ma.local:8095/imageproxy/abc123?size=512&checksum=",
            config.artworkUrl(image, schemaVersion = 96),
        )
    }

    @Test
    fun `falls back to the path and provider query below schema 31`() {
        val image = ServerImageDto(path = "/data/cover.jpg", provider = "filesystem", proxyId = "abc")
        val url = config.artworkUrl(image, schemaVersion = 30)
        assertTrue(url.orEmpty().startsWith("http://ma.local:8095/imageproxy?"), "was $url")
        assertTrue(url.orEmpty().contains("provider=filesystem"), "was $url")
    }

    @Test
    fun `has no artwork url without a path`() {
        assertNull(config.artworkUrl(ServerImageDto(), schemaVersion = 96))
    }

    @Test
    fun `rebases a server-issued imageproxy url onto the reachable origin`() {
        val raw = "http://192.168.1.10:8095/imageproxy/xyz?size=512&checksum="
        assertEquals(
            "http://ma.local:8095/imageproxy/xyz?size=512&checksum=",
            config.rebaseImageUrl(raw),
        )
    }

    @Test
    fun `keeps a non-proxy https url and drops a cleartext one`() {
        assertEquals("https://cdn.example/a.jpg", config.rebaseImageUrl("https://cdn.example/a.jpg"))
        // Cleartext from an unknown host has no imageproxy to rebase onto, so there is nothing
        // the watch can safely fetch.
        assertNull(config.rebaseImageUrl("http://cdn.example/a.jpg"))
        assertNull(config.rebaseImageUrl(""))
    }
}
