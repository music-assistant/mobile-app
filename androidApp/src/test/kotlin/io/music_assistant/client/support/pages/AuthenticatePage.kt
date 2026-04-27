package io.music_assistant.client.support.pages

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput

class AuthenticatePage(private val composeTestRule: ComposeTestRule) : Page {
    override fun assert() {
        composeTestRule.onNodeWithText("Server").assertIsDisplayed()
        composeTestRule.onNodeWithText("Connected to homeassistant.local:8095").assertIsDisplayed()
        composeTestRule.onNodeWithText("Authentication").assertIsDisplayed()
    }

    fun login(username: String, password: String): HomePage {
        composeTestRule.onNodeWithText("Username").assertIsDisplayed().performTextInput(username)
        composeTestRule.onNodeWithText("Password").assertIsDisplayed().performTextInput(password)
        composeTestRule.onNodeWithText("Login").assertIsDisplayed().performClick()
        return HomePage(composeTestRule).assertOnPage()
    }
}
