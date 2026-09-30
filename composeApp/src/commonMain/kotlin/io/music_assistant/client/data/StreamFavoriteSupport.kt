package io.music_assistant.client.data

import io.music_assistant.client.data.model.client.MediaType
import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.data.model.client.QueueTrack
import io.music_assistant.client.data.model.server.supportsFavoriteCurrentlyPlaying

/**
 * The stream's on-air song title, or null when there is none to show: a static
 * station display, an idle/pre-play stream, or a `currentMedia` payload stamped for a
 * different queue item. Shared with [hasFavoritableStreamTrack] so the Now Playing
 * metadata, the media session and the in-app player all agree on when a radio stream
 * has a real song playing, for any server or provider.
 */
internal fun radioStreamTitle(playerData: PlayerData, currentItem: QueueTrack): String? {
    if (currentItem.track.mediaType != MediaType.RADIO) return null
    val media = playerData.player.currentMedia ?: return null
    // The server always stamps queue_item_id when an MA queue item is current;
    // media stamped otherwise is not this station's stream.
    if (media.queueItemId != currentItem.id) return null
    // A title equal to the station name carries no information (idle streams and
    // the synthesized pre-play fallback both produce it).
    return media.title?.takeIf { it.isNotBlank() && it != currentItem.track.displayName }
}

/**
 * True while [PlayerData]'s current queue item is a radio stream reporting a real
 * on-air song. This is the only "there is a song to favourite" signal that holds for
 * every server and provider, so [MainDataSource.favoriteCurrentlyPlaying] is gated on
 * it rather than on stream-specific metadata.
 */
fun PlayerData.hasFavoritableStreamTrack(): Boolean {
    val currentItem = queueInfo?.currentItem ?: return false
    return radioStreamTitle(this, currentItem) != null
}

/** [hasFavoritableStreamTrack] AND the connected server can resolve it (schema >= 27). */
fun PlayerData.canFavoriteCurrentlyPlaying(schemaVersion: Int?): Boolean =
    hasFavoritableStreamTrack() && supportsFavoriteCurrentlyPlaying(schemaVersion)
