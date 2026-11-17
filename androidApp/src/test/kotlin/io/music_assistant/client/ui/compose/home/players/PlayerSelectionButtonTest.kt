package io.music_assistant.client.ui.compose.home.players

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.data.model.client.PlayerDataFixtures
import io.music_assistant.client.support.get
import io.music_assistant.client.support.rules.createTestRuleChain
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.cd_current_player
import musicassistantclient.composeapp.generated.resources.players_group_settings
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The player chip has a main section that selects a player and a section that opens grouping. */
@RunWith(AndroidJUnit4::class)
class PlayerSelectionButtonTest {
    @get:Rule
    val testRuleChain = createTestRuleChain()

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `each section opens its own sheet and the group section shows the count`() {
        var selections = 0
        var groupRequests = 0
        val player = PlayerDataFixtures.playerData(
            groupChildren = listOf(PlayerDataFixtures.bind().copy(isBound = true)),
        )
        setChip(player, onSelectPlayer = { selections++ }, onGroup = { groupRequests++ })

        composeTestRule.onNode(groupSection().and(hasText("+1"))).assertIsDisplayed().performClick()
        assertEquals(1, groupRequests)
        assertEquals(0, selections)

        composeTestRule.onNodeWithContentDescription(Res.string.cd_current_player.get(player.player.name))
            .performClick()
        assertEquals(1, selections)
        assertEquals(1, groupRequests)
    }

    @Test
    fun `a chip without a group action has one section`() {
        setChip(PlayerDataFixtures.playerData(), onGroup = null)

        composeTestRule.onNode(groupSection()).assertDoesNotExist()
    }

    @Test
    fun `a player without grouping has one section`() {
        setChip(PlayerDataFixtures.playerData().copy(isLocal = true), onGroup = {})

        composeTestRule.onNode(groupSection()).assertDoesNotExist()
    }

    private fun groupSection() =
        hasContentDescription(Res.string.players_group_settings.get())

    private fun setChip(player: PlayerData, onSelectPlayer: () -> Unit = {}, onGroup: (() -> Unit)?) {
        composeTestRule.setContent {
            PlayerSelectionButton(
                player = player,
                sendSpinState = null,
                onSelectPlayer = onSelectPlayer,
                onGroup = onGroup,
            )
        }
    }
}
