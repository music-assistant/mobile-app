package io.music_assistant.client.ui.compose

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.music_assistant.client.data.hasFavoritableStreamTrack
import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.support.get
import io.music_assistant.client.support.radioStreamPlayer
import io.music_assistant.client.ui.compose.common.PlayerColors
import io.music_assistant.client.ui.compose.home.players.FullPlayerItem
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.cd_favorite
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Exercises the actual heart's click handler with a URI-backed station. */
@RunWith(AndroidJUnit4::class)
class StreamFavoriteTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private var stationClicks = 0
    private var streamClicks = 0

    @Test
    fun `on air song outranks the URI backed station`() {
        val player = radioStreamPlayer("Artist - Song")
        assertTrue(player.hasFavoritableStreamTrack())
        show(player, supported = true)
        clickHeart()
        assertEquals(1, streamClicks)
        assertEquals(0, stationClicks)
    }

    @Test
    fun `station without a song title keeps its station gesture`() {
        val player = radioStreamPlayer("Station")
        show(player, supported = true)
        clickHeart()
        assertEquals(0, streamClicks)
        assertEquals(1, stationClicks)
    }

    @Test
    fun `unsupported server keeps its station gesture`() {
        show(radioStreamPlayer("Artist - Song"), supported = false)
        clickHeart()
        assertEquals(0, streamClicks)
        assertEquals(1, stationClicks)
    }

    private fun clickHeart() =
        composeTestRule.onNodeWithContentDescription(Res.string.cd_favorite.get()).performClick()

    private fun show(player: PlayerData, supported: Boolean) {
        composeTestRule.setContent {
            FullPlayerItem(
                modifier = Modifier,
                item = player,
                colors = PlayerColors(Color.Gray, Color.White),
                playerAction = { _, _ -> },
                onFavoriteClick = { stationClicks++ },
                onFavoriteStreamClick = { streamClicks++ },
                canFavoriteStream = supported && player.hasFavoritableStreamTrack(),
                livePositionFlow = null,
            )
        }
    }
}
