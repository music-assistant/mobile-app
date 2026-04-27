package io.music_assistant.client.support.pages

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick

class ConnectPage(private val composeTestRule: ComposeTestRule) : Page {
    override fun assert() {
        composeTestRule.onNodeWithText("Connection Method").assertIsDisplayed()
    }

    fun connect(): AuthenticatePage {
        composeTestRule.onNodeWithText("Connect")
            .assertIsDisplayed()
            .performClick()

        return AuthenticatePage(composeTestRule).assertOnPage()
    }
}
