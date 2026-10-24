package io.music_assistant.client.settings

import io.music_assistant.client.data.model.client.ClickContext
import io.music_assistant.client.data.model.client.ItemKind
import io.music_assistant.client.data.model.client.QueueOption
import io.music_assistant.client.data.model.client.testAudiobook
import io.music_assistant.client.data.model.client.testPlaylist
import io.music_assistant.client.data.model.client.testPodcastEpisode
import io.music_assistant.client.data.model.client.testTrack
import io.music_assistant.client.data.model.server.PlayMediaSupport
import io.music_assistant.client.ui.compose.common.items.ItemAction
import io.music_assistant.client.ui.compose.common.items.effectiveFor
import io.music_assistant.client.ui.compose.common.items.toItemAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DefaultClickOptionTest {
    @Test
    fun `start endless mix applies only to capable kinds`() {
        val mixCapable = setOf(ItemKind.TRACK, ItemKind.ALBUM, ItemKind.ARTIST, ItemKind.PLAYLIST)
        ItemKind.entries.forEach { kind ->
            assertEquals(
                kind in mixCapable,
                DefaultClickOption.START_ENDLESS_MIX.appliesTo(kind),
                "START_ENDLESS_MIX.appliesTo($kind)",
            )
        }
    }

    @Test
    fun `play shuffled applies only to ordered containers`() {
        val shuffleable = setOf(ItemKind.ALBUM, ItemKind.ARTIST, ItemKind.PLAYLIST)
        ItemKind.entries.forEach { kind ->
            assertEquals(
                kind in shuffleable,
                DefaultClickOption.PLAY_SHUFFLED.appliesTo(kind),
                "PLAY_SHUFFLED.appliesTo($kind)",
            )
        }
    }

    @Test
    fun `queue actions apply to every kind`() {
        val queueActions = DefaultClickOption.entries -
            setOf(DefaultClickOption.START_ENDLESS_MIX, DefaultClickOption.PLAY_SHUFFLED)
        queueActions.forEach { action ->
            ItemKind.entries.forEach { kind ->
                assertEquals(true, action.appliesTo(kind), "$action.appliesTo($kind)")
            }
        }
    }

    @Test
    fun `isAvailableIn currently mirrors appliesTo for every context for actions other than PLAY_FROM_HERE`() {
        DefaultClickOption.entries.filter { it != DefaultClickOption.PLAY_FROM_HERE }
            .forEach { action ->
                ItemKind.entries.forEach { kind ->
                    ClickContext.entries.forEach { ctx ->
                        assertEquals(
                            action.appliesTo(kind),
                            action.isAvailableIn(ctx, kind),
                            "$action.isAvailableIn($ctx, $kind)",
                        )
                    }
                }
            }
    }

    @Test
    fun `isAvailableIn is true for PLAY_FROM_HERE with ALBUM PLAYLIST and PODCAST`() {
        assertTrue(
            DefaultClickOption.PLAY_FROM_HERE.isAvailableIn(ClickContext.ALBUM, ItemKind.TRACK),
            "PLAY_FROM_HERE.isAvailableIn(ALBUM, TRACK) should be true",
        )

        assertTrue(
            DefaultClickOption.PLAY_FROM_HERE.isAvailableIn(ClickContext.PLAYLIST, ItemKind.TRACK),
            "PLAY_FROM_HERE.isAvailableIn(PLAYLIST, TRACK) should be true",
        )

        assertTrue(
            DefaultClickOption.PLAY_FROM_HERE.isAvailableIn(ClickContext.PODCAST, ItemKind.PODCAST_EPISODE),
            "PLAY_FROM_HERE.isAvailableIn(PODCAST, PODCAST_EPISODE) should be true",
        )
    }

    @Test
    fun `isAvailableIn is false for PLAY_FROM_HERE outside a container context`() {
        val containerContexts = setOf(ClickContext.ALBUM, ClickContext.PLAYLIST, ClickContext.PODCAST)
        val otherContexts = ClickContext.entries.filter { it !in containerContexts }

        otherContexts.forEach {
            assertFalse(
                DefaultClickOption.PLAY_FROM_HERE.isAvailableIn(it, ItemKind.TRACK),
                "PLAY_FROM_HERE.isAvailableIn($it, TRACK) should be false",
            )
        }
    }

    @Test
    fun `toItemAction maps to the matching ItemAction`() {
        assertEquals(
            ItemAction.Play(QueueOption.REPLACE),
            DefaultClickOption.PLAY_NOW.toItemAction(),
        )
        assertEquals(
            ItemAction.Play(QueueOption.PLAY),
            DefaultClickOption.INSERT_NEXT_AND_PLAY.toItemAction(),
        )
        assertEquals(
            ItemAction.Play(QueueOption.NEXT),
            DefaultClickOption.INSERT_NEXT.toItemAction(),
        )
        assertEquals(
            ItemAction.Play(QueueOption.ADD),
            DefaultClickOption.ADD_TO_QUEUE.toItemAction(),
        )
        assertEquals(ItemAction.StartEndlessMix, DefaultClickOption.START_ENDLESS_MIX.toItemAction())
        assertEquals(ItemAction.PlayShuffled, DefaultClickOption.PLAY_SHUFFLED.toItemAction())
    }

    @Test
    fun `effectiveFor returns null for a non-playable item`() {
        assertNull(DefaultClickOption.PLAY_NOW.effectiveFor(testTrack(isPlayable = false), ALL))
    }

    @Test
    fun `effectiveFor keeps start endless mix when the item can start one`() {
        assertEquals(
            ItemAction.StartEndlessMix,
            DefaultClickOption.START_ENDLESS_MIX.effectiveFor(testTrack(), ALL),
        )
    }

    @Test
    fun `effectiveFor falls back to play now when radio is unavailable`() {
        val playNow = ItemAction.Play(QueueOption.REPLACE)
        assertEquals(
            playNow,
            DefaultClickOption.START_ENDLESS_MIX.effectiveFor(testPlaylist(isDynamic = true), ALL),
        )
        assertEquals(playNow, DefaultClickOption.START_ENDLESS_MIX.effectiveFor(testPodcastEpisode(), ALL))
    }

    @Test
    fun `effectiveFor plays shuffled only for a container on a server with the flag`() {
        val playNow = ItemAction.Play(QueueOption.REPLACE)
        assertEquals(ItemAction.PlayShuffled, DefaultClickOption.PLAY_SHUFFLED.effectiveFor(testPlaylist(), ALL))
        assertEquals(playNow, DefaultClickOption.PLAY_SHUFFLED.effectiveFor(testPlaylist(), PlayMediaSupport.NONE))
        assertEquals(playNow, DefaultClickOption.PLAY_SHUFFLED.effectiveFor(testTrack(), ALL))
    }

    @Test
    fun `effectiveFor plays from here for a track or an episode only`() {
        assertEquals(ItemAction.PlayFromHere, DefaultClickOption.PLAY_FROM_HERE.effectiveFor(testTrack(), ALL))
        assertEquals(ItemAction.PlayFromHere, DefaultClickOption.PLAY_FROM_HERE.effectiveFor(testPodcastEpisode(), ALL))
        assertEquals(
            ItemAction.Play(QueueOption.REPLACE),
            DefaultClickOption.PLAY_FROM_HERE.effectiveFor(testAudiobook(), ALL),
        )
    }

    private companion object {
        val ALL = PlayMediaSupport(shuffle = true, startFromBeginning = true)
    }
}
