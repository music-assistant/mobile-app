package io.music_assistant.client.support.pages

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.music_assistant.client.data.model.server.MediaType
import io.music_assistant.client.data.model.server.ServerMediaItem
import io.music_assistant.client.support.isTab

fun ComposePage.clickOnMedia(
    serverMediaItem: ServerMediaItem,
    navigationItem: String,
): MediaItemPage {
    return clickOnMedia(serverMediaItem.name, serverMediaItem.mediaType, navigationItem)
}

fun ComposePage.clickOnMedia(name: String, type: MediaType, navigationItem: String): MediaItemPage {
    composeTestRule.onNodeWithText(name)
        .assertIsDisplayed()
        .performClick()

    return MediaItemPage(name, type, navigationItem, composeTestRule).assertOnPage()
}

fun ComposePage.assertNavBar(items: List<String>, selected: String) {
    items.forEach {
        if (it == selected) {
            composeTestRule.onNode(isTab(it)).assertIsSelected()
        } else {
            composeTestRule.onNode(isTab(it)).assertIsNotSelected()
        }
    }
}

fun ComposePage.clickNavBarItem(item: String) {
    composeTestRule.onNode(isTab(item)).assertIsDisplayed().performClick()
}

fun ComposePage.clickSearch(): SearchPage {
    clickNavBarItem("Search")
    return SearchPage(composeTestRule).assertOnPage()
}

fun <T : Page> ComposePage.clickSearch(destination: T): T {
    clickNavBarItem("Search")
    return destination.assertOnPage()
}

fun <T : Page> ComposePage.clickHome(destination: T): T {
    clickNavBarItem("Home")
    return destination.assertOnPage()
}

fun ComposePage.clickLibrary(): LibraryPage {
    clickNavBarItem("Library")
    return LibraryPage("Artists", composeTestRule).assertOnPage()
}

fun <T : Page> ComposePage.clickLibrary(destination: T): T {
    clickNavBarItem("Library")
    return destination.assertOnPage()
}

fun <T : ComposePage> T.assertMediaDisplayed(name: String): T {
    composeTestRule.onNodeWithText(name).assertIsDisplayed()
    return this
}

fun <T : ComposePage> T.assertCurrentPlayer(
    name: String,
    playing: Boolean = false,
    item: String? = null,
): T {
    composeTestRule.waitUntil {
        composeTestRule.onNodeWithContentDescription("Current player: $name").isDisplayed()
    }

    composeTestRule.waitUntil {
        if (playing) {
            composeTestRule.onNodeWithContentDescription("Pause").isDisplayed()
        } else {
            composeTestRule.onNodeWithContentDescription("Play").isDisplayed()
        }
    }

    if (item != null) {
        composeTestRule.onNodeWithContentDescription("Playing $item").assertIsDisplayed()
    }

    return this
}
