package io.music_assistant.client.support.pages

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.music_assistant.client.data.model.client.MediaType
import io.music_assistant.client.data.model.server.ServerMediaItem
import io.music_assistant.client.support.get
import io.music_assistant.client.support.isTab
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.action_go_to_artist
import musicassistantclient.composeapp.generated.resources.action_play_now
import musicassistantclient.composeapp.generated.resources.cd_more
import musicassistantclient.composeapp.generated.resources.media_type_albums
import musicassistantclient.composeapp.generated.resources.media_type_tracks
import musicassistantclient.composeapp.generated.resources.nav_home
import musicassistantclient.composeapp.generated.resources.nav_library
import musicassistantclient.composeapp.generated.resources.nav_search
import musicassistantclient.composeapp.generated.resources.nav_settings

class ItemPage(
    private val name: String,
    private val type: MediaType,
    private val navigationItem: String,
    composeTestRule: ComposeTestRule,
) : ComposePage(composeTestRule) {
    constructor(
        serverMediaItem: ServerMediaItem,
        navigationItem: String,
        composeTestRule: ComposeTestRule,
    ) : this(
        serverMediaItem.name,
        MediaType.fromServer(serverMediaItem.mediaType) ?: MediaType.UNKNOWN,
        navigationItem,
        composeTestRule,
    )

    override fun assert() {
        composeTestRule.onNodeWithText(name).assertIsDisplayed()
        composeTestRule.onNodeWithText(Res.string.action_play_now.get()).assertIsDisplayed()
            .assertHasClickAction()
        assertNavBar(
            items = listOf(
                Res.string.nav_home.get(),
                Res.string.nav_search.get(),
                Res.string.nav_library.get(),
                Res.string.nav_settings.get(),
            ),
            selected = navigationItem,
        )

        when (type) {
            MediaType.ARTIST -> {
                composeTestRule.onNode(isTab(Res.string.media_type_albums.get()))
                    .assertIsDisplayed()
                composeTestRule.onNode(isTab(Res.string.media_type_tracks.get()))
                    .assertIsDisplayed()
            }

            MediaType.ALBUM -> {
                composeTestRule.onNode(isTab(Res.string.media_type_tracks.get()))
                    .assertIsDisplayed()
                composeTestRule.onNode(isTab(Res.string.media_type_albums.get()))
                    .assertIsNotDisplayed()
            }

            MediaType.TRACK -> TODO()
            MediaType.PLAYLIST -> {
                composeTestRule.onNode(isTab(Res.string.media_type_tracks.get()))
                    .assertIsDisplayed()
            }
            MediaType.RADIO -> TODO()
            MediaType.AUDIOBOOK -> TODO()
            MediaType.PODCAST -> TODO()
            MediaType.PODCAST_EPISODE -> TODO()
            MediaType.GENRE -> TODO()
            MediaType.FOLDER -> TODO()
            MediaType.FLOW_STREAM -> TODO()
            MediaType.ANNOUNCEMENT -> TODO()
            MediaType.UNKNOWN -> TODO()
        }
    }

    fun clickGoToArtist(artist: String): ItemPage {
        composeTestRule.onNodeWithContentDescription(Res.string.cd_more.get()).performClick()
        composeTestRule.onNodeWithText(Res.string.action_go_to_artist.get()).performClick()
        return ItemPage(
            artist,
            MediaType.ARTIST,
            navigationItem,
            composeTestRule,
        ).assertOnPage()
    }

    fun clickPlay(): ItemPage {
        composeTestRule.onNodeWithText(Res.string.action_play_now.get()).performClick()
        return this
    }
}
