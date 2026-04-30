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
import io.music_assistant.client.support.get
import io.music_assistant.client.support.isTab
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.action_pause
import musicassistantclient.composeapp.generated.resources.action_play
import musicassistantclient.composeapp.generated.resources.cd_current_player
import musicassistantclient.composeapp.generated.resources.cd_playing
import musicassistantclient.composeapp.generated.resources.media_type_artists
import musicassistantclient.composeapp.generated.resources.nav_home
import musicassistantclient.composeapp.generated.resources.nav_library
import musicassistantclient.composeapp.generated.resources.nav_search

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
    clickNavBarItem(Res.string.nav_search.get())
    return SearchPage(composeTestRule).assertOnPage()
}

fun <T : Page> ComposePage.clickSearch(destination: T): T {
    clickNavBarItem(Res.string.nav_search.get())
    return destination.assertOnPage()
}

fun <T : Page> ComposePage.clickHome(destination: T): T {
    clickNavBarItem(Res.string.nav_home.get())
    return destination.assertOnPage()
}

fun ComposePage.clickLibrary(): LibraryPage {
    clickNavBarItem(Res.string.nav_library.get())
    return LibraryPage(Res.string.media_type_artists.get(), composeTestRule).assertOnPage()
}

fun <T : Page> ComposePage.clickLibrary(destination: T): T {
    clickNavBarItem(Res.string.nav_library.get())
    return destination.assertOnPage()
}

fun <T : ComposePage> T.assertMediaDisplayed(name: String): T {
    composeTestRule.onNodeWithText(name).assertIsDisplayed()
    return this
}

fun <T : ComposePage> T.playMedia(item: ServerMediaItem): T {
    composeTestRule.onNodeWithText(item.name).performClick()
    return this
}

fun <T : ComposePage> T.pause(): T {
    composeTestRule.onNodeWithContentDescription(Res.string.action_pause.get()).performClick()
    return this
}

fun <T : ComposePage> T.assertCurrentPlayer(
    name: String,
    playing: Boolean = false,
    item: String? = null,
): T {
    composeTestRule.waitUntil {
        composeTestRule.onNodeWithContentDescription(Res.string.cd_current_player.get().format(name)).isDisplayed()
    }

    composeTestRule.waitUntil {
        if (playing) {
            composeTestRule.onNodeWithContentDescription(Res.string.action_pause.get()).isDisplayed()
        } else {
            composeTestRule.onNodeWithContentDescription(Res.string.action_play.get()).isDisplayed()
        }
    }

    if (item != null) {
        composeTestRule.onNodeWithContentDescription(Res.string.cd_playing.get().format(item)).assertIsDisplayed()
    }

    return this
}
