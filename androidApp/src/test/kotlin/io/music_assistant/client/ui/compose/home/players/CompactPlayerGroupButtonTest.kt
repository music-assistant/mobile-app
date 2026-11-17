package io.music_assistant.client.ui.compose.home.players

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.data.model.client.PlayerDataFixtures
import io.music_assistant.client.support.get
import io.music_assistant.client.support.rules.createTestRuleChain
import io.music_assistant.client.ui.compose.common.PlayerColors
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.players_group_settings
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The collapsed player puts the group button in its player chip, or in place of Next without one. */
@RunWith(AndroidJUnit4::class)
class CompactPlayerGroupButtonTest {
    @get:Rule
    val testRuleChain = createTestRuleChain()

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `shows the group count on the group button and opens grouping`() {
        var groupRequests = 0
        val player = PlayerDataFixtures.playerData(
            groupChildren = listOf(PlayerDataFixtures.bind().copy(isBound = true)),
        )

        setPlayer(player) { groupRequests++ }

        composeTestRule.onNodeWithText("+1").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(Res.string.players_group_settings.get())
            .assertIsDisplayed()
            .performClick()
        assertEquals(1, groupRequests)
    }

    @Test
    fun `shows no group button for a player without grouping`() {
        setPlayer(PlayerDataFixtures.playerData().copy(isLocal = true)) {}

        composeTestRule.onNodeWithContentDescription(Res.string.players_group_settings.get())
            .assertDoesNotExist()
    }

    @Test
    fun `the player chip of the wide row takes the group button`() {
        setPlayer(PlayerDataFixtures.playerData(), onSelectPlayer = {}) {}

        composeTestRule.onAllNodesWithContentDescription(Res.string.players_group_settings.get())
            .assertCountEquals(1)
    }

    private fun setPlayer(player: PlayerData, onSelectPlayer: (() -> Unit)? = null, onGroup: () -> Unit) {
        composeTestRule.setContent {
            CompactPlayerItem(
                modifier = Modifier,
                item = player,
                colors = PlayerColors(Color.Black, Color.White),
                sendSpinState = null,
                onSelectPlayer = onSelectPlayer,
                onGroup = onGroup,
            )
        }
    }
}
