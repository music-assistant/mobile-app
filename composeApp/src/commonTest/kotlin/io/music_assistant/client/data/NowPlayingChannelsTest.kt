package io.music_assistant.client.data

import io.music_assistant.client.data.model.client.ImageInfo
import io.music_assistant.client.data.model.client.ImageType
import io.music_assistant.client.data.model.client.Player
import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.data.model.client.PlayerType
import io.music_assistant.client.data.model.client.Queue
import io.music_assistant.client.data.model.client.QueueInfo
import io.music_assistant.client.data.model.client.QueueTrack
import io.music_assistant.client.data.model.client.RepeatMode
import io.music_assistant.client.data.model.client.items.Album
import io.music_assistant.client.data.model.client.items.Artist
import io.music_assistant.client.data.model.client.items.PlayableItem
import io.music_assistant.client.data.model.client.items.Track
import io.music_assistant.client.data.model.client.testAudiobook
import io.music_assistant.client.data.model.client.testTrack
import io.music_assistant.client.ui.compose.common.DataState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NowPlayingTrackChannelTest {
    private val track = NowPlayingTrack(
        mediaItemId = "track-1",
        title = "About Farewell",
        artist = "Alela Diane",
        album = "About Farewell",
        artworkUrl = "https://example.invalid/cover.jpg",
        duration = 240.0,
        isLongFormContent = false,
    )

    @Test
    fun valueEqualityDedupesSameSongQueuedTwice() {
        assertTrue(track == track.copy())
        assertFalse(track == track.copy(mediaItemId = "track-2"))
    }

    @Test
    fun displayedMetadataChangeEmits() {
        assertFalse(track == track.copy(title = "Different"))
    }

    @Test
    fun mapperPreservesIdentityMetadataArtworkDurationAndLongFormFlag() {
        val artist = Artist(
            itemId = "artist-1",
            provider = "test",
            name = "Artist",
            providerMappings = null,
            metadata = null,
            favorite = null,
            uri = null,
            images = emptyMap(),
        )
        val album = Album(
            itemId = "album-1",
            provider = "test",
            name = "Album",
            providerMappings = null,
            metadata = null,
            favorite = null,
            uri = null,
            images = emptyMap(),
            version = null,
            year = null,
            artists = listOf(artist),
        )
        val image = ImageInfo(
            type = ImageType.THUMB,
            path = "cover.jpg",
            isRemotelyAccessible = true,
            provider = "test",
            url = "https://example.invalid/cover.jpg",
        )
        val item = Track(
            itemId = "track-42",
            provider = "test",
            name = "Song",
            providerMappings = null,
            metadata = null,
            favorite = null,
            uri = null,
            images = mapOf(ImageType.THUMB to image),
            duration = 240.0,
            isPlayable = true,
            artists = listOf(artist),
            album = album,
            discNumber = null,
            trackNumber = null,
            position = null,
            version = null,
        )

        assertEquals(
            NowPlayingTrack(
                mediaItemId = "track-42",
                title = "Song",
                artist = "Artist",
                album = "Album",
                artworkUrl = "https://example.invalid/cover.jpg",
                duration = 240.0,
                isLongFormContent = false,
            ),
            buildNowPlayingTrack(playerData(item, queueInfo(queueId = "queue-1"))),
        )
        assertTrue(
            buildNowPlayingTrack(playerData(testAudiobook(), queueInfo(queueId = "queue-1")))!!
                .isLongFormContent,
        )
    }
}

class NowPlayingTransportDedupTest {
    private val playing = NowPlayingTransport(
        isPlaying = true,
        elapsedSec = 30.0,
        anchorMs = 10_000L,
        rate = 1.0,
    )

    @Test
    fun uninterruptedPlaybackProjectsOldAnchorBeforeComparing() {
        val later = playing.copy(anchorMs = 20_000L, elapsedSec = 40.0)
        assertTrue(
            NowPlayingChannelChangeDetection.sameTransport("track-1", playing, "track-1", later),
        )
    }

    @Test
    fun realPositionJumpEmits() {
        val jumped = playing.copy(anchorMs = 20_000L, elapsedSec = 43.0)
        assertFalse(
            NowPlayingChannelChangeDetection.sameTransport("track-1", playing, "track-1", jumped),
        )
    }

    @Test
    fun playingAndRateChangesEmit() {
        assertFalse(
            NowPlayingChannelChangeDetection.sameTransport(
                "track-1",
                playing,
                "track-1",
                playing.copy(isPlaying = false, rate = 0.0),
            ),
        )
    }

    @Test
    fun nullElapsedMatchesOnlyNullElapsed() {
        val old = playing.copy(elapsedSec = null)
        assertTrue(
            NowPlayingChannelChangeDetection.sameTransport(
                "track-1",
                old,
                "track-1",
                old.copy(anchorMs = 20_000L),
            ),
        )
        assertFalse(
            NowPlayingChannelChangeDetection.sameTransport("track-1", old, "track-1", playing),
        )
    }

