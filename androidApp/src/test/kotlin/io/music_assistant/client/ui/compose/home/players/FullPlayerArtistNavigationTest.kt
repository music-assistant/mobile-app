package io.music_assistant.client.ui.compose.home.players

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.music_assistant.client.data.model.client.AppMediaItemFixtures
import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.data.model.client.PlayerDataFixtures
import io.music_assistant.client.data.model.client.PlayerDataFixtures.toQueue
import io.music_assistant.client.data.model.client.PlayerDataFixtures.toQueueTrack
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.Artist
import io.music_assistant.client.support.get
import io.music_assistant.client.ui.compose.common.PlayerColors
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.cd_playing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FullPlayerArtistNavigationTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private var navigatedTo: AppMediaItem? = null
    private var chooserRequests = 0

    @Test
    fun `tapping the artist line of a single-artist track navigates to the artist`() {
        val artist = AppMediaItemFixtures.artist(name = "Solo")
        show(playerPlaying(listOf(artist)))

        clickTrackInfo()

        assertEquals(artist, navigatedTo)
        assertEquals(0, chooserRequests)
    }

    @Test
    fun `tapping the artist line of a multi-artist track asks the host for the chooser`() {
        val artists = listOf(
            AppMediaItemFixtures.artist(name = "First"),
            AppMediaItemFixtures.artist(name = "Second"),
        )
        show(playerPlaying(artists))

        clickTrackInfo()

        assertNull(navigatedTo)
        assertEquals(1, chooserRequests)
    }

    @Test
    fun `track info has no click action for a track without artists`() {
        show(playerPlaying(emptyList()))

        trackInfo().assert(hasClickAction().not())
    }

    // The track-info column clears its children's semantics, so the artist line's click is
    // reachable through the column's semantic action: the path a screen reader takes.
    private fun trackInfo() =
        composeTestRule.onNodeWithContentDescription(Res.string.cd_playing.get(TRACK_NAME))

    private fun clickTrackInfo() = trackInfo().performSemanticsAction(SemanticsActions.OnClick)

    private fun playerPlaying(artists: List<Artist>): PlayerData {
        val track = AppMediaItemFixtures.track(name = TRACK_NAME, artists = artists)
        return PlayerDataFixtures.playerData(listOf(track.toQueueTrack()).toQueue())
    }

    private fun show(player: PlayerData) {
        composeTestRule.setContent {
            FullPlayerItem(
                modifier = Modifier,
                item = player,
                colors = PlayerColors(Color.Gray, Color.White),
                playerAction = { _, _ -> },
                onFavoriteClick = {},
                livePositionFlow = null,
                navigateToItem = { navigatedTo = it },
                onChooseArtist = { chooserRequests++ },
            )
        }
    }

    private companion object {
        const val TRACK_NAME = "Song"
    }
}
