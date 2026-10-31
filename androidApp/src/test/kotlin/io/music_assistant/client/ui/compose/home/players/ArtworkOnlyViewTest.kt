package io.music_assistant.client.ui.compose.home.players

import androidx.activity.ComponentActivity
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.music_assistant.client.data.model.client.MediaType
import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.data.model.client.PlayerDataFixtures
import io.music_assistant.client.data.model.client.PlayerMedia
import io.music_assistant.client.support.get
import io.music_assistant.client.ui.compose.common.PlayerColors
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.action_show_artwork_only
import musicassistantclient.composeapp.generated.resources.action_show_player_controls
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The cover of the full player shown alone: how it opens and how it closes. */
@RunWith(AndroidJUnit4::class)
class ArtworkOnlyViewTest {
    // No animations, as the system setting can ask: the backdrop of a playing player then holds
    // still, and the test clock can go idle.
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>(effectContext = NoMotion)

    private var requests = 0
    private var dismissals = 0

    @Test
    fun `a tap on the cover of the full player asks for the cover alone`() {
        showFullPlayer(player(imageUrl = IMAGE_URL))

        composeTestRule.onNode(hasClickLabel(Res.string.action_show_artwork_only.get()))
            .performSemanticsAction(SemanticsActions.OnClick)

        assertEquals(1, requests)
    }

    @Test
    fun `a cover without artwork cannot be shown alone`() {
        showFullPlayer(player(imageUrl = null))

        composeTestRule.onNode(hasClickLabel(Res.string.action_show_artwork_only.get()))
            .assertDoesNotExist()
    }

    @Test
    fun `a tap anywhere leaves the cover`() {
        showArtworkOnly(player(imageUrl = IMAGE_URL))

        composeTestRule.onNode(hasClickLabel(Res.string.action_show_player_controls.get()))
            .performClick()

        assertEquals(1, dismissals)
    }

    @Test
    fun `back leaves the cover`() {
        showArtworkOnly(player(imageUrl = IMAGE_URL))

        composeTestRule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }

        assertEquals(1, dismissals)
    }

    private fun hasClickLabel(label: String) = SemanticsMatcher("click label is \"$label\"") {
        it.config.getOrNull(SemanticsActions.OnClick)?.label == label
    }

    private fun player(imageUrl: String?): PlayerData {
        val data = PlayerDataFixtures.playerData()
        return data.copy(
            player = data.player.copy(
                isPlaying = true,
                currentMedia = PlayerMedia(
                    title = TITLE,
                    artist = "Jungle",
                    album = "FIP Groove",
                    imageUrl = imageUrl,
                    duration = null,
                    queueId = null,
                    queueItemId = null,
                    mediaType = MediaType.RADIO,
                    uri = null,
                ),
            ),
        )
    }

    private fun showFullPlayer(player: PlayerData) {
        composeTestRule.setContent {
            FullPlayerItem(
                modifier = Modifier,
                item = player,
                colors = PlayerColors(Color.Gray, Color.White),
                playerAction = { _, _ -> },
                onFavoriteChange = null,
                livePositionFlow = null,
                onArtworkClick = { requests++ },
            )
        }
    }

    private fun showArtworkOnly(player: PlayerData) {
        composeTestRule.setContent {
            ArtworkOnlyView(player = player, onDismiss = { dismissals++ })
        }
    }

    private object NoMotion : MotionDurationScale {
        override val scaleFactor = 0f
    }

    private companion object {
        const val TITLE = "Molly"
        const val IMAGE_URL = "https://artwork.test/molly.jpg"
    }
}
