package io.music_assistant.client.support.pages

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput

class SearchPage(composeTestRule: ComposeTestRule) : ComposePage(composeTestRule) {
    override fun assert() {
        composeTestRule.onNodeWithText("Start searching...").assertIsDisplayed()
        assertNavBar(items = listOf("Home", "Library", "Search", "Settings"), selected = "Search")
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
        return clickOnMedia(name, "Search")
    }
}
