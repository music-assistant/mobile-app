package io.music_assistant.client.services

import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.music_assistant.client.data.model.client.AppMediaItemFixtures
import io.music_assistant.client.data.model.client.MediaType
import io.music_assistant.client.data.model.client.PlayerDataFixtures
import io.music_assistant.client.data.model.client.PlayerMedia
import io.music_assistant.client.data.model.client.QueueTrack
import io.music_assistant.client.data.model.client.items.PlayableItem
import io.music_assistant.client.data.model.client.items.RadioStation
import io.music_assistant.client.ui.compose.common.DataState
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class)
class MediaNotificationDataStationTest {
    @Test
    fun `radio station name is retained without replacing on-air song metadata`() {
        val data = MediaNotificationData.from(
            playerData(
                station = radioStation("Example Radio"),
                media = PlayerMedia(
                    title = "On Air Song",
                    artist = "On Air Artist",
                    album = "On Air Album",
                    imageUrl = null,
                    duration = null,
                    queueId = "queue",
                    queueItemId = "radio-item",
                    mediaType = MediaType.RADIO,
                    uri = null,
                ),
            ),
            multiplePlayers = false,
            canEditLibrary = false,
            effectiveElapsedSec = null,
        )

        assertEquals("Example Radio", data.stationName)
        assertEquals("On Air Song", data.name)
        assertEquals("On Air Artist", data.artist)
        assertEquals("On Air Album", data.album)
    }

    @Test
    fun `a radio song shows its length without a seek bar to drag`() {
        val data = MediaNotificationData.from(
            playerData(
                station = radioStation("Example Radio"),
                media = PlayerMedia(
                    title = "On Air Song",
                    artist = "On Air Artist",
                    album = null,
                    imageUrl = null,
                    duration = 430.0,
                    queueId = "queue",
                    queueItemId = "radio-item",
                    mediaType = MediaType.RADIO,
                    uri = null,
                ),
            ),
            multiplePlayers = false,
            canEditLibrary = false,
            effectiveElapsedSec = 104.0,
        )

        assertEquals(430_000L, data.duration)
        assertEquals(104_000L, data.elapsedTime)
        assertEquals(0L, sessionTransportActions(data) and PlaybackStateCompat.ACTION_SEEK_TO)
        assertEquals(
            PlaybackStateCompat.ACTION_SEEK_TO,
            sessionTransportActions(data.copy(isSeekable = true)) and PlaybackStateCompat.ACTION_SEEK_TO,
        )
    }

    @Test
    fun `station metadata clears when playback switches to a track`() {
        val data = MediaNotificationData.from(
            playerData(
                station = AppMediaItemFixtures.track(name = "A Track"),
                media = PlayerMedia(
                    title = "A Track",
                    artist = "An Artist",
                    album = "An Album",
                    imageUrl = null,
                    duration = 180.0,
                    queueId = "queue",
                    queueItemId = "track-item",
                    mediaType = MediaType.TRACK,
                    uri = null,
                ),
            ),
            multiplePlayers = false,
            canEditLibrary = false,
            effectiveElapsedSec = null,
        )

        assertNull(data.stationName)
        assertEquals("A Track", data.name)
        assertEquals("An Artist", data.artist)
        assertEquals("An Album", data.album)
        val description = buildMediaSessionMetadata(
            data = data,
            bitmap = null,
            unknownTrack = "Unknown Track",
            artist = "An Artist",
        ).description
        assertEquals("A Track", description.title.toString())
        assertEquals("An Artist", description.subtitle.toString())
        assertEquals("An Album", description.description.toString())
    }

    @Test
    fun `radio station title is available when no on-air title is supplied`() {
        val data = MediaNotificationData.from(
            playerData(
                station = radioStation("Example Radio"),
                media = PlayerMedia(
                    title = null,
                    artist = null,
                    album = null,
                    imageUrl = null,
                    duration = null,
                    queueId = "queue",
                    queueItemId = "radio-item",
                    mediaType = MediaType.RADIO,
                    uri = null,
                ),
            ),
            multiplePlayers = false,
            canEditLibrary = false,
            effectiveElapsedSec = null,
        )

        assertEquals("Example Radio", data.stationName)
        assertNull(data.name)
        assertNull(data.artist)
        assertNull(data.album)
    }

