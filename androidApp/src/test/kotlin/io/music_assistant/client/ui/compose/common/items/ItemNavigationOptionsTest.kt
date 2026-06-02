package io.music_assistant.client.ui.compose.common.items

import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.music_assistant.client.data.model.client.AppMediaItemFixtures
import io.music_assistant.client.support.get
import io.music_assistant.client.ui.compose.common.OverflowMenu
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.action_go_to_album
import musicassistantclient.composeapp.generated.resources.action_go_to_artist
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
}
