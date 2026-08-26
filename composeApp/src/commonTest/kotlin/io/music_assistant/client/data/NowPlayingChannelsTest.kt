package io.music_assistant.client.data

import io.music_assistant.client.data.model.client.Chapter
import io.music_assistant.client.data.model.client.ImageInfo
import io.music_assistant.client.data.model.client.ImageType
import io.music_assistant.client.data.model.client.MediaType
import io.music_assistant.client.data.model.client.Player
import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.data.model.client.PlayerMedia
import io.music_assistant.client.data.model.client.PlayerType
import io.music_assistant.client.data.model.client.Queue
import io.music_assistant.client.data.model.client.QueueInfo
import io.music_assistant.client.data.model.client.QueueTrack
import io.music_assistant.client.data.model.client.RepeatMode
import io.music_assistant.client.data.model.client.items.Album
import io.music_assistant.client.data.model.client.items.Artist
import io.music_assistant.client.data.model.client.items.PlayableItem
import io.music_assistant.client.data.model.client.items.Track
import io.music_assistant.client.data.model.client.msUntilChapterEnd
import io.music_assistant.client.data.model.client.presentationChapter
import io.music_assistant.client.data.model.client.testAudiobook
import io.music_assistant.client.data.model.client.testPodcastEpisode
import io.music_assistant.client.data.model.client.testRadio
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

class NowPlayingRadioStreamMetadataTest {
    private fun streamMedia(
        title: String? = "Song",
        artist: String? = "Artist",
        queueItemId: String? = "queue-item-1",
        imageUrl: String? = null,
    ) = PlayerMedia(
        title = title,
        artist = artist,
        album = null,
        imageUrl = imageUrl,
        duration = null,
        queueId = "queue-1",
        queueItemId = queueItemId,
        mediaType = MediaType.RADIO,
        uri = null,
    )

    private fun radioTrack(media: PlayerMedia?): NowPlayingTrack =
        buildNowPlayingTrack(
            playerData(testRadio(), queueInfo(queueId = "queue-1"), currentMedia = media),
        )!!

    @Test
    fun overlaysDynamicStreamMetadataKeepingStationIdentity() {
        val built = radioTrack(streamMedia(imageUrl = "https://example.invalid/song.jpg"))
        assertEquals("Song", built.title)
        assertEquals("Artist", built.artist)
        assertEquals("name", built.album)
        assertEquals("id", built.mediaItemId)
        assertEquals("https://example.invalid/song.jpg", built.artworkUrl)
    }

    @Test
    fun streamTitleEqualToStationNameKeepsStaticPresentation() {
        val built = radioTrack(streamMedia(title = "name"))
        assertEquals("name", built.title)
        assertNull(built.artist)
        assertNull(built.album)
    }

    @Test
    fun missingOrBlankStreamTitleKeepsStaticPresentation() {
        assertEquals("name", radioTrack(null).title)
        assertEquals("name", radioTrack(streamMedia(title = null)).title)
        assertEquals("name", radioTrack(streamMedia(title = " ")).title)
    }

    @Test
    fun mediaStampedWithAnotherQueueItemIsIgnored() {
        val built = radioTrack(streamMedia(queueItemId = "queue-item-other"))
        assertEquals("name", built.title)
        assertNull(built.artist)
    }

    @Test
    fun unstampedMediaIsIgnored() {
        val built = radioTrack(streamMedia(queueItemId = null))
        assertEquals("name", built.title)
        assertNull(built.artist)
    }

    @Test
    fun blankStreamArtistRendersAsNoArtist() {
        assertNull(radioTrack(streamMedia(artist = " ")).artist)
    }

    @Test
    fun nonRadioContentIgnoresCurrentMedia() {
        val built = buildNowPlayingTrack(
            playerData(testTrack(), queueInfo(queueId = "queue-1"), currentMedia = streamMedia()),
        )!!
        assertEquals("name", built.title)
    }
}

class NowPlayingTransportDedupTest {
    private val playing = NowPlayingTransport(
        mediaItemId = "track-1",
        isPlaying = true,
        elapsedSec = 30.0,
        anchorMs = 10_000L,
        rate = 1.0,
    )

    @Test
    fun uninterruptedPlaybackProjectsOldAnchorBeforeComparing() {
        val later = playing.copy(anchorMs = 20_000L, elapsedSec = 40.0)
        assertTrue(NowPlayingChannelChangeDetection.sameTransport(playing, later))
    }

    @Test
    fun realPositionJumpEmits() {
        val jumped = playing.copy(anchorMs = 20_000L, elapsedSec = 43.0)
        assertFalse(NowPlayingChannelChangeDetection.sameTransport(playing, jumped))
    }

    @Test
    fun playingAndRateChangesEmit() {
        assertFalse(
            NowPlayingChannelChangeDetection.sameTransport(
                playing,
                playing.copy(isPlaying = false, rate = 0.0),
            ),
        )
    }

    @Test
    fun nullElapsedMatchesOnlyNullElapsed() {
        val old = playing.copy(elapsedSec = null)
        assertTrue(
            NowPlayingChannelChangeDetection.sameTransport(old, old.copy(anchorMs = 20_000L)),
        )
        assertFalse(NowPlayingChannelChangeDetection.sameTransport(old, playing))
    }

