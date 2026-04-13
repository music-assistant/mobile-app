package io.music_assistant.client.support.pages

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithText

class MedaItemPage(private val name: String, private val composeTestRule: ComposeTestRule) : Page {
    override fun assert() {
        composeTestRule.onNodeWithText(name).assertIsDisplayed()
        composeTestRule.onNodeWithText("Play").assertIsDisplayed().assertHasClickAction()
    }
}
