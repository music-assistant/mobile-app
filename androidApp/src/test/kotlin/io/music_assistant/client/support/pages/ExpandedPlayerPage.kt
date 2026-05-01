package io.music_assistant.client.support.pages

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.music_assistant.client.data.model.server.MediaType
import io.music_assistant.client.support.get
import io.music_assistant.client.ui.compose.home.FloatingBarSemantics
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.action_go_to_album
import musicassistantclient.composeapp.generated.resources.action_go_to_artist
import musicassistantclient.composeapp.generated.resources.cd_more

class ExpandedPlayerPage(
    val name: String,
    val boolean: Boolean,
    val item: String?,
    composeTestRule: ComposeTestRule,
) : ComposePage(composeTestRule) {
    override fun assert() {
        composeTestRule.onNodeWithTag(FloatingBarSemantics.TAG).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Collapse").assertIsDisplayed()
        assertPlayer(
            name = name,
            playing = boolean,
            item = item,
        )
    }

    fun goToArtist(artist: String, navigationItem: String): MediaItemPage {
        composeTestRule.onNodeWithContentDescription(Res.string.cd_more.get()).performClick()
        composeTestRule.onNodeWithText(Res.string.action_go_to_artist.get()).performClick()
        return MediaItemPage(
            artist,
            MediaType.ARTIST,
            navigationItem,
            composeTestRule,
        ).assertOnPage()
    }

    fun goToAlbum(album: String, navigationItem: String): MediaItemPage {
        composeTestRule.onNodeWithContentDescription(Res.string.cd_more.get()).performClick()
        composeTestRule.onNodeWithText(Res.string.action_go_to_album.get()).performClick()
        return MediaItemPage(
            album,
            MediaType.ALBUM,
            navigationItem,
            composeTestRule,
        ).assertOnPage()
    }
}
