package io.music_assistant.client.support.pages

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import io.music_assistant.client.support.get
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.auth_login
import musicassistantclient.composeapp.generated.resources.auth_token
import musicassistantclient.composeapp.generated.resources.auth_use_token

class AuthenticatePage(private val composeTestRule: ComposeTestRule) : Page {
    override fun assert() {
        composeTestRule.onNodeWithText("Server").assertIsDisplayed()
        composeTestRule.onNodeWithText("homeassistant.local:8095").assertIsDisplayed()
        composeTestRule.onNodeWithText("Authentication").assertIsDisplayed()
    }

    fun login(username: String, password: String): HomePage {
        fillLogin(username, password)
        return HomePage(composeTestRule).assertOnPage()
    }

    fun loginWithError(username: String, password: String, error: String): AuthenticatePage {
        fillLogin(username, password)

        composeTestRule.onNodeWithText(error).performScrollTo().assertIsDisplayed()
        return this.assertOnPage()
    }

    fun loginWithToken(token: String): HomePage {
        fillToken(token)
        return HomePage(composeTestRule).assertOnPage()
    }

    fun loginWithTokenError(token: String, error: String): AuthenticatePage {
        fillToken(token)

        composeTestRule.onNodeWithText(error).performScrollTo().assertIsDisplayed()
        return this.assertOnPage()
    }

    private fun fillLogin(username: String, password: String) {
        composeTestRule.onNodeWithText("Username").assertIsDisplayed().performTextInput(username)
        composeTestRule.onNodeWithText("Password").assertIsDisplayed().performTextInput(password)
        composeTestRule.onNodeWithText("Login").assertIsDisplayed().performClick()
    }

    private fun fillToken(token: String) {
        composeTestRule.onNodeWithText(Res.string.auth_use_token.get()).assertIsDisplayed().performClick()
        composeTestRule.onNodeWithText(Res.string.auth_token.get()).assertIsDisplayed().performTextInput(token)
        composeTestRule.onNodeWithText(Res.string.auth_login.get()).assertIsDisplayed().performClick()
    }
}
