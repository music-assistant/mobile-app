package io.music_assistant.client.support.pages

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.music_assistant.client.support.get
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.auth_logout
import musicassistantclient.composeapp.generated.resources.nav_settings
import musicassistantclient.composeapp.generated.resources.settings_disconnect

class SettingsPage(composeTestRule: ComposeTestRule) : ComposePage(composeTestRule) {
    override fun assert() {
        composeTestRule.onNodeWithText(Res.string.nav_settings.get()).assertIsDisplayed()
        composeTestRule.onNodeWithText(Res.string.settings_disconnect.get()).assertIsDisplayed()
    }

    fun disconnect(): ConnectPage {
        composeTestRule.onNodeWithText(Res.string.settings_disconnect.get()).performClick()
        return ConnectPage(composeTestRule, savedCredentials = true).assertOnPage()
    }

    fun logout(): AuthenticatePage {
        composeTestRule.onNodeWithText(Res.string.auth_logout.get()).performClick()
        return AuthenticatePage(composeTestRule).assertOnPage()
    }
}
