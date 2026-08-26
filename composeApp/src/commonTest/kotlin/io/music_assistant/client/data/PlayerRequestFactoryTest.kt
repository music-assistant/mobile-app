package io.music_assistant.client.data

import io.music_assistant.client.data.model.client.Chapter
import io.music_assistant.client.data.model.client.Metadata
import io.music_assistant.client.data.model.client.Player
import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.data.model.client.PlayerType
import io.music_assistant.client.data.model.client.Queue
import io.music_assistant.client.data.model.client.QueueInfo
import io.music_assistant.client.data.model.client.QueueTrack
import io.music_assistant.client.data.model.client.RepeatMode
import io.music_assistant.client.data.model.client.items.PlayableItem
import io.music_assistant.client.data.model.client.testAudiobook
import io.music_assistant.client.data.model.client.testPodcastEpisode
import io.music_assistant.client.data.model.client.testTrack
import io.music_assistant.client.data.model.server.ServerUserPreferences
import io.music_assistant.client.ui.compose.common.DataState
import io.music_assistant.client.ui.compose.common.action.PlayerAction
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins [PlayerRequestFactory.resolve] for audiobook chapter jumps. The regression it
 * guards: on the local (Sendspin) player the optimistic update freezes the tracked
 * position to 0.0 *before* the request is built, so when chapter resolution read that
 * frozen position it always seeked to chapter 2. Moving the resolution into `resolve()`
 * — which runs first and reads the *live* position — is what these tests lock in.
 */
class PlayerRequestFactoryTest {
    private val queueId = "queue-1"

    // Chapters at 0/100/200/300 s; each 100 s long.
    private fun audiobookWithChapters(): PlayableItem = testAudiobook().copy(
        chapters = listOf(
            Chapter(position = 0, name = "Ch1", start = 0.0, end = 100.0),
            Chapter(position = 1, name = "Ch2", start = 100.0, end = 200.0),
            Chapter(position = 2, name = "Ch3", start = 200.0, end = 300.0),
            Chapter(position = 3, name = "Ch4", start = 300.0, end = 400.0),
        ),
    )

    /** Resolve [action] with the shared tracker parked (paused) at [positionSec]. */
    private fun resolveAt(
        positionSec: Double,
        item: PlayableItem,
        action: PlayerAction,
        chapterProgressEnabled: Boolean = true,
    ): PlayerAction {
        val tracker = PlayerPositionTracker()
        // Paused anchor → effectiveSec returns exactly positionSec (no wall-clock drift).
        tracker.setAnchor(queueId = queueId, elapsedSec = positionSec, isPlaying = false)
        val preferences = UserPreferences().apply {
            update(ServerUserPreferences(audiobookChapterProgress = chapterProgressEnabled))
        }
        return PlayerRequestFactory(tracker, preferences).resolve(playerDataWith(item), action)
    }

    @Test
    fun nextInsideChapterSeeksToNextChapterStartNotChapterTwo() {
        // Playing 50 s into chapter 2 (100..200). Must advance to chapter 3 (200),
        // not collapse to chapter 2 from a zeroed position.
        assertEquals(PlayerAction.SeekTo(200L), resolveAt(150.0, audiobookWithChapters(), PlayerAction.Next))
    }

    @Test
    fun nextPastLastChapterFallsThroughToNextCommand() {
        // No later chapter → keep the bare Next; buildRequest then emits a plain `next`.
        assertEquals(PlayerAction.Next, resolveAt(350.0, audiobookWithChapters(), PlayerAction.Next))
    }

    @Test
    fun nextOnNonAudiobookFallsThroughToNextCommand() {
        assertEquals(PlayerAction.Next, resolveAt(150.0, testTrack(), PlayerAction.Next))
    }

    @Test
    fun previousDeepIntoChapterRestartsCurrentChapter() {
        // 50 s into chapter 2 (> 5 s grace) → restart chapter 2 (100).
        assertEquals(PlayerAction.SeekTo(100L), resolveAt(150.0, audiobookWithChapters(), PlayerAction.Previous))
    }

    @Test
    fun previousWithinGraceGoesToPreviousChapter() {
        // 2 s into chapter 2 (<= 5 s grace) → jump back to chapter 1 (0).
        assertEquals(PlayerAction.SeekTo(0L), resolveAt(102.0, audiobookWithChapters(), PlayerAction.Previous))
    }

    @Test
    fun previousOnNonAudiobookFallsThroughToPreviousCommand() {
        assertEquals(PlayerAction.Previous, resolveAt(150.0, testTrack(), PlayerAction.Previous))
    }

    @Test
    fun previousInFirstChapterSeeksToMediaStartNeverPlainPrevious() {
        // Deep into chapter 1 → restart it (0); within the 5 s grace there is no
        // prior chapter, so clamp to media start. Neither falls through to a plain
        // `previous`, which would jump to the prior queue item.
        assertEquals(PlayerAction.SeekTo(0L), resolveAt(50.0, audiobookWithChapters(), PlayerAction.Previous))
        assertEquals(PlayerAction.SeekTo(0L), resolveAt(2.0, audiobookWithChapters(), PlayerAction.Previous))
    }

