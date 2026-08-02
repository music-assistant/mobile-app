package io.music_assistant.client.support.pages

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import io.music_assistant.client.data.model.client.MediaType
import io.music_assistant.client.data.model.server.ServerMediaItem
import io.music_assistant.client.support.get
import io.music_assistant.client.support.isTab
import io.music_assistant.client.support.withinTag
import io.music_assistant.client.ui.compose.home.FloatingBarSemantics
import io.music_assistant.client.ui.compose.support.inScrollable
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.action_pause
import musicassistantclient.composeapp.generated.resources.action_play
import musicassistantclient.composeapp.generated.resources.banner_no_network
import musicassistantclient.composeapp.generated.resources.banner_reconnecting
import musicassistantclient.composeapp.generated.resources.cd_album_item
import musicassistantclient.composeapp.generated.resources.cd_current_player
import musicassistantclient.composeapp.generated.resources.cd_filter
import musicassistantclient.composeapp.generated.resources.cd_playing
import musicassistantclient.composeapp.generated.resources.common_apply
import musicassistantclient.composeapp.generated.resources.nav_home
import musicassistantclient.composeapp.generated.resources.nav_library
import musicassistantclient.composeapp.generated.resources.nav_search
import musicassistantclient.composeapp.generated.resources.nav_settings
import musicassistantclient.composeapp.generated.resources.players_nothing

fun ComposePage.clickOnMedia(
    serverMediaItem: ServerMediaItem,
    navigationItem: String,
    withinTag: String? = null,
    provider: String? = null,
): ItemPage {
    composeTestRule.onNode(mediaItemMatcher(serverMediaItem, withinTag, provider))
        .assertIsDisplayed()
        .performClick()

    val type = MediaType.fromServer(serverMediaItem.mediaType) ?: MediaType.UNKNOWN
    return ItemPage(serverMediaItem.name, type, navigationItem, composeTestRule).assertOnPage()
}

fun <T : ComposePage> T.clickItemOption(serverMediaItem: ServerMediaItem, action: String): T {
    composeTestRule.onNode(mediaItemMatcher(serverMediaItem)).performTouchInput { longClick() }
    composeTestRule.onNodeWithText(action).performClick()
    return this
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
    return LibraryPage(composeTestRule).assertOnPage()
}

fun <T : Page> ComposePage.clickLibrary(destination: T): T {
    clickNavBarItem(Res.string.nav_library.get())
    return destination.assertOnPage()
}

fun ComposePage.clickSettings(): SettingsPage {
    clickNavBarItem(Res.string.nav_settings.get())
    return SettingsPage(composeTestRule).assertOnPage()
}

fun <T : ComposePage> T.assertMediaDisplayed(
    serverMediaItem: ServerMediaItem,
    withinTag: String? = null,
    inScrollable: String? = null,
    provider: String? = null,
): T {
    val matcher = mediaItemMatcher(serverMediaItem, withinTag, provider)
    if (inScrollable != null) {
        composeTestRule.inScrollable(inScrollable) { onNode(matcher).assertIsDisplayed() }
    } else {
        composeTestRule.onNode(matcher).assertIsDisplayed()
    }

    return this
}

fun <T : ComposePage> T.assertMediaNotDisplayed(
    serverMediaItem: ServerMediaItem,
    provider: String? = null,
): T {
    composeTestRule.onNode(mediaItemMatcher(serverMediaItem, provider = provider))
        .assertIsNotDisplayed()
    return this
}

fun <T : ComposePage> T.playMedia(item: ServerMediaItem, withinTag: String? = null): T {
    composeTestRule.onNode(mediaItemMatcher(item, withinTag)).performClick()
    return this
}

fun <T : ComposePage> T.pause(): T {
    composeTestRule.onNodeWithContentDescription(Res.string.action_pause.get()).performClick()
    return this
}

fun <T : ComposePage> T.assertPlayer(
    name: String,
    playing: Boolean = false,
    item: String? = null,
): T {
    composeTestRule.waitUntil {
        composeTestRule
            .onNodeWithContentDescription(Res.string.cd_current_player.get().format(name))
            .isDisplayed()
    }

    composeTestRule.waitUntil {
        if (playing) {
            composeTestRule.onNodeWithContentDescription(Res.string.action_pause.get())
                .isDisplayed()
        } else {
            composeTestRule.onNodeWithContentDescription(Res.string.action_play.get()).isDisplayed()
        }
    }

    // waitUntil — the track-name flow update can lag the play action.
    if (item != null) {
        composeTestRule.waitUntil {
            composeTestRule
                .onNodeWithContentDescription(Res.string.cd_playing.get().format(item))
                .isDisplayed()
        }
    } else {
        composeTestRule.waitUntil {
            composeTestRule
                .onNodeWithContentDescription(Res.string.players_nothing.get())
                .isDisplayed()
        }
    }

    return this
}

fun <T : ComposePage> T.expandPlayer(
    name: String,
    playing: Boolean,
    item: String?,
): ExpandedPlayerPage {
    composeTestRule.onNodeWithTag(FloatingBarSemantics.TAG).performClick()
    return ExpandedPlayerPage(name, playing, item, composeTestRule).assertOnPage()
}

fun <T : ComposePage> T.assertReconnectingBanner(showing: Boolean): T {
    if (showing) {
        composeTestRule.onNodeWithText(Res.string.banner_reconnecting.get(1)).assertIsDisplayed()
    } else {
        composeTestRule.onNodeWithText(Res.string.banner_reconnecting.get(1)).assertIsNotDisplayed()
    }

    return this
}

fun <T : ComposePage> T.assertNoNetworkBanner(showing: Boolean): T {
    if (showing) {
        composeTestRule.onNodeWithText(Res.string.banner_no_network.get()).assertIsDisplayed()
    } else {
        composeTestRule.onNodeWithText(Res.string.banner_no_network.get()).assertIsNotDisplayed()
    }

    return this
}

fun <T : ComposePage> T.enableFilter(action: (FilterSheetPage) -> Unit): T {
    composeTestRule.onNodeWithContentDescription(Res.string.cd_filter.get()).performClick()
    action(FilterSheetPage(composeTestRule).assertOnPage())
    composeTestRule.onNodeWithText(Res.string.common_apply.get()).performClick()

    composeTestRule.onNodeWithContentDescription(Res.string.cd_filter.get()).assertIsOn()

    return this
}

private fun mediaItemMatcher(
    item: ServerMediaItem,
    withinTag: String? = null,
    provider: String? = null,
): SemanticsMatcher {
    val matcher = if (MediaType.fromServer(item.mediaType) == MediaType.ALBUM) {
        if (provider != null) {
            hasContentDescription(Res.string.cd_album_item.get(item.name, provider))
        } else {
            hasContentDescription(Res.string.cd_album_item.get(item.name, item.provider))
        }
    } else {
        hasContentDescription(item.name)
    }

    return if (withinTag != null) {
        withinTag(withinTag).and(matcher)
    } else {
        matcher
    }
}
