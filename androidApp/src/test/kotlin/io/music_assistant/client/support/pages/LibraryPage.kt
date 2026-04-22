package io.music_assistant.client.support.pages

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.ComposeTestRule
import io.music_assistant.client.support.isTab

class LibraryPage(private val type: String, composeTestRule: ComposeTestRule) : ComposePage(composeTestRule) {
    override fun assert() {
        composeTestRule.onNode(isTab(type)).assertIsSelected()
    }
}
