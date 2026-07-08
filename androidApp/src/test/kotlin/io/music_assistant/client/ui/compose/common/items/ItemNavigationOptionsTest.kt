package io.music_assistant.client.ui.compose.common.items

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.music_assistant.client.data.model.client.AppMediaItemFixtures
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.support.get
import io.music_assistant.client.ui.compose.common.OverflowMenu
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.action_choose_artist
import musicassistantclient.composeapp.generated.resources.action_go_to_album
import musicassistantclient.composeapp.generated.resources.action_go_to_artist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ItemNavigationOptionsTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `AppMediaItem#navigationOptions does not include go to artist for track without artists`() {
        val track = AppMediaItemFixtures.track(artists = emptyList())
        composeTestRule.setContent {
            val navigationOptions = track.navigationOptions { }
            OverflowMenu(
                expanded = true,
                options = navigationOptions,
            )
        }

        composeTestRule.onNodeWithText(Res.string.action_go_to_artist.get()).assertIsNotDisplayed()
    }

    @Test
    fun `AppMediaItem#navigationOptions does not include go to album for track without album`() {
        val track = AppMediaItemFixtures.track(album = null)
        composeTestRule.setContent {
            val navigationOptions = track.navigationOptions { }
            OverflowMenu(
                expanded = true,
                options = navigationOptions,
            )
        }

        composeTestRule.onNodeWithText(Res.string.action_go_to_album.get()).assertIsNotDisplayed()
    }

    @Test
    fun `single-artist track navigates straight to the artist without a dialog`() {
        val artist = AppMediaItemFixtures.artist(name = "Solo")
        val track = AppMediaItemFixtures.track(artists = listOf(artist))
        var navigatedTo: AppMediaItem? = null
        composeTestRule.setContent {
            val navigationOptions = track.navigationOptions { navigatedTo = it }
            OverflowMenu(
                expanded = true,
                options = navigationOptions,
            )
        }

        composeTestRule.onNodeWithText(Res.string.action_go_to_artist.get()).performClick()

        composeTestRule.onNodeWithText(Res.string.action_choose_artist.get()).assertIsNotDisplayed()
        assertEquals(artist, navigatedTo)
    }

    @Test
    fun `multi-artist track opens the choose-artist dialog and navigates to the picked artist`() {
        val first = AppMediaItemFixtures.artist(name = "First")
        val second = AppMediaItemFixtures.artist(name = "Second")
        val track = AppMediaItemFixtures.track(artists = listOf(first, second))
        var navigatedTo: AppMediaItem? = null
        composeTestRule.setContent {
            val navigationOptions = track.navigationOptions { navigatedTo = it }
            OverflowMenu(
                expanded = true,
                options = navigationOptions,
            )
        }

        composeTestRule.onNodeWithText(Res.string.action_go_to_artist.get()).performClick()

        // The dialog lists every artist and defers navigation until one is picked.
        composeTestRule.onNodeWithText(Res.string.action_choose_artist.get()).assertIsDisplayed()
        composeTestRule.onNodeWithText("First").assertIsDisplayed()
        composeTestRule.onNodeWithText("Second").assertIsDisplayed()
        assertNull(navigatedTo)

        composeTestRule.onNodeWithText("Second").performClick()

        assertEquals(second, navigatedTo)
    }
}
