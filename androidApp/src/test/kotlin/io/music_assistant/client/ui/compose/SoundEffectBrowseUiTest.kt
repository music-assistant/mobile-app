package io.music_assistant.client.ui.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.music_assistant.client.data.model.client.ClickContext
import io.music_assistant.client.data.model.client.QueueOption
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.Playlist
import io.music_assistant.client.data.model.client.items.SoundEffect
import io.music_assistant.client.data.model.server.ProviderMapping
import io.music_assistant.client.data.play.PlayMode
import io.music_assistant.client.settings.ViewMode
import io.music_assistant.client.ui.compose.common.items.LibraryActions
import io.music_assistant.client.ui.compose.common.items.PlaylistActions
import io.music_assistant.client.ui.compose.common.items.ProvideClickActions
import io.music_assistant.client.ui.compose.common.items.localizedSubtitle
import io.music_assistant.client.ui.compose.library.AdaptiveMediaGrid
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class SoundEffectBrowseUiTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `browse renders provider sounds and tapping dispatches play`() {
        val sounds = listOf(
            sound("ambient_sounds", "white_noise", "White noise"),
            sound("rain_mood", "rain", "Rain"),
        )
        var played: AppMediaItem? = null
        var mode: PlayMode? = null
        composeTestRule.setContent {
            Box {
                ProvideClickActions(ClickContext.BROWSE) {
                    AdaptiveMediaGrid(
                        modifier = Modifier.fillMaxSize(),
                        items = sounds,
                        hasMore = false,
                        viewMode = ViewMode.LIST,
                        onNavigateClick = { error("Sounds should play, not navigate") },
                        onPlayClick = { item, option ->
                            played = item
                            mode = option
                        },
                        playlistActions = object : PlaylistActions {
                            override suspend fun getEditablePlaylists(): List<Playlist> = emptyList()
                            override fun addToPlaylist(itemUri: String?, playlist: Playlist) = Unit
                            override suspend fun createPlaylist(name: String): Playlist? = null
                        },
                        libraryActions = object : LibraryActions {
                            override fun onLibraryClick(item: AppMediaItem) = Unit
                            override fun onFavoriteChange(item: AppMediaItem, favorite: Boolean?) = Unit
                        },
                        contentPadding = PaddingValues(),
                    )
                }
            }
        }
        composeTestRule.onNodeWithContentDescription("White noise").assertIsDisplayed().performClick()
        composeTestRule.runOnIdle {
            assertEquals(sounds[0], played)
            assertEquals(PlayMode(QueueOption.REPLACE), mode)
        }
        composeTestRule.onNodeWithContentDescription("Rain").assertIsDisplayed().performClick()
        composeTestRule.runOnIdle { assertEquals(sounds[1], played) }
    }

    @Test
    fun `only AI Radio clips have the AI host subtitle`() {
        val rain = sound("rain_mood", "rain", "Rain")
        val noise = sound("ambient_sounds", "white_noise", "White noise")
        val host = sound("ai_radio--instance", "clip", "Introduction")
        val mappedHost = host.copy(
            provider = "opaque-instance",
            providerMappings = listOf(ProviderMapping("clip", "ai_radio", "opaque-instance")),
        )
        val unrelated = host.copy(provider = "ai_radio_other")
        composeTestRule.setContent {
            BasicText(
                listOf(
                    rain.localizedSubtitle(),
                    noise.localizedSubtitle(),
                    host.localizedSubtitle(),
                    mappedHost.localizedSubtitle(),
                    unrelated.localizedSubtitle(),
                ).joinToString(" | "),
            )
        }
        composeTestRule.onNodeWithText(
            "Sound effect | Sound effect | AI host | AI host | Sound effect",
        ).assertIsDisplayed()
    }

    private fun sound(provider: String, id: String, name: String) = SoundEffect(
        itemId = id,
        provider = provider,
        name = name,
        providerMappings = null,
        metadata = null,
        images = emptyMap(),
        duration = null,
        uri = "$provider://sound_effect/$id",
    )
}
