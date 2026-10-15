package io.music_assistant.client.ui.compose.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QueueMenuTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `menu remains scrollable when popup supplies unbounded height`() {
        composeTestRule.setContent {
            Layout(content = {
                // Material 3 DropdownMenuContent applies these modifiers in this order.
                Column(
                    Modifier.queueMenuHeight()
                        .padding(vertical = 8.dp)
                        .width(IntrinsicSize.Max)
                        .verticalScroll(rememberScrollState())
                        .testTag("menu"),
                ) {
                    repeat(30) { BasicText("Action $it", Modifier.padding(16.dp)) }
                }
            }) { measurables, constraints ->
                val menu = measurables.single().measure(
                    constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity),
                )
                layout(menu.width, menu.height) { menu.place(0, 0) }
            }
        }

        composeTestRule.onNodeWithTag("menu").performScrollToNode(hasText("Action 29"))
        composeTestRule.onNodeWithText("Action 29").assertIsDisplayed()
    }
}
