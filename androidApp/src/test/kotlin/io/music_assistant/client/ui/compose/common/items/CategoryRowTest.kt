package io.music_assistant.client.ui.compose.common.items

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.Playlist
import io.music_assistant.client.ui.compose.common.toDisplayString
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CategoryRowTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `is not visible if ItemCategory items is empty`() {
        val itemCategory = ItemCategory<Nothing>(
            id = "blah",
            title = "title".toDisplayString(),
            items = emptyList(),
        )

        composeTestRule.setContent {
            CategoryRow(
                itemCategory = itemCategory,
                onNavigateClick = {},
                onPlayClick = { _, _, _, _ -> },
                playlistActions = StubPlaylistActions(),
                libraryActions = StubLibraryActions(),
                providerIconFetcher = { _, _ -> },
            )
        }

        composeTestRule.onNodeWithText("title").assertIsNotDisplayed()
    }

    @Test
    fun `is visible if ItemCategory items is empty and filter is not null`() {
        val itemCategory = ItemCategory(
            id = "blah",
            title = "title".toDisplayString(),
            items = emptyList(),
            filter = ItemCategory.Filter(
                label = "filter".toDisplayString(),
                options = listOf("option"),
                labelTransform = { it.toDisplayString() },
            ),
        )

        composeTestRule.setContent {
            CategoryRow(
                itemCategory = itemCategory,
                onNavigateClick = {},
                onPlayClick = { _, _, _, _ -> },
                playlistActions = StubPlaylistActions(),
                libraryActions = StubLibraryActions(),
                providerIconFetcher = { _, _ -> },
            )
        }

        composeTestRule.onNodeWithText("title").assertIsDisplayed()
    }
}

private class StubPlaylistActions : PlaylistActions {
    override suspend fun getEditablePlaylists(): List<Playlist> {
        TODO("Not yet implemented")
    }

    override fun addToPlaylist(itemUri: String?, playlist: Playlist) {
        TODO("Not yet implemented")
    }

    override suspend fun createPlaylist(name: String): Playlist? {
        TODO("Not yet implemented")
    }
}

private class StubLibraryActions : LibraryActions {
    override fun onLibraryClick(item: AppMediaItem) {
        TODO("Not yet implemented")
    }

    override fun onFavoriteClick(item: AppMediaItem) {
        TODO("Not yet implemented")
    }
}
