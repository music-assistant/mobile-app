package io.music_assistant.client.support.pages

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.music_assistant.client.data.model.client.MediaType
import io.music_assistant.client.support.get
import io.music_assistant.client.support.withinTag
import io.music_assistant.client.ui.compose.home.CollapsibleQueueSemantics
import io.music_assistant.client.ui.compose.home.FloatingBarSemantics
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.action_go_to_album
import musicassistantclient.composeapp.generated.resources.action_go_to_artist
import musicassistantclient.composeapp.generated.resources.cd_more
import musicassistantclient.composeapp.generated.resources.queue_clear
import musicassistantclient.composeapp.generated.resources.queue_label_with_position
import musicassistantclient.composeapp.generated.resources.queue_transfer

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

    fun goToArtist(artist: String, navigationItem: String): ItemPage {
        clickMore()
        composeTestRule.onNodeWithText(Res.string.action_go_to_artist.get()).performClick()
        return ItemPage(
            artist,
            MediaType.ARTIST,
            navigationItem,
            composeTestRule,
        ).assertOnPage()
    }

    fun goToAlbum(album: String, navigationItem: String): ItemPage {
        clickMore()
        composeTestRule.onNodeWithText(Res.string.action_go_to_album.get()).performClick()
        return ItemPage(
            album,
            MediaType.ALBUM,
            navigationItem,
            composeTestRule,
        ).assertOnPage()
    }

    fun clearQueue(): ExpandedPlayerPage {
        clickMore()
        composeTestRule.onNodeWithText(Res.string.queue_clear.get()).performClick()
        return ExpandedPlayerPage(name, false, null, composeTestRule).assertOnPage()
    }

    private fun clickMore() {
        composeTestRule
            .onNode(withinTag(FloatingBarSemantics.TAG).and(hasContentDescription(Res.string.cd_more.get())))
            .performClick()
    }

    fun transferQueue(player: String): ExpandedPlayerPage {
        clickMore()
        composeTestRule.onNodeWithText(Res.string.queue_transfer.get()).performClick()
        composeTestRule.onNodeWithText(player).performClick()
        return ExpandedPlayerPage(player, true, item, composeTestRule).assertOnPage()
    }

    fun openQueue(currentPosition: Int, size: Int): ExpandedPlayerPage {
        composeTestRule
            .onNodeWithText(Res.string.queue_label_with_position.get(currentPosition, size))
            .performClick()
        return this
    }

    fun assertQueue(vararg titles: String): ExpandedPlayerPage {
        titles.forEach {
            composeTestRule
                .onNode(withinTag(CollapsibleQueueSemantics.QUEUE_TAG).and(hasText(it)))
                .assertIsDisplayed()
        }

        return this
    }
}
