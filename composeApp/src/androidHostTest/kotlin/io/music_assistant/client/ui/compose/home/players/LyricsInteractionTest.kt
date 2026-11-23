package io.music_assistant.client.ui.compose.home.players

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.music_assistant.client.data.model.client.AppMediaItemFixtures
import io.music_assistant.client.data.model.client.Lyrics
import io.music_assistant.client.data.model.client.PlayerDataFixtures
import io.music_assistant.client.data.model.client.PlayerDataFixtures.toQueue
import io.music_assistant.client.data.model.client.PlayerDataFixtures.toQueueTrack
import io.music_assistant.client.ui.compose.common.DataState
import io.music_assistant.client.ui.compose.common.PlayerColors
import kotlinx.coroutines.runBlocking
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.cd_lyrics_close
import musicassistantclient.composeapp.generated.resources.lyrics_error
import musicassistantclient.composeapp.generated.resources.lyrics_fetching
import musicassistantclient.composeapp.generated.resources.lyrics_retry
import musicassistantclient.composeapp.generated.resources.lyrics_unavailable
import org.jetbrains.compose.resources.getString
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LyricsInteractionTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun artworkOpensLyricsWhenEnabled() {
        var requests = 0
        val track = AppMediaItemFixtures.track(name = "Song")
        val player = PlayerDataFixtures.playerData(listOf(track.toQueueTrack()).toQueue())
        rule.setContent {
            FullPlayerItem(
                modifier = Modifier.fillMaxSize(),
                item = player,
                colors = PlayerColors(Color.Gray, Color.White),
                playerAction = { _, _ -> },
                onFavoriteChange = null,
                livePositionFlow = null,
                onArtworkClick = { requests++ },
            )
        }

        rule.onNodeWithTag(FullPlayerSemantics.ARTWORK_TAG).assert(hasClickAction()).performClick()
        rule.runOnIdle { assertEquals(1, requests) }
    }

    @Test
    fun statusPanelKeepsLoadingEmptyAndErrorStatesDistinct() {
        var state by mutableStateOf<DataState<Lyrics>>(DataState.Loading())
        var retries = 0
        var closes = 0
        rule.setContent {
            LyricsStatePanel(
                state = state,
                livePositionFlow = null,
                onDismiss = { closes++ },
                kind = LyricsSheetKind.LYRICS,
                modifier = Modifier.fillMaxSize(),
                onRetry = { retries++ },
            )
        }

        rule.onNodeWithText(runBlocking { getString(Res.string.lyrics_fetching) }).assertIsDisplayed()
        rule.runOnIdle { state = DataState.NoData() }
        rule.onNodeWithText(runBlocking { getString(Res.string.lyrics_unavailable) }).assertIsDisplayed()
        rule.runOnIdle { state = DataState.Error() }
        rule.onNodeWithText(runBlocking { getString(Res.string.lyrics_error) }).assertIsDisplayed()
        rule.onNodeWithText(runBlocking { getString(Res.string.lyrics_retry) }).performClick()
        rule.onNodeWithContentDescription(runBlocking { getString(Res.string.cd_lyrics_close) }).performClick()
        rule.runOnIdle {
            assertEquals(1, retries)
            assertEquals(1, closes)
        }
    }
}
