package io.music_assistant.client.ui.compose.item

import io.music_assistant.client.data.model.client.AppMediaItemFixtures
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.PlayableItem
import io.music_assistant.client.ui.compose.common.DataState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The facts line sums the loaded tracks; only an album or a fixed playlist has a track list. */
class PlayableSummaryTest {
    private val tracks: List<PlayableItem> = listOf(
        AppMediaItemFixtures.track().copy(duration = 200.0),
        AppMediaItemFixtures.track().copy(duration = null),
        AppMediaItemFixtures.track().copy(duration = 220.5),
    )

    private fun state(item: AppMediaItem, playable: DataState<List<PlayableItem>>) =
        ItemDetailsViewModel.State(
            itemState = DataState.Data(item),
            albumsState = DataState.NoData(),
            playableItemsState = playable,
        )

    @Test
    fun albumSumsItsTracks() {
        assertEquals(
            PlayableSummary(3, 420.5),
            state(AppMediaItemFixtures.album(), DataState.Data(tracks)).playableSummary,
        )
    }

    @Test
    fun nothingWhileLoadingOrEmpty() {
        assertNull(state(AppMediaItemFixtures.album(), DataState.Loading()).playableSummary)
        assertNull(state(AppMediaItemFixtures.album(), DataState.Data(emptyList())).playableSummary)
    }

    @Test
    fun onlyAFixedPlaylistHasOne() {
        val playlist = AppMediaItemFixtures.playlist()

        assertEquals(PlayableSummary(3, 420.5), state(playlist, DataState.Data(tracks)).playableSummary)
        assertNull(state(playlist.copy(isDynamic = true), DataState.Data(tracks)).playableSummary)
        assertNull(state(AppMediaItemFixtures.podcast(), DataState.Data(tracks)).playableSummary)
    }
}
