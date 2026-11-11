package io.music_assistant.client.ui.compose.common.items

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.music_assistant.client.data.model.client.AppMediaItemFixtures
import io.music_assistant.client.data.model.client.QueueOption
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.Playlist
import io.music_assistant.client.data.model.client.items.PodcastEpisode
import io.music_assistant.client.data.model.server.GrantedScopes
import io.music_assistant.client.data.model.server.Scope
import io.music_assistant.client.data.play.PlayMode
import io.music_assistant.client.settings.ViewMode
import io.music_assistant.client.ui.compose.common.LocalGrantedScopes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ItemSelectionUiTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val first = AppMediaItemFixtures.track(name = "First").copy(uri = "library://track/1")
    private val second = AppMediaItemFixtures.track(name = "Second").copy(uri = "library://track/2")
    private val selection = ItemSelection()
    private val played = mutableListOf<Pair<List<AppMediaItem>, PlayMode>>()
    private val removed = mutableListOf<List<AppMediaItem>>()

    @Test
    fun `select multiple starts the mode and a tap adds a track`() {
        setTracks(selection)

        longClick("First")
        composeTestRule.onNodeWithText("Select multiple").performClick()
        composeTestRule.onNodeWithText("1 selected").assertExists()

        composeTestRule.onNodeWithText("Second", useUnmergedTree = true).performClick()

        composeTestRule.onNodeWithText("2 selected").assertExists()
        assertEquals(listOf(first, second), selection.items)
    }

    @Test
    fun `a long press does not open the menu while selecting`() {
        setTracks(selection)
        selection.start(first)
        composeTestRule.waitForIdle()

        longClick("Second")

        composeTestRule.onNodeWithText("Play now").assertDoesNotExist()
        composeTestRule.onNodeWithText("Select multiple").assertDoesNotExist()
    }

    @Test
    fun `a selected row reports the selected state`() {
        setTracks(selection)
        selection.start(first)
        composeTestRule.waitForIdle()

        composeTestRule.onNode(
            SemanticsMatcher.expectValue(SemanticsProperties.Selected, true),
            useUnmergedTree = true,
        ).assertExists()
    }

    @Test
    fun `select all selects every shown track in list order and then hides`() {
        setTracks(selection)
        selection.start(second)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription("Select all").performClick()

        composeTestRule.onNodeWithText("2 selected").assertExists()
        assertEquals(listOf(first, second), selection.items)
        composeTestRule.onNodeWithContentDescription("Select all").assertDoesNotExist()
    }

    @Test
    fun `a bulk play sends the tracks in selection order and ends the mode`() {
        setTracks(selection)
        selection.start(second)
        selection.toggle(first)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription("Actions for selected items").performClick()
        composeTestRule.onNodeWithText("Add to playlist").assertExists()
        composeTestRule.onNodeWithText("Play now").performClick()

        assertEquals(listOf(listOf(second, first) to PlayMode(QueueOption.REPLACE)), played)
        assertFalse(selection.isActive)
        composeTestRule.onNodeWithText("Select multiple").assertDoesNotExist()
    }

    @Test
    fun `the menu has no select multiple entry without a selection host`() {
        setTracks(selection = null)

        longClick("First")

        composeTestRule.onNodeWithText("Play now").assertExists()
        composeTestRule.onNodeWithText("Select multiple").assertDoesNotExist()
    }

    @Test
    fun `an episode row offers select multiple and joins the selection`() {
        val episode = PodcastEpisode(
            itemId = "e1", provider = "test", name = "Episode", providerMappings = null, metadata = null,
            favorite = null, uri = "library://podcast_episode/e1", images = emptyMap(), duration = null,
            isPlayable = true, podcast = null, fullyPlayed = null, resumePositionMs = null, version = null,
        )
        setTracks(selection, extra = episode)

        longClick("Episode")
        composeTestRule.onNodeWithText("Select multiple").performClick()
        composeTestRule.onNodeWithText("First", useUnmergedTree = true).performClick()

        assertEquals(listOf(episode, first), selection.items)
    }

    private fun longClick(text: String) {
        composeTestRule.onNodeWithText(text, useUnmergedTree = true).performTouchInput { longClick() }
        composeTestRule.waitForIdle()
    }

    @Test
    fun `remove from playlist asks first and then removes the selection`() {
        setTracks(selection, canRemove = true)
        selection.start(first)
        selection.toggle(second)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription("Actions for selected items").performClick()
        composeTestRule.onNodeWithText("Remove from playlist").performClick()
        composeTestRule.onNodeWithText("Remove 2 items from this playlist?").assertExists()
        assertEquals(emptyList<List<AppMediaItem>>(), removed)

        composeTestRule.onNodeWithText("Remove").performClick()

        assertEquals(listOf(listOf(first, second)), removed)
        assertFalse(selection.isActive)
    }

    @Test
    fun `remove from playlist is absent outside an editable playlist`() {
        setTracks(selection)
        selection.start(first)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription("Actions for selected items").performClick()

        composeTestRule.onNodeWithText("Remove from playlist").assertDoesNotExist()
    }

    private fun setTracks(selection: ItemSelection?, canRemove: Boolean = false, extra: PodcastEpisode? = null) {
        composeTestRule.setContent {
            // Inspection mode short-circuits the Koin-backed seams of the rows.
            CompositionLocalProvider(
                LocalInspectionMode provides true,
                LocalGrantedScopes provides GrantedScopes(setOf(Scope.LIBRARY_WRITE)),
                LocalItemSelection provides selection,
            ) {
                Column {
                    SelectableItems(listOf(first, second))
                    selection?.takeIf { it.isActive }?.let {
                        SelectionBar(
                            selection = it,
                            onPlay = { tracks, mode -> played += tracks to mode },
                            playlistActions = NoPlaylistActions,
                            onRemoveFromPlaylist = { items: List<AppMediaItem> -> removed += items }.takeIf { canRemove },
                        )
                    }
                    extra?.let {
                        PodcastEpisodeWithMenu(
                            item = it,
                            viewMode = ViewMode.LIST,
                            onPlayOption = { _, _ -> },
                            libraryActions = NoLibraryActions,
                            providerIconFetcher = null,
                        )
                    }
                    listOf(first, second).forEach { track ->
                        TrackWithMenu(
                            item = track,
                            viewMode = ViewMode.LIST,
                            navigateToItem = {},
                            onPlayOption = { _, _ -> },
                            playlistActions = NoPlaylistActions,
                            libraryActions = NoLibraryActions,
                            providerIconFetcher = null,
                        )
                    }
                }
            }
        }
        composeTestRule.waitForIdle()
    }
}

private object NoPlaylistActions : PlaylistActions {
    override suspend fun getEditablePlaylists(): List<Playlist> = emptyList()

    override fun addToPlaylist(itemUris: List<String>, playlist: Playlist) = Unit

    override suspend fun createPlaylist(name: String): Playlist? = null
}

private object NoLibraryActions : LibraryActions {
    override fun onLibraryClick(item: AppMediaItem) = Unit

    override fun onFavoriteChange(item: AppMediaItem, favorite: Boolean?) = Unit
}