    @Test
    fun identityChangeAlwaysEmitsFreshAnchor() {
        val samePosition = playing.copy(anchorMs = 20_000L, elapsedSec = 40.0)
        assertFalse(
            NowPlayingChannelChangeDetection.sameTransport(
                "track-1",
                playing,
                "track-2",
                samePosition,
            ),
        )
    }

    @Test
    fun nullTransportIsReplayedAsNull() {
        assertTrue(
            NowPlayingChannelChangeDetection.sameTransport(null, null, null, null),
        )
        assertNull(buildNowPlayingTransport(null, PlayerPositionTracker(), anchorMs = 1_000L))
        assertNull(NowPlayingTransportEmission("track-1", null).transport)
    }

    @Test
    fun mapperPreservesVariablePlaybackRate() {
        val queueId = "queue-1"
        val transport = buildNowPlayingTransport(
            playerData(testTrack(), queueInfo(queueId = queueId, playbackSpeed = 1.25)),
            PlayerPositionTracker(),
            anchorMs = 10_000L,
        )

        assertEquals(1.25, transport?.rate)
    }

    @Test
    fun mapperCollapsesFrozenPlayingStateToZeroRate() {
        val queueId = "queue-1"
        val tracker = PlayerPositionTracker()
        tracker.setAnchor(queueId, elapsedSec = 30.0, isPlaying = true)
        tracker.setOptimisticSeek(queueId, elapsedSec = 90.0)

        val transport = buildNowPlayingTransport(
            playerData(testTrack(), queueInfo(queueId = queueId)),
            tracker,
            anchorMs = 10_000L,
        )

        assertEquals(0.0, transport?.rate)
        assertTrue(transport?.isPlaying == true)
    }
}

class NowPlayingModesChannelTest {
    @Test
    fun modeValuesAreComparedByValue() {
        val modes = NowPlayingModes(true, RepeatMode.ONE, true)
        assertTrue(modes == modes.copy())
        assertFalse(modes == modes.copy(shuffleEnabled = false))
        assertFalse(modes == modes.copy(repeatMode = RepeatMode.ALL))
    }

    @Test
    fun togglesEnabledGatesDynamicAndLongFormContent() {
        assertTrue(nowPlayingTogglesEnabled(isDynamicPlaylist = false, isLongFormContent = false))
        assertFalse(nowPlayingTogglesEnabled(isDynamicPlaylist = true, isLongFormContent = false))
        assertFalse(nowPlayingTogglesEnabled(isDynamicPlaylist = false, isLongFormContent = true))
        assertFalse(nowPlayingTogglesEnabled(isDynamicPlaylist = true, isLongFormContent = true))
    }

    @Test
    fun nullPlayerProducesNoModes() {
        assertNull(buildNowPlayingModes(null))
    }

    @Test
    fun mapperReadsQueueModesAndAppliesBothAvailabilityGates() {
        val base = playerData(testTrack(), queueInfo(queueId = "queue-1"))
        assertEquals(
            NowPlayingModes(shuffleEnabled = false, repeatMode = RepeatMode.OFF, togglesEnabled = true),
            buildNowPlayingModes(base),
        )
        assertFalse(
            buildNowPlayingModes(
                playerData(testTrack(), queueInfo(queueId = "queue-1", isDynamicPlaylist = true)),
            )!!.togglesEnabled,
        )
        assertFalse(
            buildNowPlayingModes(playerData(testAudiobook(), queueInfo(queueId = "queue-1")))!!.togglesEnabled,
        )
    }
}

private fun queueInfo(
    queueId: String,
    isDynamicPlaylist: Boolean = false,
    playbackSpeed: Double? = null,
): QueueInfo = QueueInfo(
    id = queueId,
    available = true,
    currentIndex = 0,
    shuffleEnabled = false,
    repeatMode = RepeatMode.OFF,
    autoPlayEnabled = null,
    elapsedTime = 30.0,
    elapsedTimeLastUpdated = null,
    currentItem = QueueTrack(
        id = "queue-item-1",
        track = testTrack(),
        isPlayable = true,
        format = null,
        dsp = null,
        provider = "test",
    ),
    radioSource = emptyList(),
    isDynamicPlaylist = isDynamicPlaylist,
    playbackSpeed = playbackSpeed,
)

private fun playerData(item: PlayableItem, queueInfo: QueueInfo): PlayerData = PlayerData(
    player = Player(
        id = "player-1",
        name = "Test player",
        provider = "test",
        type = PlayerType.PLAYER,
        shouldBeShown = true,
        canSetVolume = false,
        canPower = false,
        isPowered = true,
        volumeLevel = null,
        volumeControl = null,
        volumeMuted = false,
        canMute = false,
        queueId = queueInfo.id,
        isPlaying = true,
        isAnnouncing = false,
        canGroupWith = null,
        groupMembers = null,
        staticGroupMembers = null,
        activeGroup = null,
        syncedTo = null,
        groupVolume = null,
        groupVolumeMuted = false,
        currentMedia = null,
    ),
    queue = DataState.Data(
        Queue(
            queueInfo.copy(currentItem = queueInfo.currentItem?.copy(track = item)),
            DataState.NoData(),
        ),
    ),
    parentBind = null,
    childrenBinds = emptyList(),
    isLocal = true,
)
