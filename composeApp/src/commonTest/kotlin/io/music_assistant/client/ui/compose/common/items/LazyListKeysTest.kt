package io.music_assistant.client.ui.compose.common.items

import io.music_assistant.client.data.model.client.items.Album
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.Artist
import io.music_assistant.client.data.model.client.items.RecommendationFolder
import io.music_assistant.client.data.model.client.items.Track
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Pins the [AppMediaItem.lazyListKey] contract.
 *
 * The crash that motivated this test (TestFlight build-29 LayoutNode
 * regression) fired Compose's "Key X was already used" precondition
 * (SubcomposeLayout.kt:614) during fling/prefetch on a LazyRow whose items
 * were keyed by `"${item::class.simpleName}_${item.itemId}"` — missing the
 * provider, so a cross-provider duplicate-itemId collision crashed the app
 * on scroll. CM 1.10.3 got away with it; CM 1.11.0's tightened prefetch
 * actually subcomposes the colliding sibling.
 *
 * Contract:
 *   - Any two AppMediaItems that differ in (mediaType, provider, itemId)
 *     MUST produce different keys.
 *   - Identical AppMediaItems MUST produce the same key (stable).
 */
class LazyListKeysTest {
    @Test
    fun `cross-provider same itemId produces different keys`() {
        val spotify = track(provider = "spotify", itemId = "abc")
        val apple = track(provider = "apple_music", itemId = "abc")
        assertNotEquals(spotify.lazyListKey(), apple.lazyListKey())
    }

    @Test
    fun `same media type same provider same itemId produces same key`() {
        val a = track(provider = "library", itemId = "xyz")
        val b = track(provider = "library", itemId = "xyz")
        assertEquals(a.lazyListKey(), b.lazyListKey())
    }

    @Test
    fun `different media type same provider same itemId produces different keys`() {
        val asTrack = track(provider = "library", itemId = "foo")
        val asAlbum = album(provider = "library", itemId = "foo")
        assertNotEquals(asTrack.lazyListKey(), asAlbum.lazyListKey())
    }

    @Test
    fun `cross-provider mixed-type list produces fully-distinct keys`() {
        val items: List<AppMediaItem> = listOf(
            track(provider = "spotify", itemId = "x"),
            track(provider = "apple_music", itemId = "x"),
            track(provider = "ytmusic", itemId = "x"),
            album(provider = "library", itemId = "x"),
        )
        val keys = items.map { it.lazyListKey() }
        assertEquals(items.size, keys.distinct().size)
    }

    @Test
    fun `recommendation folders distinguished by provider`() {
        // Two folders with the same canonical itemId (e.g. "random_albums")
        // but coming from different providers must still produce distinct
        // keys; otherwise scrolling the home screen with such data crashes.
        val libraryFolder = recommendationFolder(provider = "library", itemId = "random_albums")
        val spotifyFolder = recommendationFolder(provider = "spotify", itemId = "random_albums")
        assertNotEquals(libraryFolder.lazyListKey(), spotifyFolder.lazyListKey())
    }

    @Test
    fun `recommendation folder does not collide with same-id same-provider artist`() {
        // RecommendationFolder's mediaType must be distinct from MediaType.ARTIST
        // so a folder and a real Artist sharing (provider, itemId) get different
        // keys when they ever end up in the same lazy list.
        val folder = recommendationFolder(provider = "library", itemId = "random_albums")
        val artist = artist(provider = "library", itemId = "random_albums")
        assertNotEquals(folder.lazyListKey(), artist.lazyListKey())
    }

    @Test
    fun `underscore-bearing provider does not collide with other field arrangements`() {
        // Compose accepts Any? as a slot key, so the key MUST be a structurally
        // typed value (Triple) rather than an underscore-delimited string —
        // otherwise (provider="apple_music", itemId="xyz") collides with
        // (provider="apple", itemId="music_xyz").
        val a = track(provider = "apple_music", itemId = "xyz")
        val b = track(provider = "apple", itemId = "music_xyz")
        assertNotEquals(a.lazyListKey(), b.lazyListKey())
    }

    private fun track(provider: String, itemId: String): Track = Track(
        itemId = itemId,
        provider = provider,
        name = "name",
        providerMappings = null,
        metadata = null,
        favorite = null,
        uri = null,
        images = emptyMap(),
        duration = null,
        artists = emptyList(),
        album = null,
        discNumber = null,
        trackNumber = null,
        position = null,
        version = null,
    )

    private fun artist(provider: String, itemId: String): Artist = Artist(
        itemId = itemId,
        provider = provider,
        name = "name",
        providerMappings = null,
        metadata = null,
        favorite = null,
        uri = null,
        images = emptyMap(),
    )

    private fun album(provider: String, itemId: String): Album = Album(
        itemId = itemId,
        provider = provider,
        name = "name",
        providerMappings = null,
        metadata = null,
        favorite = null,
        uri = null,
        images = emptyMap(),
        version = null,
        year = null,
        artists = emptyList(),
    )

    private fun recommendationFolder(provider: String, itemId: String): RecommendationFolder =
        RecommendationFolder(
            itemId = itemId,
            provider = provider,
            name = "name",
            uri = null,
            images = emptyMap(),
            items = null,
        )
}
