package io.music_assistant.client.data.model.client.items

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Input
import io.music_assistant.client.data.model.client.ImageInfo
import io.music_assistant.client.data.model.client.ImageType
import io.music_assistant.client.data.model.client.MediaType
import io.music_assistant.client.data.model.client.Metadata
import io.music_assistant.client.data.model.server.ProviderMapping

/**
 * A live input that a server plugin exposes (a VBAN receiver, a line-in, …). The server lists
 * the ones a client may start at the `music/browse` root, and plays one by its [uri] through
 * `play_media` like any other item. Once started it is also the queue's current item.
 *
 * It never ends, so anything queued behind it is never reached: the only meaningful action is
 * to start it now. It has no library identity and no favorite state on the server, so
 * [withFavorite] is a no-op.
 */
data class AudioSource(
    override val itemId: String,
    override val provider: String,
    override val name: String,
    override val providerMappings: List<ProviderMapping>?,
    override val metadata: Metadata?,
    override val sortName: String? = null,
    override val uri: String?,
    override val images: Map<ImageType, ImageInfo>,
    override val isPlayable: Boolean,
) : AppMediaItem(), PlayableItem {
    override val mediaType: MediaType = MediaType.AUDIO_SOURCE
    override val favorite: Boolean? = null
    override val duration: Double? = null
    override val version: String? = null
    override val position: Int? = null
    override val parentName: String? = null
    override val defaultIcon = Icons.AutoMirrored.Filled.Input
    override fun withFavorite(favorite: Boolean?) = this
}
