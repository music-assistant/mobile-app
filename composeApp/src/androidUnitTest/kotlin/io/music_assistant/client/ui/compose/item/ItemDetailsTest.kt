package io.music_assistant.client.ui.compose.item

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.music_assistant.client.data.model.client.AppMediaItemFixtures
import io.music_assistant.client.data.model.server.QueueOption
import io.music_assistant.client.ui.compose.common.DataState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

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

    @Test
    fun `can play any item`() {
        var calledQueueOption: QueueOption?
        var calledRadio: Boolean?
        val onPlayClick: (QueueOption, Boolean) -> Unit = { queueOption, radio ->
            calledQueueOption = queueOption
            calledRadio = radio
        }

        val state = mutableStateOf(
            ItemDetailsViewModel.State(
                itemState = DataState.Loading(),
                albumsState = DataState.Loading(),
                playableItemsState = DataState.Loading()
            )
        )

        composeTestRule.setContent {
            ItemDetails(
                state = state.value,
                onPlayClick = onPlayClick
            )
        }

        listOf(
            AppMediaItemFixtures.artist(),
            AppMediaItemFixtures.album(),
            AppMediaItemFixtures.playlist(),
            AppMediaItemFixtures.podcast(),
            AppMediaItemFixtures.audiobook()
        ).forEach {
            calledQueueOption = null
            calledRadio = null

            state.value = ItemDetailsViewModel.State(
                itemState = DataState.Data(it),
                albumsState = DataState.NoData(),
                playableItemsState = DataState.NoData()
            )

            composeTestRule.onNodeWithContentDescription("Play now").performClick()
            assertEquals(calledQueueOption, QueueOption.REPLACE)
            assertEquals(calledRadio, false)
        }
    }

    @Test
    fun `can return from any item`() {
        var onBackCalled = false
        val onBack: () -> Unit = {
            onBackCalled = true
        }

        val state = mutableStateOf(
            ItemDetailsViewModel.State(
                itemState = DataState.Loading(),
                albumsState = DataState.Loading(),
                playableItemsState = DataState.Loading()
            )
        )

        composeTestRule.setContent {
            ItemDetails(
                state = state.value,
                onBack = onBack
            )
        }

        listOf(
            AppMediaItemFixtures.artist(),
            AppMediaItemFixtures.album(),
            AppMediaItemFixtures.playlist(),
            AppMediaItemFixtures.podcast(),
            AppMediaItemFixtures.audiobook()
        ).forEach {
            onBackCalled = false

            state.value = ItemDetailsViewModel.State(
                itemState = DataState.Data(it),
                albumsState = DataState.NoData(),
                playableItemsState = DataState.NoData()
            )

            composeTestRule.onNodeWithContentDescription("Back").performClick()
            assertEquals(onBackCalled, true)
        }
    }
}
