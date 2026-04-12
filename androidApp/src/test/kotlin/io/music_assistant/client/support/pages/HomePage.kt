package io.music_assistant.client.support.pages

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.music_assistant.client.support.assertNavBar
import io.music_assistant.client.ui.compose.support.inScrollable

class HomePage(private val composeTestRule: ComposeTestRule) : Page {
    override fun assert() {
        composeTestRule.onNodeWithText("Home").assertIsDisplayed()
        composeTestRule.assertNavBar(
            items = listOf("Home", "Settings"),
            selected = "Home"
        )
    }

    fun assertMediaDisplayed(name: String) {
        composeTestRule.onNodeWithText(name).assertIsDisplayed()
    }

    fun clickSearch(): SearchPage {
        composeTestRule.inScrollable("LibraryRow") {
            onNode(hasContentDescription("Global search"))
                .assertIsDisplayed()
                .performClick()
        }

        return SearchPage(composeTestRule).assertOnPage()
    }
}