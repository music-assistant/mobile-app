package io.music_assistant.client.data.model.client

import io.music_assistant.client.data.model.client.items.PlayableItem
import io.music_assistant.client.ui.compose.common.DataState
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Pins the web app's Stop rule: a live input without pause support and a radio stream end with Stop. */
class PlayerDataCanPauseContentTest {
    private fun playerData(item: PlayableItem? = null, mediaType: MediaType? = null): PlayerData {
        val base = PlayerDataFixtures.playerData()
        val queue = (base.queue as DataState.Data).data
        return base.copy(
            player = base.player.copy(currentMedia = mediaType?.let(::media)),
            queue = DataState.Data(
                queue.copy(
                    info = queue.info.copy(
                        currentItem = item?.let {
                            QueueTrack(id = "qi", track = it, isPlayable = true, format = null, provider = null)
                        },
                    ),
                ),
            ),
        )
    }

    private fun media(mediaType: MediaType) = PlayerMedia(
        title = "title", artist = null, album = null, imageUrl = null, duration = null,
        queueId = null, queueItemId = null, mediaType = mediaType, uri = null,
    )

    @Test
    fun aTrackCanPause() {
        assertTrue(playerData(testTrack(), MediaType.TRACK).canPauseContent)
    }

    @Test
    fun nothingPlayingCanPause() {
        assertTrue(playerData().canPauseContent)
    }

    @Test
    fun aRadioStreamCannotPause() {
        assertFalse(playerData(testRadio(), MediaType.RADIO).canPauseContent)
    }

    @Test
    fun aSourceFollowsItsOwnFlag() {
        assertFalse(playerData(testAudioSource(canPlayPause = false)).canPauseContent)
        assertTrue(playerData(testAudioSource(canPlayPause = true), MediaType.RADIO).canPauseContent)
    }
}
