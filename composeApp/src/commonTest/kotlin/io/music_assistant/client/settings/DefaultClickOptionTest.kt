package io.music_assistant.client.settings

import io.music_assistant.client.data.model.client.ClickContext
import io.music_assistant.client.data.model.client.ItemKind
import io.music_assistant.client.data.model.client.QueueOption
import io.music_assistant.client.data.model.client.testPlaylist
import io.music_assistant.client.data.model.client.testPodcastEpisode
import io.music_assistant.client.data.model.client.testTrack
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
    fun `start radio applies only to radio-capable kinds`() {
        val radioCapable = setOf(ItemKind.TRACK, ItemKind.ALBUM, ItemKind.ARTIST, ItemKind.PLAYLIST)
        ItemKind.entries.forEach { kind ->
            assertEquals(
                kind in radioCapable,
                DefaultClickOption.START_RADIO.appliesTo(kind),
                "START_RADIO.appliesTo($kind)",
            )
        }
    }

    @Test
    fun `queue actions apply to every kind`() {
        val queueActions = DefaultClickOption.entries - DefaultClickOption.START_RADIO
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
    fun `isAvailableIn is true for PLAY_FROM_HERE with ALBUM and PLAYLIST`() {
        assertTrue(
            DefaultClickOption.PLAY_FROM_HERE.isAvailableIn(ClickContext.ALBUM, ItemKind.TRACK),
            "PLAY_FROM_HERE.isAvailableIn(ALBUM, TRACK) should be false",
        )

        assertTrue(
            DefaultClickOption.PLAY_FROM_HERE.isAvailableIn(ClickContext.PLAYLIST, ItemKind.TRACK),
            "PLAY_FROM_HERE.isAvailableIn(ALBUM, TRACK) should be false",
        )
    }

    @Test
    fun `isAvailableIn is false for PLAY_FROM_HERE outside ALBUM`() {
        val otherContexts =
            ClickContext.entries.filter { it != ClickContext.ALBUM && it != ClickContext.PLAYLIST }

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
        assertEquals(ItemAction.StartRadio, DefaultClickOption.START_RADIO.toItemAction())
    }

    @Test
    fun `effectiveFor returns null for a non-playable item`() {
        assertNull(DefaultClickOption.PLAY_NOW.effectiveFor(testTrack(isPlayable = false)))
    }

    @Test
    fun `effectiveFor keeps start radio when the item can start one`() {
        assertEquals(
            ItemAction.StartRadio,
            DefaultClickOption.START_RADIO.effectiveFor(testTrack()),
        )
    }

    @Test
    fun `effectiveFor falls back to play now when radio is unavailable`() {
        val playNow = ItemAction.Play(QueueOption.REPLACE)
        assertEquals(
            playNow,
            DefaultClickOption.START_RADIO.effectiveFor(testPlaylist(isDynamic = true)),
        )
        assertEquals(playNow, DefaultClickOption.START_RADIO.effectiveFor(testPodcastEpisode()))
    }
}
