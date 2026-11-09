package io.music_assistant.client.ui.compose.home.players

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.music_assistant.client.data.model.client.PlayerDataFixtures
import io.music_assistant.client.data.model.client.PlayerDelay
import io.music_assistant.client.data.model.client.PlayerType
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GroupSettingsTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val member = PlayerDataFixtures.bind().copy(isBound = true, volume = 40f, volumeSliderAccessible = true)
    private val other = PlayerDataFixtures.bind().copy(isBound = true)
    private val outside = PlayerDataFixtures.bind()
    private val pivot = PlayerDataFixtures.playerData(groupChildren = listOf(member, other, outside))

    @Test
    fun `the delay button opens the step panel of its player only`() {
        val adjustments = mutableListOf<Pair<String, Int>>()
        composeTestRule.setContent {
            GroupSettings(
                player = pivot,
                playerDelays = mapOf(
                    member.id to PlayerDelay(0, 0..5000),
                    outside.id to PlayerDelay(0, 0..5000),
                ),
                onAdjustPlayerDelay = { id, delta -> adjustments += id to delta },
            )
        }

        composeTestRule.onNodeWithContentDescription("Playback delay of ${other.name}").assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription("Playback delay of ${outside.name}").assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription("Playback delay of ${member.name}").performClick()

        val inPanel = hasAnyAncestor(isPopup())
        composeTestRule.onNode(inPanel.and(hasText("Speed up"))).assertIsDisplayed()
        composeTestRule.onAllNodes(inPanel.and(hasText("10 ms")))[0].performClick()
        // The slow-down row is dead at the bottom of the range.
        composeTestRule.onAllNodes(inPanel.and(hasText("1 ms")))[1].assertIsNotEnabled()

        assertEquals(listOf(member.id to 10), adjustments)
    }

    @Test
    fun `the volume button opens the slider of its player`() {
        composeTestRule.setContent { GroupSettings(player = pivot) }

        composeTestRule.onNodeWithContentDescription("Volume of ${outside.name}").assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription("Volume of ${member.name}").performClick()

        composeTestRule.onNode(hasAnyAncestor(isPopup()).and(hasText("40"))).assertIsDisplayed()
    }

    @Test
    fun `an ungrouped pivot shows its checkbox checked and disabled`() {
        composeTestRule.setContent {
            GroupSettings(player = PlayerDataFixtures.playerData(groupChildren = listOf(outside)), canLeaveGroup = true)
        }

        composeTestRule.onNodeWithContentDescription("Leave group")
            .assertIsOn()
            .assertIsNotEnabled()
    }

    @Test
    fun `a group player pivot has no reachable checkbox`() {
        composeTestRule.setContent {
            GroupSettings(
                player = PlayerDataFixtures.playerData(groupChildren = listOf(member), playerType = PlayerType.GROUP),
                canLeaveGroup = true,
            )
        }

        composeTestRule.onNodeWithContentDescription("Leave group").assertDoesNotExist()
        composeTestRule.onAllNodes(isToggleable()).assertCountEquals(1)
    }
}
