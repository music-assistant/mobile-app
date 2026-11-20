package io.music_assistant.client.watch

import io.music_assistant.client.watch.data.ServerImageDto
import io.music_assistant.client.watch.data.WatchServerConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WatchServerConfigTest {
    private val plain = WatchServerConfig("mass.local", 8095, isTls = false, token = "t")
    private val secure = WatchServerConfig("mass.example", 443, isTls = true, token = "t")

    @Test
    fun plainHostBuildsHttpAndWsUrls() {
        assertEquals("http://mass.local:8095", plain.webUrl)
        assertEquals("ws://mass.local:8095/ws", plain.wsUrl)
    }

    @Test
    fun tlsHostBuildsHttpsAndWssUrls() {
        assertEquals("https://mass.example:443", secure.webUrl)
        assertEquals("wss://mass.example:443/ws", secure.wsUrl)
    }

    @Test
    fun aMissingTokenLeavesTheWatchUnconfigured() {
        assertTrue(plain.isConfigured)
        assertFalse(plain.copy(token = "").isConfigured)
        assertFalse(plain.copy(host = "").isConfigured)
    }

    @Test
    fun aPubliclyReachableHttpsImageIsFetchedDirectly() {
        val image = ServerImageDto(
            path = "https://cdn.example/cover.jpg",
            provider = "spotify",
            remotelyAccessible = true,
        )
        assertEquals("https://cdn.example/cover.jpg", plain.artworkUrl(image, schemaVersion = 31))
    }

    @Test
    fun anOpaqueProxyIdIsUsedFromSchema31() {
        val image = ServerImageDto(path = "local/cover.jpg", provider = "filesystem", proxyId = "ab cd")
        val url = plain.artworkUrl(image, schemaVersion = 31)
        assertEquals(
            "http://mass.local:8095/imageproxy/ab%20cd?size=${WatchServerConfig.ARTWORK_SIZE}&checksum=",
            url,
        )
    }

    @Test
    fun anOlderSchemaFallsBackToThePathAndProviderQuery() {
        val image = ServerImageDto(path = "local/cover.jpg", provider = "filesystem", proxyId = "abcd")
        val url = plain.artworkUrl(image, schemaVersion = 30).orEmpty()
        assertTrue(url.startsWith("http://mass.local:8095/imageproxy?path="), "was $url")
        assertTrue(url.contains("provider=filesystem"), "was $url")
    }

    @Test
    fun anEmptyImagePathHasNoArtwork() {
        assertNull(plain.artworkUrl(ServerImageDto(), schemaVersion = 31))
    }

    @Test
    fun aProxiedImageUrlIsRebasedOntoTheWatchesOwnViewOfTheServer() {
        val pushed = "http://10.0.0.2:8095/imageproxy?path=x&provider=y"
        assertEquals(
            "http://mass.local:8095/imageproxy?path=x&provider=y",
            plain.rebaseImageUrl(pushed),
        )
    }

    @Test
    fun aCleartextUrlThatIsNotProxiedIsDropped() {
        assertNull(plain.rebaseImageUrl("http://10.0.0.2:8095/somewhere/cover.jpg"))
        assertEquals(
            "https://cdn.example/cover.jpg",
            plain.rebaseImageUrl("https://cdn.example/cover.jpg"),
        )
    }
}
