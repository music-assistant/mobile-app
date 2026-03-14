package io.music_assistant.client.ui.compose.home.players

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
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
    fun `group settings not enabled for players without group children`() {
        val selectedPlayer = PlayerDataFixtures.playerData(groupChildren = emptyList())
        val otherPlayer =
            PlayerDataFixtures.playerData(groupChildren = listOf(PlayerDataFixtures.bind()))

        composeTestRule.setContent {
            SelectPlayerDialog(
                selectedPlayer = selectedPlayer,
                listOf(selectedPlayer, otherPlayer)
            )
        }

        composeTestRule.onNodeWithText("Group").assertIsNotEnabled()
    }

    @Test
    fun `group settings enabled for players with groups children`() {
        val otherPlayer = PlayerDataFixtures.playerData(groupChildren = emptyList())
        val selectedPlayer =
            PlayerDataFixtures.playerData(groupChildren = listOf(PlayerDataFixtures.bind()))

        composeTestRule.setContent {
            SelectPlayerDialog(
                selectedPlayer = selectedPlayer,
                listOf(otherPlayer, selectedPlayer)
            )
        }

        composeTestRule.onNodeWithText("Group").assertIsEnabled()
    }

    @Test
    fun `scrolls long list of players`() {
        val players = 0.until(25).map {
            PlayerDataFixtures.playerData()
        }

        composeTestRule.setContent {
            SelectPlayerDialog(
                selectedPlayer = players[0],
                players
            )
        }

        composeTestRule.inScrollable("PlayersList") {
            onNode(hasText(players.last().player.displayName)).assertIsDisplayed()
        }
    }

    @Test
    fun `group button is always shown on long list of players`() {
        val players = 0.until(25).map {
            PlayerDataFixtures.playerData()
        }

        composeTestRule.setContent {
            SelectPlayerDialog(
                selectedPlayer = players[0],
                players
            )
        }

        composeTestRule.inScrollable("PlayersList") {
            onNode(hasText(players.first().player.displayName)).assertIsDisplayed()
        }

        composeTestRule.onNodeWithText("Group").assertIsDisplayed()
    }
}