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
import kotlin.test.assertNull

class DefaultClickActionTest {
    @Test
    fun `start radio applies only to radio-capable kinds`() {
        val radioCapable = setOf(ItemKind.TRACK, ItemKind.ALBUM, ItemKind.ARTIST, ItemKind.PLAYLIST)
        ItemKind.entries.forEach { kind ->
            assertEquals(
                kind in radioCapable,
                DefaultClickAction.START_RADIO.appliesTo(kind),
                "START_RADIO.appliesTo($kind)",
            )
        }
    }

    @Test
    fun `queue actions apply to every kind`() {
        val queueActions = DefaultClickAction.entries - DefaultClickAction.START_RADIO
        queueActions.forEach { action ->
            ItemKind.entries.forEach { kind ->
                assertEquals(true, action.appliesTo(kind), "$action.appliesTo($kind)")
            }
        }
    }

    @Test
    fun `isAvailableIn currently mirrors appliesTo for every context`() {
        DefaultClickAction.entries.forEach { action ->
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
    fun `toItemAction maps to the matching ItemAction`() {
        assertEquals(ItemAction.Play(QueueOption.REPLACE), DefaultClickAction.PLAY_NOW.toItemAction())
        assertEquals(ItemAction.Play(QueueOption.PLAY), DefaultClickAction.INSERT_NEXT_AND_PLAY.toItemAction())
        assertEquals(ItemAction.Play(QueueOption.NEXT), DefaultClickAction.INSERT_NEXT.toItemAction())
        assertEquals(ItemAction.Play(QueueOption.ADD), DefaultClickAction.ADD_TO_QUEUE.toItemAction())
        assertEquals(ItemAction.StartRadio, DefaultClickAction.START_RADIO.toItemAction())
    }

    @Test
    fun `effectiveFor returns null for a non-playable item`() {
        assertNull(DefaultClickAction.PLAY_NOW.effectiveFor(testTrack(isPlayable = false)))
    }

    @Test
    fun `effectiveFor keeps start radio when the item can start one`() {
        assertEquals(ItemAction.StartRadio, DefaultClickAction.START_RADIO.effectiveFor(testTrack()))
    }

    @Test
    fun `effectiveFor falls back to play now when radio is unavailable`() {
        val playNow = ItemAction.Play(QueueOption.REPLACE)
        assertEquals(playNow, DefaultClickAction.START_RADIO.effectiveFor(testPlaylist(isDynamic = true)))
        assertEquals(playNow, DefaultClickAction.START_RADIO.effectiveFor(testPodcastEpisode()))
    }
}
