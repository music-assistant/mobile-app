package io.music_assistant.client.support.pages

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick

class HomePage(composeTestRule: ComposeTestRule) : ComposePage(composeTestRule) {
    override fun assert() {
        composeTestRule.onNodeWithText("Home").assertIsDisplayed()
        assertNavBar(items = listOf("Home", "Search", "Settings"), selected = "Home")
    }

    fun clickOnMedia(name: String): MedaItemPage {
        return clickOnMedia(name, "Home")
    }

    fun clickOnAlbums(): LibraryPage {
        composeTestRule.onNodeWithText("Albums").performClick()
        return LibraryPage("Albums", composeTestRule).assertOnPage()
    }
}