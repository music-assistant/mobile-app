package io.music_assistant.client.ui.compose

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.data.model.client.PlayerDataFixtures
import io.music_assistant.client.data.model.client.Queue
import io.music_assistant.client.support.get
import io.music_assistant.client.ui.compose.common.DataState
import io.music_assistant.client.ui.compose.home.players.PlayerControls
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.cd_shuffle_off
import musicassistantclient.composeapp.generated.resources.cd_shuffle_on
import musicassistantclient.composeapp.generated.resources.cd_shuffle_smart
import org.jetbrains.compose.resources.StringResource
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Pins the three states of the shuffle button: off, plain on, and smart shuffle. */
@RunWith(AndroidJUnit4::class)
class PlayerControlsShuffleTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun playerWith(shuffleEnabled: Boolean, smartShuffleActive: Boolean): PlayerData {
        val base = PlayerDataFixtures.playerData()
        val queue = (base.queue as DataState.Data<Queue>).data
        val info = queue.info.copy(shuffleEnabled = shuffleEnabled, smartShuffleActive = smartShuffleActive)
        return base.copy(queue = DataState.Data(queue.copy(info = info)))
    }

    private fun assertShows(shuffle: Boolean, smart: Boolean, expected: StringResource) {
        composeTestRule.setContent {
            PlayerControls(
                playerData = playerWith(shuffle, smart),
                playerAction = { _, _ -> },
                mainButtonSize = 48.dp,
            )
        }
        composeTestRule.onNodeWithContentDescription(expected.get()).assertIsDisplayed()
    }

    @Test
    fun `shuffle off`() = assertShows(shuffle = false, smart = false, expected = Res.string.cd_shuffle_off)

    @Test
    fun `plain shuffle on`() = assertShows(shuffle = true, smart = false, expected = Res.string.cd_shuffle_on)

    @Test
    fun `smart shuffle on`() = assertShows(shuffle = true, smart = true, expected = Res.string.cd_shuffle_smart)

    @Test
    fun `smart flag without shuffle shows off`() =
        assertShows(shuffle = false, smart = true, expected = Res.string.cd_shuffle_off)
}
