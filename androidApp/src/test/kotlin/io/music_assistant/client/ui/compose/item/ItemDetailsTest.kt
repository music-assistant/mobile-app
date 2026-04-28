package io.music_assistant.client.ui.compose.item

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.music_assistant.client.data.model.client.AppMediaItemFixtures
import io.music_assistant.client.data.model.server.QueueOption
import io.music_assistant.client.ui.compose.common.DataState
import io.music_assistant.client.ui.compose.support.inScrollable
import io.music_assistant.client.utils.support.MockFunction0
import io.music_assistant.client.utils.support.MockFunction2
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
            AppMediaItemFixtures.album(artist = artist),
        )

        composeTestRule.setContent {
            ItemDetails(
                state = ItemDetailsViewModel.State(
                    DataState.Data(artist),
                    DataState.Data(albums),
                    DataState.NoData(),
                ),
                geEditablePlaylists = suspend { emptyList() },
            )
        }

        composeTestRule.onAllNodes(hasText(artist.name)).onFirst().assertIsDisplayed()
        composeTestRule.inScrollable("LazyVerticalGrid") {
            onNode(hasText(albums[0].name)).assertIsDisplayed()
            onNode(hasText(albums[1].name)).assertIsDisplayed()
        }
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
                    playableItemsState = DataState.Data(tracks),
                ),
                geEditablePlaylists = suspend { emptyList() },
            )
        }

        composeTestRule.onAllNodes(hasText(album.name)).onFirst().assertIsDisplayed()
        composeTestRule.onAllNodes(hasText(artist.name)).onFirst().assertIsDisplayed()
        composeTestRule.inScrollable("LazyVerticalGrid") {
            onNode(hasText(tracks[0].name)).assertIsDisplayed()
            onNode(hasText(tracks[1].name)).assertIsDisplayed()
        }
    }

    @Test
    fun `displays album version`() {
        val artist = AppMediaItemFixtures.artist()
        val album = AppMediaItemFixtures.album(artist = artist, version = "Best Version")

        composeTestRule.setContent {
            ItemDetails(
                state = ItemDetailsViewModel.State(
                    itemState = DataState.Data(album),
                    albumsState = DataState.NoData(),
                    playableItemsState = DataState.Data(emptyList()),
                ),
                geEditablePlaylists = suspend { emptyList() },
            )
        }

        composeTestRule.onAllNodes(hasText(album.version!!)).onFirst().assertIsDisplayed()
    }

    @Test
    fun `does not show go to artist button if there are none`() {
        val album = AppMediaItemFixtures.album(artist = null)

        composeTestRule.setContent {
            ItemDetails(
                state = ItemDetailsViewModel.State(
                    itemState = DataState.Data(album),
                    albumsState = DataState.NoData(),
                    playableItemsState = DataState.Data(emptyList()),
                ),
            )
        }

        composeTestRule.onNodeWithContentDescription("More").performClick()
        composeTestRule.onNodeWithText("Go to artist").assertIsNotDisplayed()
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
                    playableItemsState = DataState.Data(tracks),
                ),
                geEditablePlaylists = suspend { emptyList() },
            )
        }

        composeTestRule.onAllNodes(hasText(playlist.name)).onFirst().assertIsDisplayed()
        composeTestRule.inScrollable("LazyVerticalGrid") {
            onNode(hasText(tracks[0].name)).assertIsDisplayed()
            onNode(hasText(tracks[0].artists!![0].name)).assertIsDisplayed()
            onNode(hasText(tracks[1].name)).assertIsDisplayed()
            onNode(hasText(tracks[1].artists!![0].name)).assertIsDisplayed()
        }
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
                    playableItemsState = DataState.Data(episodes),
                ),
                geEditablePlaylists = suspend { emptyList() },
            )
        }

        composeTestRule.onAllNodes(hasText(podcast.name)).onFirst().assertIsDisplayed()
        composeTestRule.inScrollable("LazyVerticalGrid") {
            onNode(hasText(episodes[0].name)).assertIsDisplayed()
            onNode(hasText(episodes[1].name)).assertIsDisplayed()
        }
    }

    @Test
    fun `displays audiobooks`() {
        val audiobook = AppMediaItemFixtures.audiobook(chapters = listOf("Chapter 1", "Chapter 2"))

        composeTestRule.setContent {
            ItemDetails(
                state = ItemDetailsViewModel.State(
                    itemState = DataState.Data(audiobook),
                    albumsState = DataState.NoData(),
                    playableItemsState = DataState.NoData(),
                ),
                geEditablePlaylists = suspend { emptyList() },
            )
        }

        composeTestRule.onAllNodes(hasText(audiobook.name)).onFirst().assertIsDisplayed()
        val chapters = audiobook.chapters!!
        composeTestRule.inScrollable("LazyVerticalGrid") {
            onNode(hasText(chapters[0].name)).assertIsDisplayed()
            onNode(hasText(chapters[1].name)).assertIsDisplayed()
        }
    }

    @Test
    fun `can play any item`() {
        val state = mutableStateOf(
            ItemDetailsViewModel.State(
                itemState = DataState.Loading(),
                albumsState = DataState.Loading(),
                playableItemsState = DataState.Loading(),
            ),
        )

        val onPlayClick = MockFunction2<QueueOption, Boolean>()

        composeTestRule.setContent {
            ItemDetails(
                state = state.value,
                geEditablePlaylists = suspend { emptyList() },
                onPlayClick = onPlayClick,
            )
        }

        listOf(
            AppMediaItemFixtures.artist(),
            AppMediaItemFixtures.album(),
            AppMediaItemFixtures.playlist(),
            AppMediaItemFixtures.podcast(),
            AppMediaItemFixtures.audiobook(),
        ).forEach {
            onPlayClick.reset()

            state.value = ItemDetailsViewModel.State(
                itemState = DataState.Data(it),
                albumsState = DataState.NoData(),
                playableItemsState = DataState.NoData(),
            )

            composeTestRule.onAllNodes(hasContentDescription("Play now")).onFirst().performClick()
            assertEquals(onPlayClick.arg1, QueueOption.REPLACE)
            assertEquals(onPlayClick.arg2, false)
        }
    }

    @Test
    fun `can return from any item`() {
        val onBack = MockFunction0()
        val state = mutableStateOf(
            ItemDetailsViewModel.State(
                itemState = DataState.Loading(),
                albumsState = DataState.Loading(),
                playableItemsState = DataState.Loading(),
            ),
        )

        composeTestRule.setContent {
            ItemDetails(
                state = state.value,
                onBack = onBack,
                geEditablePlaylists = suspend { emptyList() },
            )
        }

        listOf(
            AppMediaItemFixtures.artist(),
            AppMediaItemFixtures.album(),
            AppMediaItemFixtures.playlist(),
            AppMediaItemFixtures.podcast(),
            AppMediaItemFixtures.audiobook(),
        ).forEach {
            onBack.reset()

            state.value = ItemDetailsViewModel.State(
                itemState = DataState.Data(it),
                albumsState = DataState.NoData(),
                playableItemsState = DataState.NoData(),
            )

            composeTestRule.onNode(hasContentDescription("Back")).performClick()
            assertEquals(onBack.wasCalled, true)
        }
    }
}
