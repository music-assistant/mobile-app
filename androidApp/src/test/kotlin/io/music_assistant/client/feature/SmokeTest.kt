package io.music_assistant.client.feature

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.music_assistant.client.support.pages.ConnectPage
import io.music_assistant.client.ui.compose.App
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SmokeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `can connect to server`() {
        composeTestRule.setContent {
            App()
        }

        ConnectPage(composeTestRule)
            .connect()
    }
}