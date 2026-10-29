package io.music_assistant.client.support.pages

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTextExactly
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import io.music_assistant.client.data.model.client.MediaType
import io.music_assistant.client.data.model.server.ServerMediaItem
import io.music_assistant.client.support.get
import io.music_assistant.client.support.isTab
import io.music_assistant.client.support.onNodeInContent
import io.music_assistant.client.ui.compose.item.ItemDetailsScreenSemantics
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.action_go_to_artist
import musicassistantclient.composeapp.generated.resources.action_play_now
import musicassistantclient.composeapp.generated.resources.cd_find_in_list
import musicassistantclient.composeapp.generated.resources.cd_more
import musicassistantclient.composeapp.generated.resources.cd_provider_filter
import musicassistantclient.composeapp.generated.resources.cd_toggle_hide_played_episodes
import musicassistantclient.composeapp.generated.resources.cd_view_all
import musicassistantclient.composeapp.generated.resources.find_in_list_label
import musicassistantclient.composeapp.generated.resources.item_tab_versions
import musicassistantclient.composeapp.generated.resources.media_type_albums
import musicassistantclient.composeapp.generated.resources.media_type_episodes
import musicassistantclient.composeapp.generated.resources.media_type_tracks
import musicassistantclient.composeapp.generated.resources.nav_discover
import musicassistantclient.composeapp.generated.resources.nav_search

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
        composeTestRule.onNode(hasTextExactly(name)).assertIsDisplayed()
        composeTestRule.onNodeWithText(Res.string.action_play_now.get()).assertIsDisplayed()
            .assertHasClickAction()
        assertNavBar(
            items = listOf(
                Res.string.nav_discover.get(),
                Res.string.nav_search.get(),
            ),
            selected = navigationItem,
        )

        when (type) {
            MediaType.ARTIST -> Unit
            MediaType.ALBUM -> {
                waitForTab(Res.string.media_type_tracks.get())
                composeTestRule.onNodeInContent(isTab(Res.string.media_type_albums.get()))
                    .assertIsNotDisplayed()
            }

            MediaType.TRACK -> {
                waitForTab(Res.string.media_type_albums.get())
                composeTestRule.onNodeInContent(isTab(Res.string.item_tab_versions.get()))
                    .assertIsDisplayed()
            }

            MediaType.PLAYLIST -> waitForTab(Res.string.media_type_tracks.get())

            MediaType.RADIO -> TODO()
            MediaType.AUDIOBOOK -> TODO()
            MediaType.PODCAST -> waitForTab(Res.string.media_type_episodes.get())
            MediaType.PODCAST_EPISODE -> TODO()
            MediaType.GENRE -> Unit
            MediaType.FOLDER -> TODO()
            MediaType.COLLECTION -> TODO()
            MediaType.FLOW_STREAM -> TODO()
            MediaType.ANNOUNCEMENT -> TODO()
            MediaType.SOUND_EFFECT -> TODO()
            MediaType.AUDIO_SOURCE -> TODO()
            MediaType.UNKNOWN -> TODO()
        }
    }

    /**
     * The tabs bar stays hidden while the sub-lists load. That load runs on Dispatchers.Default,
     * which Compose idling does not track, so the bar can appear after the rule reports idle.
     */
    private fun waitForTab(label: String) {
        composeTestRule.waitUntil("tab '$label' is displayed") {
            composeTestRule.onNodeInContent(isTab(label)).isDisplayed()
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

    fun toggleHidePlayedEpisodes(): ItemPage {
        composeTestRule.onNodeWithContentDescription(Res.string.cd_toggle_hide_played_episodes.get())
            .performClick()
        return this
    }

    fun clickPlay(): ItemPage {
        composeTestRule.onNodeWithText(Res.string.action_play_now.get()).performClick()
        return this
    }

    fun switchProvider(row: String, current: String, new: String): ItemPage {
        composeTestRule
            .onNodeWithContentDescription(Res.string.cd_provider_filter.get(row, current))
            .performClick()
        composeTestRule.onNode(hasAnyAncestor(isPopup()).and(hasText(new))).performClick()
        return this
    }

    fun assertProviderNotOffered(row: String, current: String, provider: String): ItemPage {
        composeTestRule
            .onNodeWithContentDescription(Res.string.cd_provider_filter.get(row, current))
            .performClick()
        composeTestRule.onNode(hasAnyAncestor(isPopup()).and(hasText(current))).assertExists()
        composeTestRule.onNode(hasAnyAncestor(isPopup()).and(hasText(provider)))
            .assertDoesNotExist()
        return this
    }

    fun assertMediaDisplayed(item: ServerMediaItem, provider: String? = null): ItemPage {
        return assertMediaDisplayed(
            item,
            inScrollable = ItemDetailsScreenSemantics.LIST_TAG,
            provider = provider,
        )
    }

    fun clickViewAll(row: String): ItemListPage {
        composeTestRule
            .onNodeWithContentDescription(Res.string.cd_view_all.get(row))
            .performClick()
        return ItemListPage(row, navigationItem, composeTestRule).assertOnPage()
    }

    fun assertRowShown(row: String, shown: Boolean): ItemPage {
        val title = composeTestRule.onNodeWithText(row)
        if (shown) {
            title.assertIsDisplayed()
        } else {
            title.assertIsNotDisplayed()
        }

        return this
    }

    fun openSearchInList(): ItemPage {
        composeTestRule
            .onNodeWithContentDescription(Res.string.cd_find_in_list.get())
            .performClick()

        return this
    }

    fun searchInList(query: String): ItemPage {
        composeTestRule
            .onNodeWithText(Res.string.find_in_list_label.get())
            .performTextInput(query)

        return this
    }
}
