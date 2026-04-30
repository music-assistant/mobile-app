package io.music_assistant.client.support.pages

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.performClick
import io.music_assistant.client.data.model.server.ServerMediaItem
import io.music_assistant.client.support.get
import io.music_assistant.client.support.isTab
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.media_type_albums
import musicassistantclient.composeapp.generated.resources.media_type_artists
import musicassistantclient.composeapp.generated.resources.nav_home
import musicassistantclient.composeapp.generated.resources.nav_library
import musicassistantclient.composeapp.generated.resources.nav_search
import musicassistantclient.composeapp.generated.resources.nav_settings

class LibraryPage(private val type: String, composeTestRule: ComposeTestRule) :
    ComposePage(composeTestRule) {
    override fun assert() {
        composeTestRule.onNode(isTab(type)).assertIsSelected()
        assertNavBar(
            items = listOf(
                Res.string.nav_home.get(),
                Res.string.nav_library.get(),
                Res.string.nav_search.get(),
                Res.string.nav_settings.get(),
            ),
            selected = Res.string.nav_library.get(),
        )
    }

    fun clickAlbums(): LibraryPage {
        return clickTab(Res.string.media_type_albums.get())
    }

    fun clickArtists(): LibraryPage {
        return clickTab(Res.string.media_type_artists.get())
    }

    private fun clickTab(type: String): LibraryPage {
        composeTestRule.onNode(isTab(type)).performClick()
        return LibraryPage(type, composeTestRule).assertOnPage()
    }

    fun clickOnMedia(item: ServerMediaItem): MediaItemPage {
        return clickOnMedia(item, Res.string.nav_library.get())
    }
}