    @Test
    fun `blank radio station name is omitted`() {
        val data = MediaNotificationData.from(
            playerData(
                station = radioStation("  "),
                media = PlayerMedia(
                    title = "On Air Song",
                    artist = "On Air Artist",
                    album = "On Air Album",
                    imageUrl = null,
                    duration = null,
                    queueId = "queue",
                    queueItemId = "radio-item",
                    mediaType = MediaType.RADIO,
                    uri = null,
                ),
            ),
            multiplePlayers = false,
            canEditLibrary = false,
            effectiveElapsedSec = null,
        )

        assertNull(data.stationName)
    }

    @Test
    fun `radio metadata description exposes song artist and station through MediaDescription`() {
        val data = MediaNotificationData.from(
            playerData(
                station = radioStation("Example Radio"),
                media = PlayerMedia(
                    title = "On Air Song",
                    artist = "On Air Artist",
                    album = "On Air Album",
                    imageUrl = null,
                    duration = null,
                    queueId = "queue",
                    queueItemId = "radio-item",
                    mediaType = MediaType.RADIO,
                    uri = null,
                ),
            ),
            multiplePlayers = false,
            canEditLibrary = false,
            effectiveElapsedSec = null,
        )
        val metadata = buildMediaSessionMetadata(
            data = data,
            bitmap = null,
            unknownTrack = "Unknown Track",
            artist = "On Air Artist",
        )
        val description = metadata.description

        assertEquals("On Air Song", description.title.toString())
        assertEquals("On Air Artist", description.subtitle.toString())
        assertEquals("Example Radio", description.description.toString())
        assertEquals("On Air Song", metadata.getString(MediaMetadataCompat.METADATA_KEY_TITLE))
        assertEquals("On Air Artist", metadata.getString(MediaMetadataCompat.METADATA_KEY_ARTIST))
        assertEquals("On Air Album", metadata.getString(MediaMetadataCompat.METADATA_KEY_ALBUM))
    }

    @Test
    fun `radio without on-air title uses station once in MediaDescription`() {
        val data = MediaNotificationData.from(
            playerData(
                station = radioStation("Example Radio"),
                media = PlayerMedia(
                    title = null,
                    artist = null,
                    album = null,
                    imageUrl = null,
                    duration = null,
                    queueId = "queue",
                    queueItemId = "radio-item",
                    mediaType = MediaType.RADIO,
                    uri = null,
                ),
            ),
            multiplePlayers = false,
            canEditLibrary = false,
            effectiveElapsedSec = null,
        )
        val metadata = buildMediaSessionMetadata(
            data = data,
            bitmap = null,
            unknownTrack = "Unknown Track",
            artist = "Unknown Artist",
        )
        val description = metadata.description

        assertEquals("Example Radio", description.title.toString())
        assertNull(description.subtitle)
        assertNull(description.description)
        assertEquals("Example Radio", metadata.getString(MediaMetadataCompat.METADATA_KEY_TITLE))
        assertEquals("Unknown Artist", metadata.getString(MediaMetadataCompat.METADATA_KEY_ARTIST))
    }

    @Test
    fun `radio station is omitted from description when it matches the song title`() {
        val data = MediaNotificationData(
            multiplePlayers = false,
            longItemId = null,
            activeQueueItemId = null,
            name = "Example Radio",
            artist = "On Air Artist",
            album = null,
            repeatMode = null,
            shuffleEnabled = null,
            isLongFormContent = false,
            isFavoritableTrack = false,
            isFavoritableStream = false,
            isFavorite = false,
            isPlaying = true,
            imageUrl = null,
            chapterName = null,
            elapsedTime = null,
            elapsedUpdateTimeMs = null,
            playerName = null,
            duration = null,
            stationName = " example radio ",
        )

        val description = buildMediaSessionMetadata(
            data = data,
            bitmap = null,
            unknownTrack = "Unknown Track",
            artist = "On Air Artist",
        ).description

        assertEquals("Example Radio", description.title.toString())
        assertEquals("On Air Artist", description.subtitle.toString())
        assertNull(description.description)

        val stationMatchesArtist = buildMediaSessionMetadata(
            data = data.copy(
                name = "On Air Song",
                artist = "EXAMPLE RADIO",
                stationName = "Example Radio",
            ),
            bitmap = null,
            unknownTrack = "Unknown Track",
            artist = "EXAMPLE RADIO",
        ).description
        assertEquals("On Air Song", stationMatchesArtist.title.toString())
        assertEquals("EXAMPLE RADIO", stationMatchesArtist.subtitle.toString())
        assertNull(stationMatchesArtist.description)
    }

