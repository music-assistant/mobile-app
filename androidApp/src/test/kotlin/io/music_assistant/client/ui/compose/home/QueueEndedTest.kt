package io.music_assistant.client.ui.compose.home

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.music_assistant.client.data.model.client.AppMediaItemFixtures
import io.music_assistant.client.data.model.client.PlayerDataFixtures.toQueue
import io.music_assistant.client.data.model.client.PlayerDataFixtures.toQueueTrack
import io.music_assistant.client.data.model.client.Queue
import io.music_assistant.client.support.get
import io.music_assistant.client.support.rules.createTestRuleChain
import io.music_assistant.client.ui.compose.common.DataState
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.queue_ended
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** A queue that played to its end says so, and its last item stops reading as current. */
@RunWith(AndroidJUnit4::class)
class QueueEndedTest {
    @get:Rule
    val testRuleChain = createTestRuleChain()

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun queueOnLastItem(ended: Boolean): Queue {
        val tracks = listOf("First", "Last").map { AppMediaItemFixtures.track(name = it).toQueueTrack() }
        val queue = tracks.toQueue()
        return queue.copy(info = queue.info.copy(currentIndex = 1, currentItem = tracks.last(), ended = ended))
    }

    private fun show(queue: Queue) {
        composeTestRule.setContent {
            Queue(
                queue = DataState.Data(queue),
                onGoToLibrary = {},
                isQueueExpanded = true,
                isCurrentPage = true,
                contentPadding = PaddingValues(),
                queueAction = {},
            )
        }
    }

    @Test
    fun `an ended queue shows that playback finished`() {
        show(queueOnLastItem(ended = true))

        composeTestRule.onNodeWithText(Res.string.queue_ended.get()).assertIsDisplayed()
        composeTestRule.onNodeWithText("Last").assertIsDisplayed()
    }

    @Test
    fun `a queue stopped on its last item does not show the ended label`() {
        show(queueOnLastItem(ended = false))

        composeTestRule.onNodeWithText("Last").assertIsDisplayed()
        composeTestRule.onNodeWithText(Res.string.queue_ended.get()).assertDoesNotExist()
    }
}