    @Test
    fun identityChangeAlwaysEmitsFreshAnchor() {
        val samePosition = playing.copy(
            mediaItemId = "track-2",
            anchorMs = 20_000L,
            elapsedSec = 40.0,
        )
        assertFalse(NowPlayingChannelChangeDetection.sameTransport(playing, samePosition))
    }

    @Test
    fun nullTransportIsReplayedAsNull() {
        assertTrue(NowPlayingChannelChangeDetection.sameTransport(null, null))
        assertFalse(NowPlayingChannelChangeDetection.sameTransport(playing, null))
        assertNull(buildNowPlayingTransport(null, PlayerPositionTracker(), anchorMs = 1_000L))
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

/** Chapter-relative channels expose chapter position/duration; the domain stays absolute. */
class NowPlayingChapterPresentationTest {
    private val queueId = "queue-1"

    private fun audiobookWithChapters(): PlayableItem = testAudiobook().copy(
        duration = 400.0,
        chapters = listOf(
            Chapter(position = 0, name = "Ch1", start = 0.0, end = 100.0),
            Chapter(position = 1, name = "Ch2", start = 100.0, end = 200.0),
            Chapter(position = 2, name = "Ch3", start = 200.0, end = null),
        ),
    )

    private fun playerDataAt(item: PlayableItem): PlayerData =
        playerData(item, queueInfo(queueId = queueId))

    @Test
    fun presentationChapterResolvesAudiobookChapterAtPosition() {
        val chapter = playerDataAt(audiobookWithChapters()).presentationChapter(150.0)
        assertEquals("Ch2", chapter?.chapter?.name)
        assertEquals(100.0, chapter?.start)
        assertEquals(200.0, chapter?.end)
    }

    @Test
    fun presentationChapterIsAudiobookOnly() {
        assertNull(playerDataAt(testTrack()).presentationChapter(150.0))
        assertNull(playerDataAt(testPodcastEpisode()).presentationChapter(150.0))
    }

    @Test
    fun openEndedFinalChapterUsesTrackDuration() {
        val chapter = playerDataAt(audiobookWithChapters()).presentationChapter(250.0)
        assertEquals("Ch3", chapter?.chapter?.name)
        assertEquals(400.0, chapter?.end)
    }

    @Test
    fun trackChannelPresentsChapterNameAndDuration() {
        val data = playerDataAt(audiobookWithChapters())
        val chapter = data.presentationChapter(150.0)
        val built = buildNowPlayingTrack(data, chapter)!!
        assertEquals("Ch2", built.album)
        assertEquals(100.0, built.duration)
        // Identity and title stay the book's.
        assertEquals("id", built.mediaItemId)
        assertEquals("name", built.title)
    }

    @Test
    fun transportChannelPresentsChapterRelativeElapsed() {
        val tracker = PlayerPositionTracker()
        tracker.setAnchor(queueId, elapsedSec = 150.0, isPlaying = false)
        val data = playerDataAt(audiobookWithChapters())
        val transport = buildNowPlayingTransport(
            playerData = data,
            positionTracker = tracker,
            anchorMs = 10_000L,
            currentChapter = data.presentationChapter(150.0),
        )
        assertEquals(50.0, transport?.elapsedSec)
    }

    @Test
    fun msUntilChapterEndScalesByPlaybackSpeed() {
        val data = playerData(
            audiobookWithChapters(),
            queueInfo(queueId = queueId, playbackSpeed = 2.0),
        )
        val chapter = data.presentationChapter(150.0)
        // 50 media-seconds left at 2x → 25 s of wall clock, plus the boundary pad.
        assertEquals(25_250L, data.msUntilChapterEnd(chapter, 150.0))
    }

    @Test
    fun msUntilChapterEndIsNullOnceTheChapterEndIsBehindThePosition() {
        // resolveCurrentChapter holds the final chapter at exact media completion.
        // There is no boundary left to wake for, so no wake-up must be scheduled —
        // a pad-length delay here re-resolves at 4 Hz until playback stops.
        val data = playerDataAt(audiobookWithChapters())
        val finalChapter = data.presentationChapter(400.0)
        assertEquals(400.0, finalChapter?.end)
        assertNull(data.msUntilChapterEnd(finalChapter, 400.0))
        assertNull(data.msUntilChapterEnd(finalChapter, 450.0))
    }

    @Test
    fun msUntilChapterEndIsNullWhenPausedOrChapterless() {
        val data = playerDataAt(audiobookWithChapters())
        val paused = data.copy(player = data.player.copy(isPlaying = false))
        val chapter = data.presentationChapter(150.0)
        assertNull(paused.msUntilChapterEnd(chapter, 150.0))
        assertNull(data.msUntilChapterEnd(null, 150.0))
        assertNull(data.msUntilChapterEnd(chapter, null))
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

private fun playerData(
    item: PlayableItem,
    queueInfo: QueueInfo,
    currentMedia: PlayerMedia? = null,
): PlayerData = PlayerData(
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
        currentMedia = currentMedia,
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
