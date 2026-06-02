package io.music_assistant.client.data.model.client.items

import androidx.compose.ui.graphics.vector.ImageVector
import io.music_assistant.client.data.model.client.ImageInfo
import io.music_assistant.client.data.model.client.ImageType
import io.music_assistant.client.data.model.client.MediaType
import io.music_assistant.client.data.model.client.Metadata
import io.music_assistant.client.data.model.client.QueueTrack
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

    /** Returns a copy of this item with its [favorite] flag set to [favorite]. */
    fun withFavorite(favorite: Boolean?): PlayableItem
}

sealed class AppMediaItem {
    abstract val itemId: String
    abstract val provider: String
    abstract val name: String
    abstract val providerMappings: List<ProviderMapping>?
    abstract val metadata: Metadata?
    abstract val favorite: Boolean?
    abstract val mediaType: MediaType
    abstract val sortName: String?
    abstract val uri: String?
    abstract val images: Map<ImageType, ImageInfo>
    open val canStartRadio: Boolean get() = false

    open val displayName: String get() = name
    open val subtitle: String? get() = null

    val isInLibrary: Boolean get() = provider == "library"
    val isExplicit: Boolean get() = metadata?.explicit == true

    fun image(type: ImageType): ImageInfo? = images[type] ?: images[ImageType.MAIN]

    /**
     * URI suitable for the play_media API.
     * For genres, always constructs a full URI since the server requires it.
     * For other types, uses the server-provided [uri].
     */
    open val mediaUri: String?
        get() = uri

    private val mappingsHashes: Set<Int> by lazy {
        providerMappings?.map { it.toHash().hashCode() }?.toSet() ?: emptySet()
    }

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

fun PlayableItem.image(type: ImageType): ImageInfo? =
    images[type] ?: images[ImageType.MAIN]

val AudioFormat.description: String
    get() = listOfNotNull(
        contentType,
        sampleRate?.let { "$it Hz" },
        bitDepth?.let { "$it bit" },
    ).joinToString()

enum class QualityTier { HQ, SQ, LQ }

private val lossyContentTypes = setOf("mp3", "aac", "ogg", "opus", "vorbis", "m4a", "wma")

private fun AudioFormat.isLossy(): Boolean =
    contentType?.lowercase()?.let { ct -> lossyContentTypes.any { ct.contains(it) } } == true

private const val HI_RES_SAMPLE_RATE = 44_100
private const val HI_RES_BITRATE = 16

val AudioFormat.qualityTier: QualityTier?
    get() {
        val sr = sampleRate ?: return null
        val bd = bitDepth ?: return null
        if (sr < HI_RES_SAMPLE_RATE || bd < HI_RES_BITRATE) return QualityTier.LQ
        return if (isLossy()) QualityTier.SQ else QualityTier.HQ
    }

val QueueTrack.qualityTier: QualityTier?
    get() {
        val stages = listOfNotNull(format) +
                dsp?.values.orEmpty().mapNotNull { it.outputFormat }
        if (stages.isEmpty()) return null
        val tiers = stages.mapNotNull { it.qualityTier }
        if (tiers.isEmpty()) return null
        return when {
            QualityTier.LQ in tiers -> QualityTier.LQ
            QualityTier.SQ in tiers -> QualityTier.SQ
            else -> QualityTier.HQ
        }
    }

internal data class ProviderHash(val itemId: String, val providerInstance: String)

internal fun ProviderMapping.toHash(): ProviderHash = ProviderHash(itemId, providerInstance)