    // Podcast episodes carry chapters in metadata, not a dedicated field; chapter
    // navigation covers them too (matching the web frontend's useChapterNavigation).
    private fun podcastEpisodeWithChapters(): PlayableItem = testPodcastEpisode().copy(
        metadata = Metadata(
            explicit = false,
            images = emptyList(),
            releaseDate = null,
            chapters = listOf(
                Chapter(position = 0, name = "Ch1", start = 0.0, end = 100.0),
                Chapter(position = 1, name = "Ch2", start = 100.0, end = 200.0),
                Chapter(position = 2, name = "Ch3", start = 200.0, end = 300.0),
            ),
            lyrics = null,
            lrcLyrics = null,
        ),
    )

    @Test
    fun nextOnPodcastEpisodeSeeksToNextChapterStart() {
        assertEquals(
            PlayerAction.SeekTo(200L),
            resolveAt(150.0, podcastEpisodeWithChapters(), PlayerAction.Next),
        )
    }

    @Test
    fun previousOnPodcastEpisodeRestartsCurrentChapter() {
        assertEquals(
            PlayerAction.SeekTo(100L),
            resolveAt(150.0, podcastEpisodeWithChapters(), PlayerAction.Previous),
        )
    }

    @Test
    fun chapterNavigationDisabledByPreferenceFallsThroughToPlainCommands() {
        assertEquals(
            PlayerAction.Next,
            resolveAt(150.0, audiobookWithChapters(), PlayerAction.Next, chapterProgressEnabled = false),
        )
        assertEquals(
            PlayerAction.Previous,
            resolveAt(150.0, audiobookWithChapters(), PlayerAction.Previous, chapterProgressEnabled = false),
        )
    }

    @Test
    fun fractionalChapterStartsRoundUpSoSeeksLandInsideTheChapter() {
        // A truncated fractional start would sit a fraction of a second BEFORE the
        // chapter boundary, presenting the end of the prior chapter until audio flows.
        val fractional = testAudiobook().copy(
            chapters = listOf(
                Chapter(position = 0, name = "Ch1", start = 0.0, end = 100.5),
                Chapter(position = 1, name = "Ch2", start = 100.5, end = 200.5),
                Chapter(position = 2, name = "Ch3", start = 200.5, end = 300.0),
            ),
        )
        // Next from inside Ch2 → ceil(200.5) = 201, inside Ch3.
        assertEquals(PlayerAction.SeekTo(201L), resolveAt(150.0, fractional, PlayerAction.Next))
        // Previous deep into Ch2 restarts it → ceil(100.5) = 101, inside Ch2.
        assertEquals(PlayerAction.SeekTo(101L), resolveAt(150.0, fractional, PlayerAction.Previous))
    }

    @Test
    fun chapterNavigationToleratesUnsortedChapterMetadata() {
        val unsorted = testAudiobook().copy(
            chapters = listOf(
                Chapter(position = 2, name = "Ch3", start = 200.0, end = 300.0),
                Chapter(position = 0, name = "Ch1", start = 0.0, end = 100.0),
                Chapter(position = 1, name = "Ch2", start = 100.0, end = 200.0),
            ),
        )
        assertEquals(PlayerAction.SeekTo(200L), resolveAt(150.0, unsorted, PlayerAction.Next))
        assertEquals(PlayerAction.SeekTo(100L), resolveAt(150.0, unsorted, PlayerAction.Previous))
    }

    @Test
    fun crossfadeToggleSendsTheInverseOfTheCurrentState() {
        // The server command is an absolute setter, so the factory must invert here.
        // Echoing `current` back would make the badge look inert.
        val factory = PlayerRequestFactory(PlayerPositionTracker(), UserPreferences())
        val data = playerDataWith(testAudiobook())

        val turningOn = factory.buildRequest(data, PlayerAction.ToggleCrossfade(current = false))
        assertEquals("player_queues/crossfade", turningOn?.command)
        assertEquals(JsonPrimitive(true), turningOn?.args?.get("crossfade_enabled"))
        assertEquals(JsonPrimitive(queueId), turningOn?.args?.get("queue_id"))

        val turningOff = factory.buildRequest(data, PlayerAction.ToggleCrossfade(current = true))
        assertEquals(JsonPrimitive(false), turningOff?.args?.get("crossfade_enabled"))
    }

    private fun playerDataWith(item: PlayableItem): PlayerData = PlayerData(
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
            queueId = queueId,
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
                info = QueueInfo(
                    id = queueId,
                    available = true,
                    currentIndex = 0,
                    shuffleEnabled = false,
                    repeatMode = RepeatMode.OFF,
                    autoPlayEnabled = null,
                    elapsedTime = null,
                    elapsedTimeLastUpdated = null,
                    currentItem = QueueTrack(
                        id = "queue-item-1",
                        track = item,
                        isPlayable = true,
                        format = null,
                        dsp = null,
                        provider = "test",
                    ),
                    radioSource = emptyList(),
                ),
                items = DataState.NoData(),
            ),
        ),
        parentBind = null,
        childrenBinds = emptyList(),
        isLocal = true,
    )
}
