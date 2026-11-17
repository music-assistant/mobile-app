package io.music_assistant.client.ui.compose.common.items

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.Playlist
import io.music_assistant.client.data.model.client.items.RadioStation
import io.music_assistant.client.ui.compose.common.toDisplayString
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertTrue

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
                onPlayClick = { _, _ -> },
                playlistActions = StubPlaylistActions(),
                libraryActions = StubLibraryActions(),
                providerIconFetcher = { _, _, _ -> },
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
                onPlayClick = { _, _ -> },
                playlistActions = StubPlaylistActions(),
                libraryActions = StubLibraryActions(),
                providerIconFetcher = { _, _, _ -> },
            )
        }

        composeTestRule.onNodeWithText("title").assertIsDisplayed()
    }

    // 70% of this width is more than the 360 dp column cap.
    @Test
    @Config(qualifiers = "w950dp-h650dp-land")
    fun `stacked columns keep the column cap and do not overlap on a wide screen`() {
        val itemCategory = ItemCategory<Nothing>(
            id = "radios",
            title = "title".toDisplayString(),
            items = (1..4).map { radioStation("Radio $it") },
        )

        composeTestRule.setContent {
            CategoryRow(
                itemCategory = itemCategory,
                onNavigateClick = {},
                onPlayClick = { _, _ -> },
                playlistActions = StubPlaylistActions(),
                libraryActions = StubLibraryActions(),
                providerIconFetcher = { _, _, _ -> },
            )
        }

        // Radio 1 and Radio 2 share the first column, Radio 3 starts the second column.
        val first = composeTestRule.onNodeWithContentDescription("Radio 1").getUnclippedBoundsInRoot()
        val second = composeTestRule.onNodeWithContentDescription("Radio 3").getUnclippedBoundsInRoot()
        assertTrue(first.left >= 16.dp, "first column starts at ${first.left}, before the row padding")
        assertTrue(first.right <= second.left, "first column ends at ${first.right}, after ${second.left}")
    }
}

private fun radioStation(name: String): RadioStation =
    RadioStation(
        itemId = name,
        provider = "test",
        name = name,
        providerMappings = null,
        metadata = null,
        favorite = null,
        uri = "radio://$name",
        images = emptyMap(),
        version = null,
        isPlayable = true,
        isDynamic = false,
    )

private class StubPlaylistActions : PlaylistActions {
    override suspend fun getEditablePlaylists(): List<Playlist> {
        TODO("Not yet implemented")
    }

    override fun addToPlaylist(itemUris: List<String>, playlist: Playlist) {
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

    override fun onFavoriteChange(item: AppMediaItem, favorite: Boolean?) {
        TODO("Not yet implemented")
    }
}
