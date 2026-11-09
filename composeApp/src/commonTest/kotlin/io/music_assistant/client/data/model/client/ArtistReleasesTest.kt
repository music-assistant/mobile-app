package io.music_assistant.client.data.model.client

import io.music_assistant.client.data.model.server.ServerMediaItem
import io.music_assistant.client.ui.compose.common.items.supportsAddToPlaylist
import io.music_assistant.client.ui.compose.item.ItemList
import io.music_assistant.client.ui.compose.item.select
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArtistReleasesTest {
    private val album = AppMediaItemFixtures.album().copy(albumType = AlbumType.ALBUM)
    private val single = AppMediaItemFixtures.album().copy(albumType = AlbumType.SINGLE)
    private val ep = AppMediaItemFixtures.album().copy(albumType = AlbumType.EP)
    private val untyped = AppMediaItemFixtures.album().copy(albumType = null)

    @Test
    fun `singles and EPs go to their own row and all other albums to the albums row`() {
        val items = listOf(album, single, ep, untyped)

        assertEquals(listOf(album, untyped), items.filter(ArtistReleases.ALBUMS::contains))
        assertEquals(listOf(single, ep), items.filter(ArtistReleases.SINGLES_EPS::contains))
    }

    @Test
    fun `a release list selects its own releases`() {
        val items = listOf(album, single)

        assertEquals(listOf(single), ItemList.ArtistLibrary("1", ArtistReleases.SINGLES_EPS).select(items))
        assertEquals(listOf(album), ItemList.ArtistLibrary("1").select(items))
        assertEquals(items, ItemList.ArtistDiscography("1").select(items))
    }

    @Test
    fun `a MusicBrainz release is not playable and goes into no playlist`() {
        val release = album.copy(provider = ServerMediaItem.MUSICBRAINZ_PROVIDER, providerMappings = emptyList())

        assertTrue(release.isMusicBrainzRelease)
        assertFalse(release.isPlayable)
        assertFalse(release.supportsAddToPlaylist)
        assertTrue(album.isPlayable)
        assertTrue(album.supportsAddToPlaylist)
    }
}
