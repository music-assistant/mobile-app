package io.music_assistant.client.ui.compose.home.players

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.music_assistant.client.data.model.client.PlayerDataFixtures
import io.music_assistant.client.ui.compose.support.inScrollable
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SelectPlayerDialogTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `scrolls long list of players`() {
        val players = 0.until(25).map {
            PlayerDataFixtures.playerData()
        }

        composeTestRule.setContent {
            SelectPlayerDialog(
                selectedPlayer = players[0],
                players,
            )
        }

        composeTestRule.inScrollable("PlayersList") {
            onNode(hasText(players.last().player.name)).assertIsDisplayed()
        }
    }
}
