package io.music_assistant.client.support.pages

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.music_assistant.client.data.model.server.ServerMediaItem

class HomePage(composeTestRule: ComposeTestRule) : ComposePage(composeTestRule) {
    override fun assert() {
        composeTestRule.onNodeWithText("Home").assertIsDisplayed()
        assertNavBar(items = listOf("Home", "Library", "Search", "Settings"), selected = "Home")
    }

    fun clickOnMedia(item: ServerMediaItem): MediaItemPage {
        return clickOnMedia(item, "Home")
    }

    fun refresh(): HomePage {
        composeTestRule.onNodeWithContentDescription("Refresh").performClick()
        return this
    }
}