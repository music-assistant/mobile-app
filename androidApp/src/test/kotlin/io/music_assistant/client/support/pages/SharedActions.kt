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
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import io.music_assistant.client.data.model.client.MediaType
import io.music_assistant.client.data.model.server.ServerMediaItem
import io.music_assistant.client.nav.NavigationBarSemantics
import io.music_assistant.client.nav.NavigationDrawerSemantics
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
import musicassistantclient.composeapp.generated.resources.common_back
import musicassistantclient.composeapp.generated.resources.library_empty
import musicassistantclient.composeapp.generated.resources.media_type_albums
import musicassistantclient.composeapp.generated.resources.media_type_artists
import musicassistantclient.composeapp.generated.resources.media_type_audiobooks
import musicassistantclient.composeapp.generated.resources.media_type_genres
import musicassistantclient.composeapp.generated.resources.media_type_playlists
import musicassistantclient.composeapp.generated.resources.media_type_podcasts
import musicassistantclient.composeapp.generated.resources.media_type_radio
import musicassistantclient.composeapp.generated.resources.media_type_tracks
import musicassistantclient.composeapp.generated.resources.nav_discover
import musicassistantclient.composeapp.generated.resources.nav_menu
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

/** Long-clicks [serverMediaItem], picks the [action] navigation entry, and lands on [target]. */
fun ComposePage.clickItemNavigationOption(
    serverMediaItem: ServerMediaItem,
    action: String,
    target: ServerMediaItem,
    navigationItem: String,
    withinTag: String? = null,
): ItemPage {
    composeTestRule.onNode(mediaItemMatcher(serverMediaItem, withinTag))
        .performTouchInput { longClick() }
    composeTestRule.onNodeWithText(action).performClick()

    val type = MediaType.fromServer(target.mediaType) ?: MediaType.UNKNOWN
    return ItemPage(target.name, type, navigationItem, composeTestRule).assertOnPage()
}

fun ComposePage.assertNavBar(items: List<String>, selected: String?) {
    items.forEach {
        if (it == selected) {
            composeTestRule
                .onNode(withinTag(NavigationBarSemantics.TAG).and(isTab(it))).assertIsSelected()
        } else {
            composeTestRule
                .onNode(withinTag(NavigationBarSemantics.TAG).and(isTab(it))).assertIsNotSelected()
        }
    }
}

fun ComposePage.clickNavBarItem(item: String) {
    composeTestRule
        .onNode(withinTag(NavigationBarSemantics.TAG).and(isTab(item)))
        .assertIsDisplayed()
        .performClick()
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
    clickNavBarItem(Res.string.nav_discover.get())
    return destination.assertOnPage()
}

fun ComposePage.clickAlbums(): LibraryListPage {
    return clickLibraryType(Res.string.media_type_albums.get())
}

fun ComposePage.clickArtists(): LibraryListPage {
    return clickLibraryType(Res.string.media_type_artists.get())
}

fun ComposePage.clickPlaylists(): LibraryListPage {
    return clickLibraryType(Res.string.media_type_playlists.get())
}

fun ComposePage.clickTracks(): LibraryListPage {
    return clickLibraryType(Res.string.media_type_tracks.get())
}

fun ComposePage.clickAudiobooks(): LibraryListPage {
    return clickLibraryType(Res.string.media_type_audiobooks.get())
}

fun ComposePage.clickPodcasts(): LibraryListPage {
    return clickLibraryType(Res.string.media_type_podcasts.get())
}

fun ComposePage.clickRadio(): LibraryListPage {
    return clickLibraryType(Res.string.media_type_radio.get())
}

fun ComposePage.clickGenres(): LibraryListPage {
    return clickLibraryType(Res.string.media_type_genres.get())
}

private fun ComposePage.clickLibraryType(type: String): LibraryListPage {
    openNavDrawer()
    clickNavDrawerItem(type)
    return LibraryListPage(type, composeTestRule).assertOnPage()
}

private fun <T : ComposePage> T.openNavDrawer(): T {
    clickNavBarItem(Res.string.nav_menu.get())
    return this
}

private fun ComposePage.clickNavDrawerItem(item: String) {
    composeTestRule.onNode(withinTag(NavigationDrawerSemantics.TAG).and(isTab(item))).performClick()
}

fun ComposePage.clickSettings(): SettingsPage {
    openNavDrawer()
    clickNavDrawerItem(Res.string.nav_settings.get())
    return SettingsPage(composeTestRule).assertOnPage()
}

fun <T : ComposePage> T.assertMediaDisplayed(
    serverMediaItem: ServerMediaItem,
    withinTag: String? = null,
    inScrollable: String? = null,
    provider: String? = null,
): T {
    val matcher = mediaItemMatcher(serverMediaItem, withinTag, provider)
    composeTestRule.waitUntil("media '${serverMediaItem.name}' is displayed") {
        if (inScrollable != null) {
            composeTestRule.inScrollable(inScrollable) { onNode(matcher) }
        }
        composeTestRule.onNode(matcher).isDisplayed()
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

fun <T : ComposePage> T.clickCurrentPlayer(name: String): PlayerSelectionPage<T> {
    composeTestRule.waitUntil {
        composeTestRule
            .onNodeWithContentDescription(Res.string.cd_current_player.get().format(name))
            .isDisplayed()
    }

    composeTestRule
        .onNodeWithContentDescription(Res.string.cd_current_player.get().format(name))
        .performClick()

    return PlayerSelectionPage(this, composeTestRule).assertOnPage()
}

fun <T : ComposePage> T.assertPlayer(
    name: String,
    playing: Boolean = false,
    item: String? = null,
): T {
    composeTestRule.waitUntil("current player is '$name'") {
        composeTestRule
            .onNodeWithContentDescription(Res.string.cd_current_player.get().format(name))
            .isDisplayed()
    }

    composeTestRule.waitUntil(if (playing) "Pause is displayed" else "Play is displayed") {
        if (playing) {
            composeTestRule.onNodeWithContentDescription(Res.string.action_pause.get())
                .isDisplayed()
        } else {
            composeTestRule.onNodeWithContentDescription(Res.string.action_play.get()).isDisplayed()
        }
    }

    // waitUntil — the track-name flow update can lag the play action.
    if (item != null) {
        composeTestRule.waitUntil("player plays '$item'") {
            composeTestRule
                .onNodeWithContentDescription(Res.string.cd_playing.get().format(item))
                .isDisplayed()
        }
    } else {
        composeTestRule.waitUntil("player shows nothing playing") {
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

fun <T : ComposePage> T.assertFloatingBar(showing: Boolean): T {
    composeTestRule.waitUntil("floating bar shown == $showing") {
        composeTestRule.onAllNodesWithTag(FloatingBarSemantics.TAG)
            .fetchSemanticsNodes().isNotEmpty() == showing
    }
    return this
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

fun <T : ComposePage> T.assertNoItems(): T {
    composeTestRule.onNodeWithText(Res.string.library_empty.get()).assertIsDisplayed()
    return this
}

fun <T : ComposePage, U : Page> T.clickBack(destination: U): U {
    composeTestRule.onNodeWithContentDescription(Res.string.common_back.get()).performClick()
    return destination.assertOnPage()
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
