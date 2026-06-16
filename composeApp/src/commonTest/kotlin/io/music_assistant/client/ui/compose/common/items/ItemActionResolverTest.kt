package io.music_assistant.client.ui.compose.common.items

import io.music_assistant.client.data.model.client.ClickContext
import io.music_assistant.client.data.model.client.QueueOption
import io.music_assistant.client.data.model.client.testPodcastEpisode
import io.music_assistant.client.data.model.client.testTrack
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.action_play_album_from_here
import musicassistantclient.composeapp.generated.resources.action_play_from_here
import musicassistantclient.composeapp.generated.resources.action_play_playlist_from_here
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ItemActionResolverTest {
    private fun longClick(defaultAction: ItemAction?) = resolveLongClickActions(
        item = testTrack(),
        librarySupported = false,
        canAddToPlaylist = false,
        canRemoveFromPlaylist = false,
        progressSupported = false,
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
        val actions = resolvePlayButtonActions(testTrack(), default = ItemAction.Play(QueueOption.REPLACE))
        assertFalse(ItemAction.Play(QueueOption.REPLACE) in actions, "leading default must be excluded")
        assertTrue(ItemAction.Play(QueueOption.PLAY) in actions)
        assertTrue(ItemAction.StartRadio in actions, "tracks can start radio")
    }

    @Test
    fun `play-button overflow drops start radio when unavailable`() {
        val actions = resolvePlayButtonActions(testPodcastEpisode(), default = ItemAction.Play(QueueOption.REPLACE))
        assertFalse(ItemAction.StartRadio in actions)
        assertEquals(
            listOf(
                ItemAction.Play(QueueOption.PLAY),
                ItemAction.Play(QueueOption.NEXT),
                ItemAction.Play(QueueOption.ADD),
            ),
            actions,
        )
    }

    @Test
    fun `play-button overflow is empty for a non-playable item`() {
        assertEquals(emptyList(), resolvePlayButtonActions(testTrack(isPlayable = false), default = null))
    }
}
