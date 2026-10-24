package io.music_assistant.client.ui.compose.library

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.music_assistant.client.data.model.client.AppMediaItemFixtures
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.Playlist
import io.music_assistant.client.settings.ViewMode
import io.music_assistant.client.ui.compose.common.items.LibraryActions
import io.music_assistant.client.ui.compose.common.items.PlaylistActions
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AdaptiveMediaGridTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private var hasMore by mutableStateOf(true)
    private var isLoadingMore by mutableStateOf(false)
    private var loadMoreCalls = 0

    @Test
    fun `loads more when hasMore turns true after the first composition`() {
        hasMore = false
        setGrid()
        assertEquals(0, loadMoreCalls)

        hasMore = true
        composeTestRule.waitForIdle()

        assertEquals(1, loadMoreCalls)
    }

    @Test
    fun `loads more when a page load in progress at the first composition ends`() {
        isLoadingMore = true
        setGrid()
        assertEquals(0, loadMoreCalls)

        isLoadingMore = false
        composeTestRule.waitForIdle()

        assertEquals(1, loadMoreCalls)
    }

    // Three rows keep the last visible row within the load-more distance of the end.
    private fun setGrid() {
        val items = List(3) { AppMediaItemFixtures.track() }
        composeTestRule.setContent {
            // Inspection mode short-circuits the Koin-backed seams of the rows.
            CompositionLocalProvider(LocalInspectionMode provides true) {
                AdaptiveMediaGrid(
                    items = items,
                    isLoadingMore = isLoadingMore,
                    hasMore = hasMore,
                    viewMode = ViewMode.LIST,
                    onNavigateClick = {},
                    onPlayClick = { _, _ -> },
                    onLoadMore = { loadMoreCalls++ },
                    playlistActions = StubPlaylistActions(),
                    libraryActions = StubLibraryActions(),
                    contentPadding = PaddingValues(),
                )
            }
        }
        composeTestRule.waitForIdle()
    }
}

private class StubPlaylistActions : PlaylistActions {
    override suspend fun getEditablePlaylists(): List<Playlist> = emptyList()

    override fun addToPlaylist(itemUri: String?, playlist: Playlist) = Unit

    override suspend fun createPlaylist(name: String): Playlist? = null
}

private class StubLibraryActions : LibraryActions {
    override fun onLibraryClick(item: AppMediaItem) = Unit

    override fun onFavoriteChange(item: AppMediaItem, favorite: Boolean?) = Unit
}
