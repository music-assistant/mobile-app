package io.music_assistant.client.support.pages

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.performClick
import io.music_assistant.client.data.model.server.ServerMediaItem
import io.music_assistant.client.support.isTab

class LibraryPage(private val type: String, composeTestRule: ComposeTestRule) :
    ComposePage(composeTestRule) {
    override fun assert() {
        composeTestRule.onNode(isTab(type)).assertIsSelected()
        assertNavBar(items = listOf("Home", "Library", "Search", "Settings"), selected = "Library")
    }

    fun clickAlbums(): LibraryPage {
        return clickTab("Albums")
    }

    fun clickArtists(): LibraryPage {
        return clickTab("Artists")
    }

    private fun clickTab(type: String): LibraryPage {
        composeTestRule.onNode(isTab(type)).performClick()
        return LibraryPage(type, composeTestRule).assertOnPage()
    }

    fun clickOnMedia(item: ServerMediaItem): MediaItemPage {
        return clickOnMedia(item, "Library")
    }
}
