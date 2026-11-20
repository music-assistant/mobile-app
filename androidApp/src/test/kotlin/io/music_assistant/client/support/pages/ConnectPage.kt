package io.music_assistant.client.support.pages

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.music_assistant.client.support.get
import io.music_assistant.client.ui.compose.ScreenSemantics
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.app_name
import musicassistantclient.composeapp.generated.resources.settings_connect
import musicassistantclient.composeapp.generated.resources.settings_connect_saved
import musicassistantclient.composeapp.generated.resources.settings_connection_method

class ConnectPage(private val composeTestRule: ComposeTestRule, private val savedCredentials: Boolean = false) : Page {
    override fun assert() {
        composeTestRule.onNode(
            hasTestTag(ScreenSemantics.TITLE_TAG).and(hasText(Res.string.app_name.get())),
        ).assertIsDisplayed()
        composeTestRule.onNodeWithText(Res.string.settings_connection_method.get()).assertIsDisplayed()
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
        composeTestRule.onNodeWithText(message)
            .performScrollTo()
            .assertIsDisplayed()
        return this.assertOnPage()
    }

    private fun clickConnect() {
        if (savedCredentials) {
            composeTestRule.onNodeWithText(Res.string.settings_connect_saved.get())
                .performScrollTo()
                .assertIsDisplayed()
                .performClick()
        } else {
            composeTestRule.onNodeWithText(Res.string.settings_connect.get())
                .performScrollTo()
                .assertIsDisplayed()
                .performClick()
        }
    }
}
