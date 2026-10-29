package io.music_assistant.client.data.model.client.items

import io.music_assistant.client.data.model.client.ArtistType
import io.music_assistant.client.data.model.client.ImageInfo
import io.music_assistant.client.data.model.client.ImageType
import io.music_assistant.client.data.model.client.MediaType
import io.music_assistant.client.data.model.client.Metadata
import io.music_assistant.client.data.model.server.ProviderMapping

data class Artist(
    override val itemId: String,
    override val provider: String,
    override val name: String,
    override val providerMappings: List<ProviderMapping>?,
    override val metadata: Metadata?,
    override val favorite: Boolean?,
    override val sortName: String? = null,
    override val uri: String?,
    override val images: Map<ImageType, ImageInfo>,
    /** Null on a server that sends no type, or for a type the app does not know. */
    val artistType: ArtistType? = null,
) : AppMediaItem() {
    override fun withFavorite(favorite: Boolean?) = copy(favorite = favorite)
    override val mediaType: MediaType = MediaType.ARTIST
    override val canStartEndlessMix: Boolean = true

    /** An author or a narrator: the page shows audiobooks instead of albums and tracks. */
    val isAudiobookArtist: Boolean get() = artistType in ArtistType.audiobookOptions
}
