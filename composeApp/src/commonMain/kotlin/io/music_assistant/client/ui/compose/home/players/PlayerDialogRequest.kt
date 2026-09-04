package io.music_assistant.client.ui.compose.home.players

import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.Track

/**
 * The one dialog the players screen currently shows, if any.
 *
 * A single slot instead of one flag per dialog: the dialogs are modal, so only one can be
 * legal at a time, and the slot makes that structural. Every request names the player it
 * belongs to by id, never by [PlayerData] value, so the dialog keeps reading the latest
 * server state while the pager reorders or drops pages under it.
 */
sealed interface PlayerDialogRequest {
    val playerId: String

    data class Select(override val playerId: String) : PlayerDialogRequest

    data class Group(override val playerId: String) : PlayerDialogRequest

    data class Dsp(override val playerId: String) : PlayerDialogRequest

    data class SleepTimer(override val playerId: String) : PlayerDialogRequest

    data class Lyrics(override val playerId: String, val trackId: String) : PlayerDialogRequest

    data class AudioChain(
        override val playerId: String,
        val queueItemId: String,
    ) : PlayerDialogRequest

    data class PlaybackSpeed(
        override val playerId: String,
        val queueItemId: String,
    ) : PlayerDialogRequest

    /**
     * Carries the item itself: the add-to-playlist target is a snapshot taken at click time
     * (a queue row the user long-pressed), not something to re-derive from the player.
     */
    data class AddToPlaylist(
        override val playerId: String,
        val item: AppMediaItem,
    ) : PlayerDialogRequest
}

/**
 * True while the thing this dialog describes still exists on [player].
 *
 * This replaces the `remember(currentQueueItem?.id)` keys that the page-local dialog state
 * used to carry. The host closes a request that fails the check, so a dialog cannot outlive
 * the track it describes.
 */
fun PlayerDialogRequest.hasAnchor(player: PlayerData): Boolean = when (this) {
    is PlayerDialogRequest.Lyrics ->
        (player.queueInfo?.currentItem?.track as? Track)?.itemId == trackId

    is PlayerDialogRequest.AudioChain ->
        player.queueInfo?.currentItem?.id == queueItemId

    is PlayerDialogRequest.PlaybackSpeed ->
        player.queueInfo?.let { it.currentItem?.id == queueItemId && it.playbackSpeed != null }
            ?: false

    else -> true
}
