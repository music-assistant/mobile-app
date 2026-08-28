package io.music_assistant.client.ui.compose.common.items

import io.music_assistant.client.data.model.client.ImageInfo
import io.music_assistant.client.data.model.client.ImageType
import io.music_assistant.client.data.model.client.MediaType
import io.music_assistant.client.data.model.client.items.Album
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.Artist
import io.music_assistant.client.data.model.client.items.PlayableItem
import io.music_assistant.client.data.model.client.items.RecommendationFolder
import io.music_assistant.client.data.model.client.items.Track
import io.music_assistant.client.ui.compose.common.icons.TrackIcon
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals

/**
 * Pins the lazy-list media key contract.
 *
 * The crash that motivated this test fired Compose's "Key X was already used"
 * precondition during fling/prefetch on a LazyRow whose items were keyed by
 * `"${item::class.simpleName}_${item.itemId}"` — missing the provider, so a
 * cross-provider duplicate-itemId collision crashed the app on scroll.
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
        // The canonical key must not reduce to a naive delimiter join, or
        // (provider="apple_music", itemId="xyz") would equal
        // (provider="apple", itemId="music_xyz").
        val a = track(provider = "apple_music", itemId = "xyz")
        val b = track(provider = "apple", itemId = "music_xyz")
        assertNotEquals(a.lazyListKey(), b.lazyListKey())
    }

    @Test
    fun `occurrence keys distinguish duplicate canonical items`() {
        val duplicate = track(provider = "library", itemId = "same")
        val keys = listOf<AppMediaItem>(duplicate, duplicate).lazyListOccurrenceKeys()

        assertEquals(2, keys.size)
        assertEquals(2, keys.distinct().size)
        assertNotEquals(keys[0], keys[1])
    }

    @Test
    fun `first occurrence key is stable whether the item is alone or in a list`() {
        val duplicate = track(provider = "library", itemId = "same")
        val alone = listOf<AppMediaItem>(duplicate).lazyListOccurrenceKeys().single()
        val inList = listOf<AppMediaItem>(duplicate, duplicate).lazyListOccurrenceKeys()[0]

        assertEquals(alone, inList)
    }

    @Test
    fun `occurrence keys do not shift unrelated items after insertion`() {
        val a = track(provider = "library", itemId = "a")
        val b = track(provider = "library", itemId = "b")
        val c = track(provider = "library", itemId = "c")
        val inserted = track(provider = "library", itemId = "inserted")

        val originalKeysById = listOf<AppMediaItem>(a, b, c)
            .zip(listOf<AppMediaItem>(a, b, c).lazyListOccurrenceKeys())
            .associate { (item, key) -> item.itemId to key }
        val insertedKeysById = listOf<AppMediaItem>(inserted, a, b, c)
            .zip(listOf<AppMediaItem>(inserted, a, b, c).lazyListOccurrenceKeys())
            .associate { (item, key) -> item.itemId to key }

        assertEquals(originalKeysById["a"], insertedKeysById["a"])
        assertEquals(originalKeysById["b"], insertedKeysById["b"])
        assertEquals(originalKeysById["c"], insertedKeysById["c"])
    }

    @Test
    fun `playable occurrence keys distinguish duplicate items`() {
        // Exercises the PlayableItem-specific helper used by PlayablesTabContent.
        // Duplicate playable items must still get distinct keys (same canonical
        // key, different occurrence ordinal) without an unexplained cast at the
        // call site.
        val duplicate: PlayableItem = track(provider = "library", itemId = "same")
        val keys = listOf<PlayableItem>(duplicate, duplicate).playableLazyListOccurrenceKeys()

        assertEquals(2, keys.size)
        assertEquals(2, keys.distinct().size)
        assertNotEquals(keys[0], keys[1])
    }

    @Test
    fun `playable occurrence keys handle non AppMediaItem implementations`() {
        // The PlayableItem helper must not assume every PlayableItem is an
        // AppMediaItem: a standalone implementation is still disambiguated by
        // media type and occurrence ordinal.
        val playable = testPlayable(provider = "library", itemId = "same")
        val keys = listOf<PlayableItem>(playable, playable).playableLazyListOccurrenceKeys()

        assertEquals(2, keys.distinct().size)
        assertNotEquals(keys[0], keys[1])

        val other = testPlayable(provider = "library", itemId = "same", mediaType = MediaType.PODCAST_EPISODE)
        val otherKeys = listOf<PlayableItem>(other, other).playableLazyListOccurrenceKeys()
        assertFalse(otherKeys.any { it in keys })
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
        isPlayable = true,
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

    private fun testPlayable(
        provider: String,
        itemId: String,
        mediaType: MediaType = MediaType.TRACK,
    ): PlayableItem = object : PlayableItem {
        override val defaultIcon = TrackIcon
        override val parentName: String? = null
        override val mediaType: MediaType = mediaType
        override val itemId: String = itemId
        override val displayName: String = "name"
        override val version: String? = null
        override val duration: Double? = null
        override val uri: String? = null
        override val subtitle: String? = null
        override val images = emptyMap<ImageType, ImageInfo>()
        override val provider: String = provider
        override val isInLibrary: Boolean = provider == "library"
        override val favorite: Boolean? = null
        override val canStartEndlessMix: Boolean = false
        override val isPlayable: Boolean = true
        override fun withFavorite(favorite: Boolean?): PlayableItem = this
    }
}
