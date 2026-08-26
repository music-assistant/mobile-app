package io.music_assistant.client.data.factory

import co.touchlab.kermit.Logger
import io.music_assistant.client.data.model.client.MediaType
import io.music_assistant.client.data.model.client.QueueInfo
import io.music_assistant.client.data.model.client.QueueTrack
import io.music_assistant.client.data.model.client.RepeatMode
import io.music_assistant.client.data.model.client.items.PlayableItem
import io.music_assistant.client.data.model.server.ServerMediaItem
import io.music_assistant.client.data.model.server.ServerMediaItemImage
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
            autoPlayEnabled = autoplayEnabled ?: dontStopTheMusicEnabled,
            elapsedTime = elapsedTime,
            elapsedTimeLastUpdated = elapsedTimeLastUpdated,
            currentItem = currentItem?.let(::createTrack),
            radioSource = radioSource?.let { mediaItemFactory.createList(it) } ?: emptyList(),
            isDynamicPlaylist = isDynamic,
            playbackSpeed = playbackSpeed,
        )
    }

    fun createList(servers: List<ServerQueue>): List<QueueInfo> =
        servers.map { create(it) }

    fun createTrack(server: ServerQueueItem): QueueTrack? = with(server) {
        val streamImage = streamDetails?.streamMetadata?.imageUrl?.let(::liveStreamImage)

        // Try to use the actual media_item if available
        if (mediaItem != null) {
            val appMediaItem = mediaItemFactory.create(withQueueArtwork(mediaItem, streamImage))
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
                image = streamImage ?: image,
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

    /**
     * Resolves the artwork of a queued media item, in the same order the server and the web
     * frontend use. Three sources, because no single one covers every provider:
     *
     * 1. [streamImage] — the track a radio station plays right now. The media item is the
     *    station, so its own artwork is the station logo; this must outrank it.
     * 2. The media item's own `image` / `metadata.images`.
     * 3. [ServerQueueItem.image] — precomputed by the server at enqueue time from the FULL
     *    media item, so it already absorbs the album and podcast fallbacks for every
     *    provider. A queued track otherwise resolves nothing: `Track.image` is a server-side
     *    property (never serialized), `metadata.images` is null, and the flattened `album`
     *    ItemMapping usually carries `image: null` too.
     *
     * Source 3 fills only the `image` slot, which maps to `ImageType.MAIN` and therefore
     * loses to a real `metadata.images` thumb — it can fill a gap, never take anything away.
     * Source 1 must win outright, so it is also prepended to `metadata.images`, where
     * [MediaItemFactory] keeps the first entry per type.
     */
    private fun ServerQueueItem.withQueueArtwork(
        mediaItem: ServerMediaItem,
        streamImage: ServerMediaItemImage?,
    ): ServerMediaItem {
        val withImage = mediaItem.copy(image = streamImage ?: mediaItem.image ?: image)
        return if (streamImage == null) {
            withImage
        } else {
            withImage.copy(
                metadata = withImage.metadata?.let {
                    it.copy(images = listOf(streamImage) + it.images.orEmpty())
                },
            )
        }
    }

    /**
     * Wraps a live-stream artwork URL as an image DTO. `stream_metadata` carries a bare URL
     * rather than a full image object, so the provider is reported as `builtin` and the URL
     * is marked remotely accessible — matching how the web frontend synthesises it.
     */
    private fun liveStreamImage(url: String): ServerMediaItemImage = ServerMediaItemImage(
        type = "thumb",
        path = url,
        provider = "builtin",
        remotelyAccessible = true,
    )
}
