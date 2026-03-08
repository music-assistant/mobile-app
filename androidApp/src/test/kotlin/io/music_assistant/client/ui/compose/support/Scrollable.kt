package io.music_assistant.client.ui.compose.support

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode

fun ComposeTestRule.inScrollable(scrollableTag: String, block: ScrollableScope.() -> Unit) {
    block(ScrollableScope(this, scrollableTag))
}

class ScrollableScope(private val composeTestRule: ComposeTestRule, private val scrollableTag: String) {
    fun onNode(matcher: SemanticsMatcher): SemanticsNodeInteraction {
        composeTestRule.onNodeWithTag(scrollableTag).performScrollToNode(matcher)
        return composeTestRule.onAllNodes(matcher).onFirst()
    }
}
