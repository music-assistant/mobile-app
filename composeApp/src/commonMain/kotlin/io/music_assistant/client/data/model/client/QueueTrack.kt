package io.music_assistant.client.data.model.client

import co.touchlab.kermit.Logger
import io.music_assistant.client.data.model.client.AppMediaItem.Companion.toAppMediaItem
import io.music_assistant.client.data.model.server.AudioFormat
import io.music_assistant.client.data.model.server.DSPSettings
import io.music_assistant.client.data.model.server.MediaType
import io.music_assistant.client.data.model.server.ServerMediaItem
import io.music_assistant.client.data.model.server.ServerQueueItem

data class QueueTrack(
    val id: String,
    val track: PlayableItem,
    val isPlayable: Boolean,
    val format: AudioFormat?,
    val dsp: Map<String, DSPSettings>?,
) {
    fun audioFormat(playerId: String) = dsp?.get(playerId)?.outputFormat ?: format

    companion object {
        fun ServerQueueItem.toQueueTrack(): QueueTrack? {
            // Try to use the actual media_item if available
            if (mediaItem != null) {
                val appMediaItem = mediaItem.toAppMediaItem()
                if (appMediaItem is PlayableItem) {
                    return QueueTrack(
                        id = queueItemId,
                        track = appMediaItem,
                        isPlayable = true,
                        format = streamDetails?.audioFormat,
                        dsp = streamDetails?.dsp,
                    )
                } else {
                    Logger.w(
                        "QueueTrack: Item $queueItemId has wrong type ${appMediaItem?.let { it::class.simpleName }}, dropping",
                    )
                    return null
                }
            }

            // FALLBACK: No media_item, but we have name/duration - create display-only item
            if (name != null && duration != null) {
                Logger.w("QueueTrack: Creating UNPLAYABLE display item for $queueItemId (name='$name')")

                // Create synthetic media item for display purposes only
                val syntheticMediaItem = ServerMediaItem(
                    itemId = "unplayable_$queueItemId",
                    provider = "unknown",
                    name = name,
                    mediaType = MediaType.TRACK,
                    duration = duration,
                    image = image,
                    uri = null,
                    providerMappings = null,
                    metadata = null,
                    favorite = null,
                    artists = null,
                    album = null,
                    items = null,
                    isEditable = null,
                )

                val appMediaItem = syntheticMediaItem.toAppMediaItem()
                if (appMediaItem is PlayableItem) {
                    return QueueTrack(
                        id = queueItemId,
                        track = appMediaItem,
                        isPlayable = false,  // Mark as unplayable
                        format = null,
                        dsp = null,
                    )
                }
            }

            // No media_item and no fallback data - drop the item
            Logger.w("QueueTrack: Dropping item $queueItemId - no media_item and no fallback data")
            return null
        }
    }
}
