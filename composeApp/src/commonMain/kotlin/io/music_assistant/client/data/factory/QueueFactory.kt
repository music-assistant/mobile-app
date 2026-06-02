package io.music_assistant.client.data.factory

import co.touchlab.kermit.Logger
import io.music_assistant.client.data.model.client.MediaType
import io.music_assistant.client.data.model.client.QueueInfo
import io.music_assistant.client.data.model.client.QueueTrack
import io.music_assistant.client.data.model.client.RepeatMode
import io.music_assistant.client.data.model.client.items.PlayableItem
import io.music_assistant.client.data.model.server.ServerMediaItem
import io.music_assistant.client.data.model.server.ServerQueue
import io.music_assistant.client.data.model.server.ServerQueueItem

/**
 * Maps server-side queue DTOs ([ServerQueue], [ServerQueueItem]) to client-side
 * [QueueInfo] / [QueueTrack] models. Delegates nested media-item conversion to
 * [MediaItemFactory].
 *
 * Pure & stateless; safe to register as a Koin `single`.
 */
class QueueFactory(
    private val mediaItemFactory: MediaItemFactory,
) {
    fun create(server: ServerQueue): QueueInfo = with(server) {
        QueueInfo(
            id = queueId,
            available = available,
            currentIndex = currentIndex,
            shuffleEnabled = shuffleEnabled,
            repeatMode = RepeatMode.fromServer(repeatMode) ?: RepeatMode.OFF,
            dontStopTheMusicEnabled = dontStopTheMusicEnabled,
            elapsedTime = elapsedTime,
            elapsedTimeLastUpdated = elapsedTimeLastUpdated,
            currentItem = currentItem?.let(::createTrack),
            radioSource = radioSource?.let { mediaItemFactory.createList(it) } ?: emptyList(),
        )
    }

    fun createList(servers: List<ServerQueue>): List<QueueInfo> =
        servers.map { create(it) }

    fun createTrack(server: ServerQueueItem): QueueTrack? = with(server) {
        // Try to use the actual media_item if available
        if (mediaItem != null) {
            val appMediaItem = mediaItemFactory.create(mediaItem)
            if (appMediaItem is PlayableItem) {
                return QueueTrack(
                    id = queueItemId,
                    track = appMediaItem,
                    isPlayable = appMediaItem.isPlayable,
                    format = streamDetails?.audioFormat,
                    dsp = streamDetails?.dsp,
                    provider = streamDetails?.provider,
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

            val syntheticMediaItem = ServerMediaItem(
                itemId = "unplayable_$queueItemId",
                provider = "unknown",
                name = name,
                mediaType = MediaType.TRACK.serverValue,
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

            val appMediaItem = mediaItemFactory.create(syntheticMediaItem)
            if (appMediaItem is PlayableItem) {
                return QueueTrack(
                    id = queueItemId,
                    track = appMediaItem,
                    isPlayable = false,
                    format = null,
                    dsp = null,
                    provider = null,
                )
            }
        }

        Logger.w("QueueTrack: Dropping item $queueItemId - no media_item and no fallback data")
        return null
    }

    fun createTrackList(servers: List<ServerQueueItem>): List<QueueTrack> =
        servers.mapNotNull { createTrack(it) }
}
