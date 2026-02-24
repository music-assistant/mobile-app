package io.music_assistant.client.ui.compose.item

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.music_assistant.client.data.model.client.AppMediaItemFixtures
import io.music_assistant.client.ui.compose.common.DataState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ItemDetailsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `displays artists`() {
        val artist = AppMediaItemFixtures.artist()
        val albums = listOf(
            AppMediaItemFixtures.album(artist = artist),
            AppMediaItemFixtures.album(artist = artist)
        )

        composeTestRule.setContent {
            ItemDetails(
                state = ItemDetailsViewModel.State(
                    DataState.Data(artist),
                    DataState.Data(albums),
                    DataState.NoData()
                )
            )
        }

        composeTestRule.onAllNodes(hasText(artist.name)).assertCountEquals(3)
        composeTestRule.onNodeWithText(albums[0].name).assertIsDisplayed()
        composeTestRule.onNodeWithText(albums[1].name).assertIsDisplayed()
    }

    @Test
    fun `displays albums`() {
        val artist = AppMediaItemFixtures.artist()
        val album = AppMediaItemFixtures.album(artist = artist)
        val tracks = AppMediaItemFixtures.tracks(listOf("Track 1", "Track 2"), album)

        composeTestRule.setContent {
            ItemDetails(
                state = ItemDetailsViewModel.State(
                    itemState = DataState.Data(album),
                    albumsState = DataState.NoData(),
                    playableItemsState = DataState.Data(tracks)
                )
            )
        }

        composeTestRule.onNodeWithText(album.name).assertIsDisplayed()
        composeTestRule.onAllNodes(hasText(artist.name)).assertCountEquals(3)
        composeTestRule.onNodeWithText(tracks[0].name).assertIsDisplayed()
        composeTestRule.onNodeWithText(tracks[1].name).assertIsDisplayed()
    }

    @Test
    fun `displays playlists`() {
        val playlist = AppMediaItemFixtures.playlist()
        val tracks = AppMediaItemFixtures.tracks(listOf("Track 1", "Track 2"))

        composeTestRule.setContent {
            ItemDetails(
                state = ItemDetailsViewModel.State(
                    itemState = DataState.Data(playlist),
                    albumsState = DataState.NoData(),
                    playableItemsState = DataState.Data(tracks)
                )
            )
        }

        composeTestRule.onNodeWithText(playlist.name).assertIsDisplayed()
        composeTestRule.onNodeWithText(tracks[0].name).assertIsDisplayed()
        composeTestRule.onNodeWithText(tracks[0].artists!![0].name).assertIsDisplayed()
        composeTestRule.onNodeWithText(tracks[1].name).assertIsDisplayed()
        composeTestRule.onNodeWithText(tracks[1].artists!![0].name).assertIsDisplayed()
    }

    @Test
    fun `displays podcasts`() {
        val podcast = AppMediaItemFixtures.podcast()
        val episodes =
            AppMediaItemFixtures.episodes(listOf("Episode 1", "Episode 2"), podcast = podcast)

        composeTestRule.setContent {
            ItemDetails(
                state = ItemDetailsViewModel.State(
                    itemState = DataState.Data(podcast),
                    albumsState = DataState.NoData(),
                    playableItemsState = DataState.Data(episodes)
                )
            )
        }

        composeTestRule.onNodeWithText(podcast.name).assertIsDisplayed()
        composeTestRule.onNodeWithText(episodes[0].name).assertIsDisplayed()
        composeTestRule.onNodeWithText(episodes[1].name).assertIsDisplayed()
    }

    @Test
    fun `displays audiobooks`() {
        val audiobook = AppMediaItemFixtures.audiobook(chapters = listOf("Chapter 1", "Chapter 2"))

        composeTestRule.setContent {
            ItemDetails(
                state = ItemDetailsViewModel.State(
                    itemState = DataState.Data(audiobook),
                    albumsState = DataState.NoData(),
                    playableItemsState = DataState.NoData()
                )
            )
        }

        composeTestRule.onNodeWithText(audiobook.name).assertIsDisplayed()
        composeTestRule.onNodeWithText(audiobook.chapters!![0].name).assertIsDisplayed()
        composeTestRule.onNodeWithText(audiobook.chapters[1].name).assertIsDisplayed()
    }
}
