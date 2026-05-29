package io.music_assistant.client.support.pages

import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onParent
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import io.music_assistant.client.support.get
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.media_type_albums
import musicassistantclient.composeapp.generated.resources.media_type_artists
import musicassistantclient.composeapp.generated.resources.media_type_audiobooks
import musicassistantclient.composeapp.generated.resources.media_type_genres
import musicassistantclient.composeapp.generated.resources.media_type_playlists
import musicassistantclient.composeapp.generated.resources.media_type_podcasts
import musicassistantclient.composeapp.generated.resources.media_type_radio
import musicassistantclient.composeapp.generated.resources.media_type_tracks
import musicassistantclient.composeapp.generated.resources.nav_home
import musicassistantclient.composeapp.generated.resources.nav_library
import musicassistantclient.composeapp.generated.resources.nav_search
import musicassistantclient.composeapp.generated.resources.nav_settings

class LibraryPage(composeTestRule: ComposeTestRule) :
    ComposePage(composeTestRule) {
    override fun assert() {
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

    fun clickAlbums(): ItemListPage {
        return clickType(Res.string.media_type_albums.get())
    }

    fun clickArtists(): ItemListPage {
        return clickType(Res.string.media_type_artists.get())
    }

    fun clickPlaylists(): ItemListPage {
        return clickType(Res.string.media_type_playlists.get())
    }

    fun clickTracks(): ItemListPage {
        return clickType(Res.string.media_type_tracks.get())
    }

    fun clickAudiobooks(): ItemListPage {
        return clickType(Res.string.media_type_audiobooks.get())
    }

    fun clickPodcasts(): ItemListPage {
        return clickType(Res.string.media_type_podcasts.get())
    }

    fun clickRadio(): ItemListPage {
        return clickType(Res.string.media_type_radio.get())
    }

    fun clickGenres(): ItemListPage {
        val type = Res.string.media_type_genres.get()
        composeTestRule.onNodeWithText(type).onParent().performScrollToIndex(7)
        return clickType(type)
    }

    private fun clickType(type: String): ItemListPage {
        composeTestRule.onNodeWithText(type).performClick()
        return ItemListPage(type, composeTestRule).assertOnPage()
    }
}
