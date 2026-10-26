package io.music_assistant.client.services

import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.data.model.client.PlayerDataFixtures
import io.music_assistant.client.data.model.client.PlayerMedia
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WatchNextManagerTest {
    @Test
    fun `no current track means no row`() {
        assertNull(watchNextContentFor(null))
        assertNull(watchNextContentFor(data(currentMedia = null)))
    }

    @Test
    fun `no title means no row`() {
        assertNull(watchNextContentFor(data(currentMedia = media(title = null))))
    }

    @Test
    fun `builds content from the current track, keyed to this player's deep link`() {
        val player = data(
            playerId = "player-42",
            currentMedia = media(
                title = "Ants Marching",
                artist = "Dave Matthews Band",
                album = "Under the Table and Dreaming",
                imageUrl = "https://example.com/art.jpg",
            ),
        )
        val content = watchNextContentFor(player)
        assertEquals(
            WatchNextContent(
                title = "Ants Marching",
                subtitle = "Dave Matthews Band • Under the Table and Dreaming",
                posterArtUrl = "https://example.com/art.jpg",
                deepLinkUri = "musicassistant://app/players?player=player-42",
                durationMillis = 200_000,
                lastPlaybackPositionMillis = 100_000,
            ),
            content,
        )
    }

    @Test
    fun `missing artist and album means no subtitle`() {
        val content = watchNextContentFor(data(currentMedia = media(title = "Track", artist = null, album = null)))
        assertNull(content?.subtitle)
    }

    @Test
    fun `missing artwork means no poster art`() {
        val content = watchNextContentFor(data(currentMedia = media(title = "Track", imageUrl = null)))
        assertNull(content?.posterArtUrl)
    }

    @Test
    fun `missing duration means no duration millis`() {
        val content = watchNextContentFor(data(currentMedia = media(title = "Track", duration = null)))
        assertNull(content?.durationMillis)
    }

    private fun media(
        title: String? = "Track",
        artist: String? = "Artist",
        album: String? = "Album",
        imageUrl: String? = "https://example.com/art.jpg",
        duration: Double? = 200.0,
    ) = PlayerMedia(
        title = title,
        artist = artist,
        album = album,
        imageUrl = imageUrl,
        duration = duration,
        queueId = "queue-1",
        queueItemId = "item-1",
        mediaType = null,
        uri = null,
    )

    private fun data(
        playerId: String = "player-1",
        currentMedia: PlayerMedia?,
    ): PlayerData {
        val base = PlayerDataFixtures.playerData()
        return base.copy(
            player = base.player.copy(id = playerId, currentMedia = currentMedia),
        )
    }
}
