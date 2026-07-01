package io.music_assistant.client.support.pages

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.music_assistant.client.support.get
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.settings_connect
import musicassistantclient.composeapp.generated.resources.settings_connect_saved

class ConnectPage(private val composeTestRule: ComposeTestRule, private val savedCredentials: Boolean = false) : Page {
    override fun assert() {
        composeTestRule.onNodeWithText("Connection Method").assertIsDisplayed()
    }

    fun connect(): AuthenticatePage {
        clickConnect()
        return AuthenticatePage(composeTestRule).assertOnPage()
    }

    fun <T : Page> connect(destination: T): T {
        clickConnect()
        return destination.assertOnPage()
    }

    fun connectWithError(message: String): ConnectPage {
        clickConnect()
        composeTestRule.onNodeWithText(message).assertIsDisplayed()
        return this.assertOnPage()
    }

    private fun clickConnect() {
        if (savedCredentials) {
            composeTestRule.onNodeWithText(Res.string.settings_connect_saved.get())
                .assertIsDisplayed()
                .performClick()
        } else {
            composeTestRule.onNodeWithText(Res.string.settings_connect.get())
                .assertIsDisplayed()
                .performClick()
        }
    }
}
