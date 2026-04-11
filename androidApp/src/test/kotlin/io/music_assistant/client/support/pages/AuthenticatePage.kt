package io.music_assistant.client.support.pages

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithText

class AuthenticatePage(private val composeTestRule: ComposeTestRule) : Page() {
    override fun assert() {
        composeTestRule.onNodeWithText("Server").assertIsDisplayed()
        composeTestRule.onNodeWithText("Connected to homeassistant.local:8095").assertIsDisplayed()
    }
}