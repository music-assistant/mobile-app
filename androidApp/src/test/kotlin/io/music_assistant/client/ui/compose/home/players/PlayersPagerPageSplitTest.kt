package io.music_assistant.client.ui.compose.home.players

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.music_assistant.client.data.model.client.AppMediaItemFixtures
import io.music_assistant.client.data.model.client.PlayerDataFixtures
import io.music_assistant.client.data.model.client.PlayerDataFixtures.toQueue
import io.music_assistant.client.data.model.client.PlayerDataFixtures.toQueueTrack
import io.music_assistant.client.support.get
import io.music_assistant.client.support.rules.createTestRuleChain
import io.music_assistant.client.ui.compose.common.rememberToastState
import io.music_assistant.client.ui.compose.home.CollapsibleQueueSemantics
import io.music_assistant.client.ui.compose.home.HomeScreenViewModel
import io.music_assistant.client.ui.utils.DeviceChrome
import io.music_assistant.client.ui.utils.FoldRegion
import io.music_assistant.client.ui.utils.LocalDeviceChrome
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.queue_label
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.compose.viewmodel.koinViewModel
import org.robolectric.annotation.Config

/** The full player on a book-style foldable that is partially folded, as iOS reports it on iPhone Duo. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w950dp-h650dp-land")
class PlayersPagerPageSplitTest {
    @get:Rule
    val testRuleChain = createTestRuleChain()

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `the queue takes the left page and the player the right page, measured in the window`() {
        val track = AppMediaItemFixtures.track(name = "Song")
        val player = PlayerDataFixtures.playerData(listOf(track.toQueueTrack()).toQueue())
        // The hinge is measured from the window edge, and the pager starts to the right of it.
        val hinge = FoldRegion(start = 470.dp, end = 480.dp)
        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalDeviceChrome provides DeviceChrome(pageSplit = hinge, foldRegion = hinge),
            ) {
                Box(Modifier.padding(start = PAGER_START)) {
                    PlayersPager(
                        playerPagerState = rememberPagerState(pageCount = { 1 }),
                        state = HomeScreenViewModel.PlayersState.Data(listOf(player)),
                        homeScreenViewModel = koinViewModel(),
                        actionsViewModel = koinViewModel(),
                        dspSettingsViewModel = koinViewModel(),
                        providerViewModel = koinViewModel(),
                        queueDjViewModel = koinViewModel(),
                        expanded = true,
                        onClose = {},
                        contentPadding = PaddingValues(),
                        toastState = rememberToastState(),
                        navigateToItem = {},
                        onSelectPlayer = {},
                    )
                }
            }
        }

        composeTestRule.onNodeWithText(Res.string.queue_label.get()).assertIsDisplayed()
        val queue = composeTestRule.onNodeWithTag(CollapsibleQueueSemantics.QUEUE_TAG).getUnclippedBoundsInRoot()
        assertEquals(PAGER_START.value, queue.left.value, TOLERANCE_DP)
        assertEquals(hinge.start.value, queue.right.value, TOLERANCE_DP)
        val collapse = composeTestRule.onNodeWithContentDescription("Collapse").getUnclippedBoundsInRoot()
        assertTrue("The player starts at ${collapse.left}, after the hinge", collapse.left >= hinge.end)
    }

    private companion object {
        val PAGER_START = 40.dp
        const val TOLERANCE_DP = 0.5f
    }
}
