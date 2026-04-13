package io.music_assistant.client.support.pages

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import io.music_assistant.client.support.assertNavBar

class SearchPage(private val composeTestRule: ComposeTestRule) : Page {
    override fun assert() {
        composeTestRule.onNodeWithText("Start searching...").assertIsDisplayed()
        composeTestRule.assertNavBar(
            items = listOf("Home", "Settings"),
            selected = "Home"
        )
    }

    fun search(query: String): SearchPage {
        composeTestRule.onNodeWithText("Type at least 3 characters to search")
            .assertIsDisplayed()
            .performTextInput(query)

        return this
    }

    fun assertResult(result: String): SearchPage {
        composeTestRule.onNodeWithText(result).assertIsDisplayed()
        return this
    }

    fun clickOnMedia(name: String): MedaItemPage {
        composeTestRule.onNodeWithText(name)
            .assertIsDisplayed()
            .performClick()

        return MedaItemPage(name, composeTestRule).assertOnPage()
    }
}
