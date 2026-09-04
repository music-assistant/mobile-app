package io.music_assistant.client.ui.compose.item

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.music_assistant.client.data.model.client.AppMediaItemFixtures
import io.music_assistant.client.support.get
import io.music_assistant.client.ui.compose.common.DataState
import io.music_assistant.client.ui.compose.common.ExtractedColors
import io.music_assistant.client.ui.compose.common.ExtractedColorsSource
import io.music_assistant.client.ui.compose.support.inScrollable
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.action_go_to_artist
import musicassistantclient.composeapp.generated.resources.cd_more
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private val NoColors = object : ExtractedColorsSource {
    override fun peek(imageUrl: String): ExtractedColors? = null
    override suspend fun fetch(imageUrl: String): ExtractedColors? = null
}

/**
 * Composes with inspection mode on so the Koin-backed dynamic-colors seams
 * (`rememberDynamicColorsEnabled`, `dynamicColorsMenuOption`) short-circuit to their
 * @Preview defaults instead of hitting a Koin graph this test doesn't start.
 */
private fun ComposeContentTestRule.setInspectableContent(content: @Composable () -> Unit) =
    setContent {
        CompositionLocalProvider(LocalInspectionMode provides true, content = content)
    }

@RunWith(AndroidJUnit4::class)
class ItemDetailsTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `displays album version`() {
        val artist = AppMediaItemFixtures.artist()
        val album = AppMediaItemFixtures.album(artist = artist, version = "Best Version")

        composeTestRule.setInspectableContent {
            ItemDetails(
                state = ItemDetailsViewModel.State(
                    itemState = DataState.Data(album),
                    albumsState = DataState.NoData(),
                    playableItemsState = DataState.Data(emptyList()),
                ),
                geEditablePlaylists = suspend { emptyList() },
                fetchColors = NoColors,
            )
        }

        composeTestRule.onAllNodes(hasText(album.name)).onFirst().assertIsDisplayed()
        composeTestRule.onAllNodes(hasText(album.version!!)).onFirst().assertIsDisplayed()
    }

    @Test
    fun `does not show go to artist button if there are none`() {
        val album = AppMediaItemFixtures.album(artist = null)

        composeTestRule.setInspectableContent {
            ItemDetails(
                state = ItemDetailsViewModel.State(
                    itemState = DataState.Data(album),
                    albumsState = DataState.NoData(),
                    playableItemsState = DataState.Data(emptyList()),
                ),
                fetchColors = NoColors,
            )
        }

        composeTestRule.onNodeWithContentDescription(Res.string.cd_more.get()).performClick()
        composeTestRule.onNodeWithText(Res.string.action_go_to_artist.get()).assertIsNotDisplayed()
    }

    @Test
    fun `displays podcasts`() {
        val podcast = AppMediaItemFixtures.podcast()
        val episodes =
            AppMediaItemFixtures.episodes(listOf("Episode 1", "Episode 2"), podcast = podcast)

        composeTestRule.setInspectableContent {
            ItemDetails(
                state = ItemDetailsViewModel.State(
                    itemState = DataState.Data(podcast),
                    albumsState = DataState.NoData(),
                    playableItemsState = DataState.Data(episodes),
                ),
                geEditablePlaylists = suspend { emptyList() },
                fetchColors = NoColors,
            )
        }

        composeTestRule.onAllNodes(hasText(podcast.displayName)).onFirst().assertIsDisplayed()
        composeTestRule.inScrollable("LazyVerticalGrid") {
            onNode(hasContentDescription(episodes[0].displayName)).assertIsDisplayed()
            onNode(hasContentDescription(episodes[1].displayName)).assertIsDisplayed()
        }
    }

    @Test
    fun `displays audiobooks`() {
        val audiobook = AppMediaItemFixtures.audiobook(chapters = listOf("Chapter 1", "Chapter 2"))

        composeTestRule.setInspectableContent {
            ItemDetails(
                state = ItemDetailsViewModel.State(
                    itemState = DataState.Data(audiobook),
                    albumsState = DataState.NoData(),
                    playableItemsState = DataState.NoData(),
                ),
                geEditablePlaylists = suspend { emptyList() },
                fetchColors = NoColors,
            )
        }

        composeTestRule.onAllNodes(hasText(audiobook.displayName)).onFirst().assertIsDisplayed()
        val chapters = audiobook.chapters!!
        composeTestRule.inScrollable("LazyVerticalGrid") {
            onNode(hasText(chapters[0].name)).assertIsDisplayed()
            onNode(hasText(chapters[1].name)).assertIsDisplayed()
        }
    }
}
