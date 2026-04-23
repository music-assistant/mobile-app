package io.music_assistant.client.support.pages

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.music_assistant.client.data.model.server.MediaType
import io.music_assistant.client.support.isTab

class MediaItemPage(
    private val name: String,
    private val navigationItem: String,
    composeTestRule: ComposeTestRule,
    private val type: MediaType = MediaType.ALBUM
) : ComposePage(composeTestRule) {
    override fun assert() {
        composeTestRule.onNodeWithText(name).assertIsDisplayed()
        composeTestRule.onNodeWithText("Play").assertIsDisplayed().assertHasClickAction()
        assertNavBar(
            items = listOf("Home", "Search", "Library", "Settings"),
            selected = navigationItem
        )

        when (type) {
            MediaType.ARTIST -> {
                composeTestRule.onNode(isTab("Albums")).assertIsDisplayed()
                composeTestRule.onNode(isTab("Tracks")).assertIsDisplayed()
            }

            MediaType.ALBUM -> {
                composeTestRule.onNode(isTab("Tracks")).assertIsDisplayed()
                composeTestRule.onNode(isTab("Albums")).assertIsNotDisplayed()
            }

            MediaType.TRACK -> TODO()
            MediaType.PLAYLIST -> TODO()
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

    fun clickGoToArtist(artist: String): MediaItemPage {
        composeTestRule.onNodeWithContentDescription("More").performClick()
        composeTestRule.onNodeWithText("Go to artist").performClick()
        return MediaItemPage(
            artist,
            navigationItem,
            composeTestRule,
            MediaType.ARTIST
        ).assertOnPage()
    }
}
