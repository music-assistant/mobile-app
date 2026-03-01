package io.music_assistant.client.ui.compose.item

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.music_assistant.client.data.model.client.AppMediaItemFixtures
import io.music_assistant.client.data.model.server.QueueOption
import org.junit.Rule
import org.junit.runner.RunWith
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class ItemPlayButtonTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `clicking start radio starts radio`() {
        var calledQueueOption: QueueOption? = null
        var calledRadio: Boolean? = null
        val onPlayClick: (QueueOption, Boolean) -> Unit = { queueOption, radio ->
            calledQueueOption = queueOption
            calledRadio = radio
        }

        val item = AppMediaItemFixtures.artist()

        composeTestRule.setContent {
            ItemPlayButton(item = item, onPlayClick = onPlayClick)
        }

        composeTestRule.onNodeWithContentDescription("Play options").performClick()
        composeTestRule.onNodeWithText("Start radio").performClick()
        assertEquals(calledQueueOption, QueueOption.REPLACE)
        assertEquals(calledRadio, true)
    }
}
