package io.music_assistant.client.ui.compose.common.items

import io.music_assistant.client.data.model.client.ClickContext
import io.music_assistant.client.data.model.client.QueueOption
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.RecommendationFolder
import io.music_assistant.client.data.model.client.testAlbum
import io.music_assistant.client.data.model.client.testArtist
import io.music_assistant.client.data.model.client.testAudiobook
import io.music_assistant.client.data.model.client.testGenre
import io.music_assistant.client.data.model.client.testPlaylist
import io.music_assistant.client.data.model.client.testPodcast
import io.music_assistant.client.data.model.client.testPodcastEpisode
import io.music_assistant.client.data.model.client.testTrack
import io.music_assistant.client.data.model.server.PlayMediaSupport
import io.music_assistant.client.data.model.server.PlayMediaSupport.Companion.ALL
import io.music_assistant.client.data.play.PlayMode
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.action_play_album_from_here
import musicassistantclient.composeapp.generated.resources.action_play_from_here
import musicassistantclient.composeapp.generated.resources.action_play_playlist_from_here
import musicassistantclient.composeapp.generated.resources.action_play_podcast_from_here
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ItemActionResolverTest {
    private fun longClick(defaultAction: ItemAction?) = resolveLongClickActions(
        item = testTrack(),
        canEditLibrary = true,
        dislikeSupported = false,
        librarySupported = false,
        canAddToPlaylist = false,
        canRemoveFromPlaylist = false,
        progressSupported = false,
        playMediaSupport = ALL,
        defaultAction = defaultAction,
    )

    // Regression: surfacing PLAY_FROM_HERE in the car per-kind tap dropdown renders the action
    // with a null context. title()/icon() must fall back rather than throw (they used to).
    @Test
    fun `play from here title falls back to generic for context-less surfaces`() {
        assertEquals(Res.string.action_play_from_here, ItemAction.PlayFromHere.title(null))
        assertEquals(
            Res.string.action_play_album_from_here,
            ItemAction.PlayFromHere.title(ClickContext.ALBUM),
        )
        assertEquals(
            Res.string.action_play_playlist_from_here,
            ItemAction.PlayFromHere.title(ClickContext.PLAYLIST),
        )
    }

    @Test
    fun `play from here icon resolves for context-less surfaces without throwing`() {
        // Just must not throw; album/playlist keep their specific icons (covered by the title test's
        // parity). A non-album/playlist context returns the generic list-play icon.
        ItemAction.PlayFromHere.icon(null)
        ItemAction.PlayFromHere.icon(ClickContext.ARTIST)
    }

    @Test
    fun `no default keeps natural order with play now first`() {
        assertEquals(ItemAction.Play(QueueOption.REPLACE), longClick(defaultAction = null).first())
    }

    @Test
    fun `default action is hoisted to the front exactly once`() {
        val default = ItemAction.Play(QueueOption.NEXT)
        val actions = longClick(default)
        assertEquals(default, actions.first())
        assertEquals(1, actions.count { it == default })
    }

    @Test
    fun `play-button overflow includes play now and excludes the leading default`() {
        val actions = resolvePlayButtonActions(testTrack(), default = ItemAction.Play(QueueOption.REPLACE), ALL)
        assertFalse(ItemAction.Play(QueueOption.REPLACE) in actions, "leading default must be excluded")
        assertTrue(ItemAction.Play(QueueOption.PLAY) in actions)
        assertTrue(ItemAction.StartEndlessMix in actions, "tracks can start endless mix")
    }

    @Test
    fun `play-button overflow drops start endless mix when unavailable`() {
        val actions =
            resolvePlayButtonActions(testPodcastEpisode(), default = ItemAction.Play(QueueOption.REPLACE), ALL)
        assertFalse(ItemAction.StartEndlessMix in actions)
        assertEquals(
            listOf(
                ItemAction.Play(QueueOption.PLAY),
                ItemAction.Play(QueueOption.NEXT),
                ItemAction.Play(QueueOption.ADD),
                ItemAction.Play(QueueOption.REPLACE_NEXT),
            ),
            actions,
        )
    }

    @Test
    fun `play-button overflow is empty for a non-playable item`() {
        assertEquals(emptyList(), resolvePlayButtonActions(testTrack(isPlayable = false), default = null, ALL))
    }

    private fun playbackBlock(item: AppMediaItem, context: ClickContext?, support: PlayMediaSupport) =
        resolveLongClickActions(
            item = item,
            clickContext = context,
            canEditLibrary = true,
            dislikeSupported = false,
            librarySupported = false,
            canAddToPlaylist = false,
            canRemoveFromPlaylist = false,
            progressSupported = false,
            playMediaSupport = support,
        )

    private val fiveQueueOptions = listOf(
        QueueOption.REPLACE,
        QueueOption.PLAY,
        QueueOption.NEXT,
        QueueOption.ADD,
        QueueOption.REPLACE_NEXT,
    ).map { ItemAction.Play(it) }

    @Test
    fun `a playable browse folder offers the five queue options and play shuffled`() {
        val folder = RecommendationFolder(
            itemId = "audio/disc1",
            provider = "fs",
            name = "Disc 1",
            uri = "fs://folder/audio/disc1",
            images = emptyMap(),
            isPlayable = true,
        )
        assertEquals(fiveQueueOptions + ItemAction.PlayShuffled, playbackBlock(folder, ClickContext.BROWSE, ALL))
        assertEquals(fiveQueueOptions, playbackBlock(folder, ClickContext.BROWSE, PlayMediaSupport.NONE))
    }

    @Test
    fun `play shuffled is offered for containers only`() {
        assertTrue(ItemAction.PlayShuffled in playbackBlock(testAlbum(), ClickContext.LIBRARY, ALL))
        assertTrue(ItemAction.PlayShuffled in playbackBlock(testArtist(), ClickContext.LIBRARY, ALL))
        assertTrue(ItemAction.PlayShuffled in playbackBlock(testPlaylist(), ClickContext.LIBRARY, ALL))
        assertTrue(ItemAction.PlayShuffled in playbackBlock(testGenre(), ClickContext.LIBRARY, ALL))
        assertFalse(ItemAction.PlayShuffled in playbackBlock(testTrack(), ClickContext.LIBRARY, ALL))
        assertFalse(ItemAction.PlayShuffled in playbackBlock(testPodcast(), ClickContext.LIBRARY, ALL))
        assertFalse(ItemAction.PlayShuffled in playbackBlock(testAudiobook(), ClickContext.LIBRARY, ALL))
    }

    @Test
    fun `play from beginning needs an episode with progress and server support`() {
        val fresh = testPodcastEpisode()
        val started = testPodcastEpisode(resumePositionMs = 90_000L)
        val finished = testPodcastEpisode(fullyPlayed = true)
        assertFalse(ItemAction.PlayFromBeginning in playbackBlock(fresh, ClickContext.HOME, ALL))
        assertTrue(ItemAction.PlayFromBeginning in playbackBlock(started, ClickContext.HOME, ALL))
        assertTrue(ItemAction.PlayFromBeginning in playbackBlock(finished, ClickContext.HOME, ALL))
        assertFalse(ItemAction.PlayFromBeginning in playbackBlock(finished, ClickContext.HOME, PlayMediaSupport.NONE))
        // An audiobook keeps its resume position: the server flag is for episodes only.
        assertFalse(ItemAction.PlayFromBeginning in playbackBlock(testAudiobook(), ClickContext.HOME, ALL))
    }

    @Test
    fun `an episode in its podcast offers play from here to latest`() {
        val episode = testPodcastEpisode()
        assertTrue(ItemAction.PlayFromHere in playbackBlock(episode, ClickContext.PODCAST, ALL))
        assertFalse(ItemAction.PlayFromHere in playbackBlock(episode, ClickContext.HOME, ALL))
        assertEquals(Res.string.action_play_podcast_from_here, ItemAction.PlayFromHere.title(ClickContext.PODCAST))
    }

    @Test
    fun `each playback action maps to one play mode and the rest to none`() {
        assertEquals(PlayMode(QueueOption.REPLACE_NEXT), ItemAction.Play(QueueOption.REPLACE_NEXT).toPlayMode())
        assertEquals(PlayMode(QueueOption.REPLACE, shuffle = true), ItemAction.PlayShuffled.toPlayMode())
        assertEquals(PlayMode(QueueOption.PLAY, startFromBeginning = true), ItemAction.PlayFromBeginning.toPlayMode())
        assertEquals(PlayMode(QueueOption.REPLACE, fromHereInParent = true), ItemAction.PlayFromHere.toPlayMode())
        assertEquals(PlayMode(QueueOption.REPLACE, endlessMix = true), ItemAction.StartEndlessMix.toPlayMode())
        assertEquals(null, ItemAction.AddToLibrary.toPlayMode())
        assertEquals(null, ItemAction.Customize.toPlayMode())
    }

    private val libraryWrites = setOf(
        ItemAction.AddToLibrary,
        ItemAction.RemoveFromLibrary,
        ItemAction.Favorite,
        ItemAction.Unfavorite,
        ItemAction.Dislike,
        ItemAction.RemoveDislike,
        ItemAction.AddToPlaylist,
        ItemAction.RemoveFromPlaylist,
        ItemAction.MarkPlayed,
        ItemAction.MarkUnplayed,
    )

    private fun longClickWrites(canEditLibrary: Boolean) = resolveLongClickActions(
        item = testPodcastEpisode(),
        canEditLibrary = canEditLibrary,
        dislikeSupported = true,
        librarySupported = true,
        canAddToPlaylist = true,
        canRemoveFromPlaylist = true,
        progressSupported = true,
        playMediaSupport = ALL,
    ).filter { it in libraryWrites }

    @Test
    fun `long-click menu offers library writes only with library write scope`() {
        assertTrue(longClickWrites(canEditLibrary = true).isNotEmpty())
        assertEquals(emptyList(), longClickWrites(canEditLibrary = false))
    }

    @Test
    fun `long-click menu keeps playback actions without library write scope`() {
        val actions = resolveLongClickActions(
            item = testTrack(),
            canEditLibrary = false,
            dislikeSupported = false,
            librarySupported = true,
            canAddToPlaylist = true,
            canRemoveFromPlaylist = true,
            progressSupported = true,
            playMediaSupport = ALL,
        )
        assertTrue(ItemAction.Play(QueueOption.REPLACE) in actions)
    }

    @Test
    fun `detail overflow is empty without library write scope`() {
        val overflow = { canEditLibrary: Boolean ->
            resolveDetailOverflowActions(
                item = testTrack(),
                canEditLibrary = canEditLibrary,
                dislikeSupported = false,
                librarySupported = true,
                canAddToPlaylist = true,
            )
        }
        assertTrue(overflow(true).isNotEmpty())
        assertEquals(emptyList(), overflow(false))
    }

    @Test
    fun `favorite menu has a like pair and a dislike pair on server 2_11`() {
        assertEquals(listOf(ItemAction.Favorite, ItemAction.Dislike), favoriteActions(null, dislikeSupported = true))
        assertEquals(listOf(ItemAction.Unfavorite, ItemAction.Dislike), favoriteActions(true, dislikeSupported = true))
        assertEquals(
            listOf(ItemAction.Favorite, ItemAction.RemoveDislike),
            favoriteActions(false, dislikeSupported = true),
        )
    }

    // An older server sends false for "not a favorite", so it is never a dislike there.
    @Test
    fun `favorite menu is a like toggle without dislike support`() {
        assertEquals(listOf(ItemAction.Favorite), favoriteActions(false, dislikeSupported = false))
        assertEquals(listOf(ItemAction.Unfavorite), favoriteActions(true, dislikeSupported = false))
    }

    @Test
    fun `favorite entries set the state that they name`() {
        assertEquals(true, ItemAction.Favorite.favorite)
        assertEquals(false, ItemAction.Dislike.favorite)
        assertEquals(null, ItemAction.Unfavorite.favorite)
        assertEquals(null, ItemAction.RemoveDislike.favorite)
    }
}
