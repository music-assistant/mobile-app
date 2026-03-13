package io.music_assistant.client.ui.compose.home.players

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.music_assistant.client.data.model.client.PlayerDataFixtures
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
}