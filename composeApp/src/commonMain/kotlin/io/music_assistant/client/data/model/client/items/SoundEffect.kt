package io.music_assistant.client.data.model.client.items

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RecordVoiceOver
import io.music_assistant.client.data.model.client.ImageInfo
import io.music_assistant.client.data.model.client.ImageType
import io.music_assistant.client.data.model.client.MediaType
import io.music_assistant.client.data.model.client.Metadata
import io.music_assistant.client.data.model.server.ProviderMapping

/**
 * A server-rendered audio clip that is not library content. It reaches us in two ways:
 * - as a queue item: the spoken host segments the `ai_radio` plugin splices between tracks;
 * - from `music/sound_effects`: the ambient sounds a queue can mix in as an overlay. The
 *   overlay selects the sound by its [uri].
 *
 * Without this type [io.music_assistant.client.data.factory.MediaItemFactory] returns null
 * for `sound_effect` and [io.music_assistant.client.data.factory.QueueFactory] drops the
 * item, so the clip plays while staying invisible in the queue.
 *
 * It has no favorite state on the server, so [withFavorite] is a no-op.
 */
data class SoundEffect(
    override val itemId: String,
    override val provider: String,
    override val name: String,
    override val providerMappings: List<ProviderMapping>?,
    override val metadata: Metadata?,
    override val sortName: String? = null,
    override val images: Map<ImageType, ImageInfo>,
    override val duration: Double?,
    override val uri: String?,
) : AppMediaItem(), PlayableItem {
    override val mediaType: MediaType = MediaType.SOUND_EFFECT
    override val favorite: Boolean? = null
    override val version: String? = null
    override val position: Int? = null
    override val parentName: String? = null
    override val defaultIcon = Icons.Default.RecordVoiceOver
    override fun withFavorite(favorite: Boolean?) = this
}
