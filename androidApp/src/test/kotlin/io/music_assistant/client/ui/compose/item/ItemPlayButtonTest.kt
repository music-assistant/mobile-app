package io.music_assistant.client.ui.compose.item

import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.music_assistant.client.data.model.client.AppMediaItemFixtures
import io.music_assistant.client.data.model.client.QueueOption
import io.music_assistant.client.support.get
import io.music_assistant.client.utils.support.MockFunction2
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.action_add_to_queue
import musicassistantclient.composeapp.generated.resources.action_insert_next
import musicassistantclient.composeapp.generated.resources.action_insert_next_and_play
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class ItemPlayButtonTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `clicking 'Start radio' triggers radio`() {
        val item = AppMediaItemFixtures.artist()
        val onPlayClick = MockFunction2<QueueOption, Boolean>()

        composeTestRule.setContent {
            ItemPlayButton(item = item, onPlayClick = onPlayClick)
        }

        composeTestRule.onNodeWithContentDescription("Play options").performClick()
        composeTestRule.onNodeWithText("Start radio").performClick()
        assertEquals(onPlayClick.arg1, QueueOption.REPLACE)
        assertEquals(onPlayClick.arg2, true)
    }

    @Test
    fun `does not show 'Start radio' for item that doesn't support it`() {
        val item = AppMediaItemFixtures.podcast()

        composeTestRule.setContent {
            ItemPlayButton(item = item, onPlayClick = MockFunction2())
        }

        composeTestRule.onNodeWithContentDescription("Play options").performClick()
        composeTestRule.onNodeWithText("Start radio").assertIsNotDisplayed()
    }

    @Test
    fun `clicking 'Play now' triggers PLAY in queue`() {
        val item = AppMediaItemFixtures.artist()
        val onPlayClick = MockFunction2<QueueOption, Boolean>()

        composeTestRule.setContent {
            ItemPlayButton(item = item, onPlayClick = onPlayClick)
        }

        composeTestRule.onNodeWithContentDescription("Play options").performClick()
        composeTestRule.onNodeWithText(Res.string.action_insert_next_and_play.get()).performClick()
        assertEquals(onPlayClick.arg1, QueueOption.PLAY)
        assertEquals(onPlayClick.arg2, false)
    }

    @Test
    fun `clicking 'Play next' triggers NEXT in queue`() {
        val item = AppMediaItemFixtures.artist()
        val onPlayClick = MockFunction2<QueueOption, Boolean>()

        composeTestRule.setContent {
            ItemPlayButton(item = item, onPlayClick = onPlayClick)
        }

        composeTestRule.onNodeWithContentDescription("Play options").performClick()
        composeTestRule.onNodeWithText(Res.string.action_insert_next.get()).performClick()
        assertEquals(onPlayClick.arg1, QueueOption.NEXT)
        assertEquals(onPlayClick.arg2, false)
    }

    @Test
    fun `clicking 'Add to queue' triggers ADD in queue`() {
        val item = AppMediaItemFixtures.artist()
        val onPlayClick = MockFunction2<QueueOption, Boolean>()

        composeTestRule.setContent {
            ItemPlayButton(item = item, onPlayClick = onPlayClick)
        }

        composeTestRule.onNodeWithContentDescription("Play options").performClick()
        composeTestRule.onNodeWithText(Res.string.action_add_to_queue.get()).performClick()
        assertEquals(onPlayClick.arg1, QueueOption.ADD)
        assertEquals(onPlayClick.arg2, false)
    }
}
