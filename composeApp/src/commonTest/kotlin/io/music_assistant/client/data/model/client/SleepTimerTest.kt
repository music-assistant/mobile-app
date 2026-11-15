package io.music_assistant.client.data.model.client

import io.music_assistant.client.data.model.client.AppMediaItemFixtures.audiobook
import io.music_assistant.client.data.model.client.AppMediaItemFixtures.track
import io.music_assistant.client.data.model.client.PlayerDataFixtures.toQueue
import io.music_assistant.client.data.model.client.items.PlayableItem
import io.music_assistant.client.data.model.server.SleepTimerMode
import io.music_assistant.client.ui.compose.common.DataState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Pins the end-of-content options to the conditions that `players/sleep_timer/set` accepts. */
class SleepTimerTest {
    private fun playing(
        item: PlayableItem,
        isPlaying: Boolean = true,
        ended: Boolean = false,
    ): PlayerData {
        val queue = listOf(QueueTrack(id = "qi1", track = item, isPlayable = true, format = null, provider = null))
            .toQueue()
        val data = PlayerDataFixtures.playerData(queue)
        return data.copy(
            player = data.player.copy(isPlaying = isPlaying),
            queue = DataState.Data(queue.copy(info = queue.info.copy(ended = ended))),
        )
    }

    @Test
    fun trackOffersEndOfItemOnly() {
        assertEquals(listOf(SleepTimerMode.END_OF_ITEM), playing(track()).sleepTimerEndModes())
    }

    @Test
    fun itemWithChaptersAlsoOffersEndOfChapter() {
        val book = audiobook(chapters = listOf("One", "Two")).copy(duration = 3600.0)

        assertEquals(
            listOf(SleepTimerMode.END_OF_ITEM, SleepTimerMode.END_OF_CHAPTER),
            playing(book).sleepTimerEndModes(),
        )
    }

    @Test
    fun notPlayingOrEndedQueueOrUnknownDurationOffersNothing() {
        assertEquals(emptyList(), playing(track(), isPlaying = false).sleepTimerEndModes())
        assertEquals(emptyList(), playing(track(), ended = true).sleepTimerEndModes())
        assertEquals(emptyList(), playing(track().copy(duration = null)).sleepTimerEndModes())
        assertEquals(emptyList(), PlayerDataFixtures.playerData().sleepTimerEndModes())
    }

    @Test
    fun durationTimerIsNoEndMode() {
        val player = PlayerDataFixtures.player()

        assertNull(player.copy(sleepTimerMode = SleepTimerMode.DURATION).sleepTimerEndMode)
        assertEquals(
            SleepTimerMode.END_OF_ITEM,
            player.copy(sleepTimerMode = SleepTimerMode.END_OF_ITEM).sleepTimerEndMode,
        )
    }
}