    @Test
    fun `radio station matching raw artist keeps the rendered player suffix once`() {
        val data = radioDisplayData(
            name = "On Air Song",
            artist = "KEXP",
            station = "KEXP",
        )

        val description = buildMediaSessionMetadata(
            data = data,
            bitmap = null,
            unknownTrack = "Unknown Track",
            artist = "KEXP (on Kitchen)",
        ).description

        assertEquals("On Air Song", description.title.toString())
        assertEquals("KEXP (on Kitchen)", description.subtitle.toString())
        assertNull(description.description)

        val stationTitle = buildMediaSessionMetadata(
            data = data.copy(name = null),
            bitmap = null,
            unknownTrack = "Unknown Track",
            artist = "KEXP (on Kitchen)",
        ).description
        assertEquals("KEXP", stationTitle.title.toString())
        assertNull(stationTitle.subtitle)
        assertNull(stationTitle.description)
    }

    @Test
    fun `blank radio title falls back to station in MediaDescription`() {
        val data = MediaNotificationData(
            multiplePlayers = false,
            longItemId = null,
            activeQueueItemId = null,
            name = "   ",
            artist = "On Air Artist",
            album = null,
            repeatMode = null,
            shuffleEnabled = null,
            isLongFormContent = false,
            isFavoritableTrack = false,
            isFavoritableStream = false,
            isFavorite = false,
            isPlaying = true,
            imageUrl = null,
            chapterName = null,
            elapsedTime = null,
            elapsedUpdateTimeMs = null,
            playerName = null,
            duration = null,
            stationName = "Example Radio",
        )

        val description = buildMediaSessionMetadata(
            data = data,
            bitmap = null,
            unknownTrack = "Unknown Track",
            artist = "On Air Artist",
        ).description

        assertEquals("Example Radio", description.title.toString())
        assertEquals("On Air Artist", description.subtitle.toString())
        assertNull(description.description)
    }

    private fun radioDisplayData(name: String?, artist: String?, station: String) =
        MediaNotificationData(
            multiplePlayers = false,
            longItemId = null,
            activeQueueItemId = null,
            name = name,
            artist = artist,
            album = null,
            repeatMode = null,
            shuffleEnabled = null,
            isLongFormContent = false,
            isFavoritableTrack = false,
            isFavoritableStream = false,
            isFavorite = false,
            isPlaying = true,
            imageUrl = null,
            chapterName = null,
            elapsedTime = null,
            elapsedUpdateTimeMs = null,
            playerName = null,
            duration = null,
            stationName = station,
        )

    private fun playerData(station: PlayableItem, media: PlayerMedia) =
        PlayerDataFixtures.playerData().let { original ->
            val item = QueueTrack(
                id = media.queueItemId ?: "queue-item",
                track = station,
                isPlayable = true,
                format = null,
                provider = null,
            )
            val queue = (original.queue as DataState.Data).data
            original.copy(
                player = original.player.copy(currentMedia = media),
                queue = DataState.Data(
                    queue.copy(info = queue.info.copy(currentItem = item)),
                ),
            )
        }

    private fun radioStation(name: String): RadioStation =
        RadioStation(
            itemId = "radio-id",
            provider = "test",
            name = name,
            providerMappings = null,
            metadata = null,
            favorite = null,
            uri = null,
            images = emptyMap(),
            version = null,
            isPlayable = true,
            isDynamic = false,
        )
}
