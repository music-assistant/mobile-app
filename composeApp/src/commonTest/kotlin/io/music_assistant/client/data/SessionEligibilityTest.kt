package io.music_assistant.client.data

import io.music_assistant.client.data.MainDataSource.Companion.isSessionEligible
import io.music_assistant.client.data.model.client.PlayerDataFixtures
import io.music_assistant.client.data.model.client.QueueTrack
import io.music_assistant.client.data.model.client.testTrack
import io.music_assistant.client.ui.compose.common.DataState
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for [MainDataSource.isSessionEligible].
 *
 * A player the server cannot reach stays listed in the app, so the app can show that the
 * speaker exists and offer to wake it (issue #944). The media session is the one place that
 * must not follow: it would draw a notification whose transport buttons reach nothing.
 */
class SessionEligibilityTest {
    private fun playing(available: Boolean) = PlayerDataFixtures.playerData().let { data ->
        data.copy(
            player = data.player.copy(isAvailable = available),
            queue = DataState.Data(
                (data.queue as DataState.Data).data.let { queue ->
                    queue.copy(
                        info = queue.info.copy(
                            currentItem = QueueTrack(
                                id = "queue-item-1",
                                track = testTrack(),
                                isPlayable = true,
                                format = null,
                                dsp = null,
                                provider = "test",
                            ),
                        ),
                    )
                },
            ),
        )
    }

    @Test
    fun `a reachable player with a current item is eligible`() {
        assertTrue(isSessionEligible(playing(available = true)))
    }

    @Test
    fun `an unreachable player is not eligible even with a current item`() {
        assertFalse(isSessionEligible(playing(available = false)))
    }

    @Test
    fun `a reachable player without a current item is not eligible`() {
        assertFalse(isSessionEligible(PlayerDataFixtures.playerData()))
    }
}
