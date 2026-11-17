package io.music_assistant.client.data.model.client.items

import io.music_assistant.client.data.model.client.AppMediaItemFixtures
import io.music_assistant.client.data.model.server.ProviderMapping
import io.music_assistant.client.data.model.server.ServerMediaItem
import io.music_assistant.client.ui.compose.common.items.ItemAction
import io.music_assistant.client.ui.compose.common.items.resolveDetailOverflowActions
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LibraryMembershipTest {
    private fun mapping(inLibrary: Boolean?) = ProviderMapping("s1", "spotify", "spotify-1", inLibrary = inLibrary)

    private fun libraryAlbum(inLibrary: Boolean?, favorite: Boolean? = null) = AppMediaItemFixtures.album().copy(
        provider = ServerMediaItem.LIBRARY_PROVIDER,
        providerMappings = listOf(mapping(inLibrary)),
        favorite = favorite,
    )

    private fun overflow(item: AppMediaItem) = resolveDetailOverflowActions(
        item = item,
        canEditLibrary = true,
        dislikeSupported = false,
        librarySupported = true,
        canAddToPlaylist = false,
    )

    @Test
    fun `a library row with a mapping in the library is in the library`() {
        assertTrue(libraryAlbum(inLibrary = true).isInLibrary)
    }

    @Test
    fun `a library row kept for a library track is not in the library`() {
        val album = libraryAlbum(inLibrary = false)
        assertTrue(album.isLibraryItem)
        assertFalse(album.isInLibrary)
    }

    @Test
    fun `a liked library row is in the library`() {
        assertTrue(libraryAlbum(inLibrary = false, favorite = true).isInLibrary)
    }

    @Test
    fun `a library row is in the library when the server sends no flag`() {
        assertTrue(libraryAlbum(inLibrary = null).isInLibrary)
    }

    @Test
    fun `an item of a music service is not in the library`() {
        val album = AppMediaItemFixtures.album().copy(providerMappings = listOf(mapping(inLibrary = true)))
        assertFalse(album.isLibraryItem)
        assertFalse(album.isInLibrary)
    }

    @Test
    fun `a library row outside the library offers adding it and no favorite actions`() {
        val actions = overflow(libraryAlbum(inLibrary = false))
        assertTrue(ItemAction.AddToLibrary in actions)
        assertFalse(ItemAction.RemoveFromLibrary in actions)
        assertFalse(ItemAction.Favorite in actions)
    }

    @Test
    fun `a library row in the library offers removing it and the favorite actions`() {
        val actions = overflow(libraryAlbum(inLibrary = true))
        assertTrue(ItemAction.RemoveFromLibrary in actions)
        assertTrue(ItemAction.Favorite in actions)
    }
}
