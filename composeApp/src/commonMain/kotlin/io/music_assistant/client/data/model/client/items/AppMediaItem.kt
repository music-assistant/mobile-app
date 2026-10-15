package io.music_assistant.client.data.model.client.items

import androidx.compose.ui.graphics.vector.ImageVector
import io.music_assistant.client.data.model.client.ImageInfo
import io.music_assistant.client.data.model.client.ImageType
import io.music_assistant.client.data.model.client.MediaType
import io.music_assistant.client.data.model.client.Metadata
import io.music_assistant.client.data.model.server.AudioFormat
import io.music_assistant.client.data.model.server.ProviderMapping
import io.music_assistant.client.data.model.server.ServerMediaItem

interface PlayableItem {
    val defaultIcon: ImageVector
    val parentName: String?
    val mediaType: MediaType
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
    val canStartEndlessMix: Boolean

    val isPlayable: Boolean

    /** Server `position`. In a playlist listing it is the row key for removal, for every item type. */
    val position: Int?

    /** Returns a copy of this item with its [favorite] flag set to [favorite]. */
    fun withFavorite(favorite: Boolean?): PlayableItem
}

val PlayableItem?.isLongFormSpokenContent: Boolean
    get() = this is Audiobook || this is PodcastEpisode

object LongFormSeekDefaults {
    const val BACK_SECONDS = 10L
    const val FORWARD_SECONDS = 30L
}

/**
 * Items whose played/unplayed state can be marked on the server. Retains the original
 * [source] DTO so it can be echoed back verbatim — the server's `music/mark_played`
 * deserializes it into a full media item, which our decomposed client model can't
 * reconstruct (e.g. a podcast episode's required `position`/`podcast` fields).
 */
interface MarkableItem {
    val source: ServerMediaItem

    /**
     * A copy reflecting a new played state, for optimistically patching lists after a
     * mark request — the server only writes the playlog and emits no update event.
     */
    fun withPlayed(fullyPlayed: Boolean): AppMediaItem
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
    open val canStartEndlessMix: Boolean get() = false

    open val isPlayable: Boolean get() = true

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

/**
 * A quick favorite toggle is possible only when the add path has a [uri] to send. A live
 * input has a uri, but nothing on the server to favorite.
 */
val AppMediaItem.canBeFavorited: Boolean get() = uri != null && this !is AudioSource

fun PlayableItem.image(type: ImageType): ImageInfo? =
    images[type] ?: images[ImageType.MAIN] ?: images.firstNotNullOfOrNull { it.value }

val AudioFormat.description: String
    get() = listOfNotNull(
        contentType,
        sampleRate?.let { "$it Hz" },
        bitDepth?.let { "$it bit" },
    ).joinToString()

internal data class ProviderHash(val itemId: String, val providerInstance: String)

internal fun ProviderMapping.toHash(): ProviderHash = ProviderHash(itemId, providerInstance)
