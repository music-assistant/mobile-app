package io.music_assistant.client.ui.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.height
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.music_assistant.client.data.model.client.AppMediaItemFixtures
import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.data.model.client.PlayerDataFixtures
import io.music_assistant.client.data.model.client.PlayerDataFixtures.toQueueTrack
import io.music_assistant.client.data.model.client.Queue
import io.music_assistant.client.support.get
import io.music_assistant.client.ui.compose.common.DataState
import io.music_assistant.client.ui.compose.home.players.PlayerBadgesRow
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.cd_autoplay_off
import musicassistantclient.composeapp.generated.resources.cd_autoplay_on
import musicassistantclient.composeapp.generated.resources.cd_crossfade_off
import musicassistantclient.composeapp.generated.resources.cd_crossfade_on
import musicassistantclient.composeapp.generated.resources.cd_sleep_timer
import musicassistantclient.composeapp.generated.resources.cd_sleep_timer_off
import org.jetbrains.compose.resources.StringResource
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Pins the badge row's state machine. Both badges are permanent fixtures once supported —
 * on/off and enabled/disabled are states of a badge, not reasons to add or remove one — so
 * these assert on the state-carrying content descriptions and on click actions.
 */
@RunWith(AndroidJUnit4::class)
class PlayerBadgesRowTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val toggledWith = mutableListOf<Boolean>()
    private val crossfadeToggledWith = mutableListOf<Boolean>()

    private fun playerWith(
        autoPlayEnabled: Boolean?,
        isDynamicPlaylist: Boolean = false,
        emptyQueue: Boolean = false,
        sleepTimer: Boolean = false,
        crossfadeEnabled: Boolean? = null,
    ): PlayerData {
        val base = PlayerDataFixtures.playerData()
        val queue = (base.queue as DataState.Data<Queue>).data
        val info = queue.info.copy(
            autoPlayEnabled = autoPlayEnabled,
            crossfadeEnabled = crossfadeEnabled,
            isDynamicPlaylist = isDynamicPlaylist,
            currentItem = if (emptyQueue) null else AppMediaItemFixtures.track().toQueueTrack(),
        )
        return base.copy(
            player = base.player.copy(
                sleepTimerExpiresAt = if (sleepTimer) {
                    System.currentTimeMillis() / 1000.0 + 900
                } else {
                    null
                },
            ),
            queue = DataState.Data(queue.copy(info = info)),
        )
    }

    private fun show(player: PlayerData, sleepTimerSupported: Boolean = true) {
        composeTestRule.setContent {
            PlayerBadgesRow(
                player = player,
                tint = Color.Red,
                onSleepTimerClick = {}.takeIf { sleepTimerSupported },
                onToggleAutoplay = { toggledWith += it },
                onToggleCrossfade = { crossfadeToggledWith += it },
            )
        }
    }

    private fun node(resource: StringResource) =
        composeTestRule.onNodeWithContentDescription(resource.get())

    // --- autoplay ---------------------------------------------------------------

    @Test
    fun `autoplay badge is absent when the server has no autoplay support`() {
        show(playerWith(autoPlayEnabled = null))
        assertFailsWith<AssertionError> { node(Res.string.cd_autoplay_on).assertIsDisplayed() }
        assertFailsWith<AssertionError> { node(Res.string.cd_autoplay_off).assertIsDisplayed() }
    }

    @Test
    fun `autoplay off is shown and turns on when tapped`() {
        show(playerWith(autoPlayEnabled = false))
        node(Res.string.cd_autoplay_off).assertIsDisplayed().assertHasClickAction().performClick()
        // Carries the CURRENT state; the action sends its inverse.
        assertEquals(listOf(false), toggledWith)
    }

    @Test
    fun `autoplay on is shown and turns off when tapped`() {
        show(playerWith(autoPlayEnabled = true))
        node(Res.string.cd_autoplay_on).assertIsDisplayed().assertHasClickAction().performClick()
        assertEquals(listOf(true), toggledWith)
    }

    @Test
    fun `autoplay is on but disabled on a dynamic queue`() {
        show(playerWith(autoPlayEnabled = false, isDynamicPlaylist = true))
        // Dynamic sources force autoplay on regardless of the stored flag.
        node(Res.string.cd_autoplay_on).assertIsDisplayed().assertHasNoClickAction().performClick()
        assertEquals(emptyList(), toggledWith)
    }

    @Test
    fun `autoplay is disabled when there is nothing to play`() {
        show(playerWith(autoPlayEnabled = false, emptyQueue = true))
        node(Res.string.cd_autoplay_off).assertIsDisplayed().assertHasNoClickAction().performClick()
        assertEquals(emptyList(), toggledWith)
    }

    // --- crossfade --------------------------------------------------------------

    @Test
    fun `crossfade badge is absent when the server has no crossfade support`() {
        show(playerWith(autoPlayEnabled = true, crossfadeEnabled = null))
        assertFailsWith<AssertionError> { node(Res.string.cd_crossfade_on).assertIsDisplayed() }
        assertFailsWith<AssertionError> { node(Res.string.cd_crossfade_off).assertIsDisplayed() }
    }

    @Test
    fun `crossfade off is shown and turns on when tapped`() {
        show(playerWith(autoPlayEnabled = true, crossfadeEnabled = false))
        node(Res.string.cd_crossfade_off).assertIsDisplayed().assertHasClickAction().performClick()
        assertEquals(listOf(false), crossfadeToggledWith)
    }

    @Test
    fun `crossfade on is shown and turns off when tapped`() {
        show(playerWith(autoPlayEnabled = true, crossfadeEnabled = true))
        node(Res.string.cd_crossfade_on).assertIsDisplayed().assertHasClickAction().performClick()
        assertEquals(listOf(true), crossfadeToggledWith)
    }

    @Test
    fun `crossfade stays tappable on a dynamic queue`() {
        // Deliberately unlike autoplay: the server rejects shuffle and repeat on a dynamic
        // queue but accepts crossfade, so dimming this would forbid a call it allows.
        show(
            playerWith(
                autoPlayEnabled = true,
                isDynamicPlaylist = true,
                crossfadeEnabled = false,
            ),
        )
        node(Res.string.cd_crossfade_off).assertIsDisplayed().assertHasClickAction().performClick()
        assertEquals(listOf(false), crossfadeToggledWith)
    }

    @Test
    fun `crossfade is disabled when there is nothing to play`() {
        show(playerWith(autoPlayEnabled = true, emptyQueue = true, crossfadeEnabled = false))
        node(Res.string.cd_crossfade_off).assertIsDisplayed().assertHasNoClickAction().performClick()
        assertEquals(emptyList(), crossfadeToggledWith)
    }

    // --- sleep timer ------------------------------------------------------------

    @Test
    fun `sleep badge is absent when the server does not support timers`() {
        show(playerWith(autoPlayEnabled = true), sleepTimerSupported = false)
        assertFailsWith<AssertionError> { node(Res.string.cd_sleep_timer).assertIsDisplayed() }
        assertFailsWith<AssertionError> { node(Res.string.cd_sleep_timer_off).assertIsDisplayed() }
    }

    @Test
    fun `sleep badge shows idle and stays tappable with no timer running`() {
        show(playerWith(autoPlayEnabled = true))
        node(Res.string.cd_sleep_timer_off).assertIsDisplayed().assertHasClickAction()
    }

    @Test
    fun `sleep badge shows active while a timer runs`() {
        show(playerWith(autoPlayEnabled = true, sleepTimer = true))
        node(Res.string.cd_sleep_timer).assertIsDisplayed().assertHasClickAction()
    }

    // --- layout -----------------------------------------------------------------

    @Test
    fun `row height does not depend on badge state`() {
        // Both in one composition: the rule allows only a single setContent.
        composeTestRule.setContent {
            Column {
                PlayerBadgesRow(
                    player = playerWith(autoPlayEnabled = null),
                    tint = Color.Red,
                    onSleepTimerClick = null,
                    onToggleAutoplay = {},
                    onToggleCrossfade = {},
                    modifier = Modifier.testTag("empty"),
                )
                PlayerBadgesRow(
                    player = playerWith(
                        autoPlayEnabled = true,
                        sleepTimer = true,
                        crossfadeEnabled = true,
                    ),
                    tint = Color.Red,
                    onSleepTimerClick = {},
                    onToggleAutoplay = {},
                    onToggleCrossfade = {},
                    modifier = Modifier.testTag("populated"),
                )
            }
        }
        val empty = composeTestRule.onNodeWithTag("empty").getUnclippedBoundsInRoot().height
        val populated =
            composeTestRule.onNodeWithTag("populated").getUnclippedBoundsInRoot().height

        assertEquals(populated, empty, "badge row must not change height with its content")
    }
}
