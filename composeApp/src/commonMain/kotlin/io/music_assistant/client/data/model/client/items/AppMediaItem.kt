package io.music_assistant.client.data.model.client.items

import androidx.compose.ui.graphics.vector.ImageVector
import io.music_assistant.client.data.model.client.ImageInfo
import io.music_assistant.client.data.model.client.ImageType
import io.music_assistant.client.data.model.client.MediaType
import io.music_assistant.client.data.model.client.Metadata
import io.music_assistant.client.data.model.server.AudioFormat
import io.music_assistant.client.data.model.server.ProviderMapping

interface PlayableItem {
    val defaultIcon: ImageVector
    val parentName: String?
    val itemId: String
    val displayName: String
    val version: String?
    val duration: Double?
    val uri: String?
    val subtitle: String?
    val images: Map<ImageType, ImageInfo>
    val provider: String
    val isInLibrary: Boolean
    val favorite: Boolean?
    val longId: Long
        get() = itemId.hashCode().toLong()
    val canStartRadio: Boolean
}

sealed class AppMediaItem(
    val itemId: String,
    val provider: String,
    val name: String,
    val providerMappings: List<ProviderMapping>?,
    val metadata: Metadata?,
    val favorite: Boolean?,
    val mediaType: MediaType,
    val sortName: String?,
    val uri: String?,
    val images: Map<ImageType, ImageInfo>,
    val canStartRadio: Boolean = false,
) {
    open val displayName: String = name
    open val subtitle: String? = null

    val isInLibrary = provider == "library"

    val isExplicit: Boolean = metadata?.explicit == true

    /**
     * URI suitable for the play_media API.
     * For genres, always constructs a full URI since the server requires it.
     * For other types, uses the server-provided [uri].
     */
    open val mediaUri: String?
        get() = uri

    private val mappingsHashes =
        providerMappings?.map { it.toHash().hashCode() }?.toSet() ?: emptySet()

    fun hasAnyMappingFrom(other: AppMediaItem): Boolean =
        mappingsHashes.intersect(other.mappingsHashes).isNotEmpty()

    override fun equals(other: Any?): Boolean {
        return other is AppMediaItem &&
                itemId == other.itemId &&
                name == other.name &&
                mediaType == other.mediaType &&
                provider == other.provider &&
                favorite == other.favorite &&
                uri == other.uri
    }

    override fun hashCode(): Int {
        return mediaType.hashCode() +
                19 * itemId.hashCode() +
                31 * provider.hashCode() +
                37 * name.hashCode() +
                41 * (favorite?.hashCode() ?: 0) +
                43 * (uri?.hashCode() ?: 0)
    }

    override fun toString(): String =
        "AppMediaItem(" +
                "itemId='$itemId', " +
                "provider='$provider', " +
                "name='$name', " +
                "favorite=$favorite, " +
                "mediaType=$mediaType, " +
                "providerMappings=$providerMappings, " +
                "uri=$uri" +
                ")"
}

val AudioFormat.description: String
    get() = listOfNotNull(
        contentType,
        sampleRate?.let { "$it Hz" },
        bitDepth?.let { "$it bit" },
    ).joinToString()

internal data class ProviderHash(val itemId: String, val providerInstance: String)

internal fun ProviderMapping.toHash(): ProviderHash = ProviderHash(itemId, providerInstance)
