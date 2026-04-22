package io.music_assistant.client.support.pages

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.performClick
import io.music_assistant.client.support.isTab

class LibraryPage(private val type: String, composeTestRule: ComposeTestRule) : ComposePage(composeTestRule) {
    override fun assert() {
        composeTestRule.onNode(isTab(type)).assertIsSelected()
        assertNavBar(items = listOf("Home", "Library", "Search", "Settings"), selected = "Library")
    }

    fun clickAlbums(): LibraryPage {
        composeTestRule.onNode(isTab("Albums")).performClick()
        return LibraryPage("Albums", composeTestRule).assertOnPage()
    }

    fun clickOnMedia(name: String): MedaItemPage {
        return clickOnMedia(name, "Library")
    }
}
