package io.music_assistant.client.data.model.client

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.Radio
import androidx.compose.ui.graphics.vector.ImageVector
import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments
import io.ktor.http.encodeURLQueryComponent
import io.music_assistant.client.data.model.server.AudioFormat
import io.music_assistant.client.data.model.server.MediaItemImage
import io.music_assistant.client.data.model.server.MediaType
import io.music_assistant.client.data.model.server.Metadata
import io.music_assistant.client.data.model.server.ProviderMapping
import io.music_assistant.client.data.model.server.SearchResult
import io.music_assistant.client.data.model.server.ServerMediaItem

interface PlayableItem {
    val defaultIcon: ImageVector
    val parentName: String?
    val itemId: String
    val name: String
    val duration: Double?
    val uri: String?
    val subtitle: String?
    val imageInfo: AppMediaItem.ImageInfo?
    val provider: String
    val isInLibrary: Boolean
    val favorite: Boolean?
    val longId: Long
    val canStartRadio: Boolean
}

abstract class AppMediaItem(
    val itemId: String,
    val provider: String,
    val name: String,
    val providerMappings: List<ProviderMapping>?,
    metadata: Metadata?,
    val favorite: Boolean?,
    val mediaType: MediaType,
    //val sortName: String?,
    val uri: String?,
    val image: MediaItemImage?,
    //val isPlayable: Boolean?,
    //val timestampAdded: Long?,
    //val timestampModified: Long?,
    val canStartRadio: Boolean = false
) {

    open val subtitle: String? = null
    val longId = itemId.hashCode().toLong()

    val isInLibrary = provider == "library"

    private val mappingsHashes =
        providerMappings?.map { it.toHash().hashCode() }?.toSet() ?: emptySet()

    fun hasAnyMappingFrom(other: AppMediaItem): Boolean =
        mappingsHashes.intersect(other.mappingsHashes).isNotEmpty()

    fun hasAnyMappingFrom(other: ServerMediaItem): Boolean =
        mappingsHashes
            .intersect(
                other.providerMappings?.map { it.toHash().hashCode() }?.toSet() ?: emptySet()
            )
            .isNotEmpty()

    override fun equals(other: Any?): Boolean {
        return other is AppMediaItem
                && itemId == other.itemId
                && name == other.name
                && mediaType == other.mediaType
                && provider == other.provider
                && favorite == other.favorite
                && uri == other.uri
    }

    override fun hashCode(): Int {
        return mediaType.hashCode() +
                19 * itemId.hashCode() +
                31 * provider.hashCode() +
                37 * name.hashCode() +
                41 * (favorite?.hashCode() ?: 0) +
                43 * (uri?.hashCode() ?: 0)
    }

    override fun toString(): String {
        return "AppMediaItem(itemId='$itemId', provider='$provider', name='$name', favorite=$favorite, mediaType=$mediaType, providerMappings=$providerMappings, uri=$uri)"
    }

    val imageInfo: ImageInfo? = (image ?: metadata?.images?.getOrNull(0))
        ?.let { image ->
            ImageInfo(
                image.path,
                image.remotelyAccessible,
                image.provider
            )
        }

    data class ImageInfo(
        val path: String,
        val isRemotelyAccessible: Boolean,
        val provider: String,
    ) {
        fun url(serverUrl: String?): String? =
            path.takeIf { isRemotelyAccessible }
                ?: serverUrl?.let { server ->
                    return URLBuilder(server).apply {
                        // Append the static path segment
                        appendPathSegments("imageproxy")
                        parameters.apply {
                            append(
                                "path",
                                path.encodeURLQueryComponent()
                            ) // TODO check if needed twice
                            append("provider", provider)
                            append("checksum", "")
                        }
                    }.buildString()
                }
    }

    class RecommendationFolder(
        itemId: String,
        provider: String,
        name: String,
        providerMappings: List<ProviderMapping>?,
//        mediaType: MediaType,
        //sortName: String?,
        uri: String?,
        image: MediaItemImage?,
//        isPlayable: Boolean?,
//        timestampAdded: Long?,
//        timestampModified: Long?,
//        val musicbrainzId: String?,
        val items: List<AppMediaItem>? = null,
    ) : AppMediaItem(
        itemId = itemId,
        provider = provider,
        name = name,
        providerMappings = null,
        metadata = null,
        favorite = null,
        mediaType = MediaType.ARTIST,
        //sortName,
        uri = uri,
        image = image,
        //isPlayable,
        //timestampAdded,
        //timestampModified,
    ) {
        val rowItemType = when (itemId) {
            "recently_added_tracks", "recent_favorite_tracks" -> MediaType.TRACK
            "recently_added_albums", "random_albums" -> MediaType.ALBUM
            "random_artists" -> MediaType.ARTIST
            "favorite_playlists" -> MediaType.PLAYLIST
            else -> null
        }
    }

    class Artist(
        itemId: String,
        provider: String,
        name: String,
        providerMappings: List<ProviderMapping>?,
        metadata: Metadata?,
        favorite: Boolean?,
//        mediaType: MediaType,
        //sortName: String?,
        uri: String?,
        image: MediaItemImage?,
//        isPlayable: Boolean?,
//        timestampAdded: Long?,
//        timestampModified: Long?,
//        val musicbrainzId: String?,
    ) : AppMediaItem(
        itemId = itemId,
        provider = provider,
        name = name,
        providerMappings = providerMappings,
        metadata = metadata,
        favorite = favorite,
        mediaType = MediaType.ARTIST,
        //sortName,
        uri = uri,
        image = image,
        //isPlayable,
        //timestampAdded,
        //timestampModified,
        canStartRadio = true,
    )

    class Album(
        itemId: String,
        provider: String,
        name: String,
        providerMappings: List<ProviderMapping>?,
        metadata: Metadata?,
        favorite: Boolean?,
//        mediaType: MediaType,
//        sortName: String?,
        uri: String?,
        image: MediaItemImage?,
//        isPlayable: Boolean?,
//        timestampAdded: Long?,
//        timestampModified: Long?,
//        val musicbrainzId: String?,
        //val version: String?,
//        val year: Int?,
        val artists: List<Artist>?,
//        val albumType: AlbumType?,
    ) : AppMediaItem(
        itemId = itemId,
        provider = provider,
        name = name,
        providerMappings = providerMappings,
        metadata = metadata,
        favorite = favorite,
        mediaType = MediaType.ALBUM,
        //sortName,
        uri = uri,
        image = image,
        //isPlayable,
        //timestampAdded,
        //timestampModified,
        canStartRadio = true,
    ) {
        override val subtitle = artists?.joinToString(separator = ", ") { it.name }
    }

    class Track(
        itemId: String,
        provider: String,
        name: String,
        providerMappings: List<ProviderMapping>?,
        metadata: Metadata?,
        favorite: Boolean?,
//        mediaType: MediaType,
//        sortName: String?,
        uri: String?,
        image: MediaItemImage?,
//        isPlayable: Boolean?,
//        timestampAdded: Long?,
//        timestampModified: Long?,
//        val musicbrainzId: String?,
        //val version: String?,
        override val duration: Double?,
//        val isrc: String?,
        val artists: List<Artist>?,
// album track only
        val album: Album?,
//        val discNumber: Int?,
//        val trackNumber: Int?,
// playlist track only
//        val position: Int?,
    ) : AppMediaItem(
        itemId = itemId,
        provider = provider,
        name = name,
        providerMappings = providerMappings,
        metadata = metadata,
        favorite = favorite,
        mediaType = MediaType.TRACK,
        //sortName,
        uri = uri,
        image = image,
        //isPlayable,
        //timestampAdded,
        //timestampModified,
        canStartRadio = true,
    ), PlayableItem {
        override val subtitle = artists?.joinToString(separator = ", ") { it.name }
        override val parentName: String? = album?.name
        override val defaultIcon = Icons.Default.MusicNote
    }

    class Playlist(
        itemId: String,
        provider: String,
        name: String,
        providerMappings: List<ProviderMapping>?,
        metadata: Metadata?,
        favorite: Boolean?,
        //mediaType: MediaType,
        //sortName: String?,
        uri: String?,
        image: MediaItemImage?,
        //isPlayable: Boolean?,
        //timestampAdded: Long?,
        //timestampModified: Long?,
        //val owner: String?,
        val isEditable: Boolean?,
    ) : AppMediaItem(
        itemId = itemId,
        provider = provider,
        name = name,
        providerMappings = providerMappings,
        metadata = metadata,
        favorite = favorite,
        mediaType = MediaType.PLAYLIST,
        //sortName,
        uri = uri,
        image = image,
        //isPlayable,
        //timestampAdded,
        //timestampModified,
        canStartRadio = true,
    ) {
        override val subtitle = "Playlist"
    }

    class Podcast(
        itemId: String,
        provider: String,
        name: String,
        providerMappings: List<ProviderMapping>?,
        metadata: Metadata?,
        favorite: Boolean?,
        uri: String?,
        image: MediaItemImage?,
    ) : AppMediaItem(
        itemId = itemId,
        provider = provider,
        name = name,
        providerMappings = providerMappings,
        metadata = metadata,
        favorite = favorite,
        mediaType = MediaType.PODCAST,
        uri = uri,
        image = image,
    ) {
        override val subtitle = "Podcast"
    }

    class PodcastEpisode(
        itemId: String,
        provider: String,
        name: String,
        providerMappings: List<ProviderMapping>?,
        metadata: Metadata?,
        favorite: Boolean?,
        uri: String?,
        image: MediaItemImage?,
        override val duration: Double?,
        val podcast: Podcast?,
        val fullyPlayed: Boolean?,
        val resumePositionMs: Long?,
    ) : AppMediaItem(
        itemId = itemId,
        provider = provider,
        name = name,
        providerMappings = providerMappings,
        metadata = metadata,
        favorite = favorite,
        mediaType = MediaType.PODCAST_EPISODE,
        uri = uri,
        image = image,
    ), PlayableItem {
        override val subtitle = null
        override val parentName: String? = podcast?.name
        override val defaultIcon = Icons.Default.Podcasts
    }

    class RadioStation(
        itemId: String,
        provider: String,
        name: String,
        providerMappings: List<ProviderMapping>?,
        metadata: Metadata?,
        favorite: Boolean?,
        uri: String?,
        image: MediaItemImage?,
    ) : AppMediaItem(
        itemId = itemId,
        provider = provider,
        name = name,
        providerMappings = providerMappings,
        metadata = metadata,
        favorite = favorite,
        mediaType = MediaType.RADIO,
        uri = uri,
        image = image,
    ), PlayableItem {
        override val duration: Double? = null  // Radio stations have no duration
        override val subtitle: String = "Radio"
        override val parentName: String? = null  // No parent item
        override val defaultIcon = Icons.Default.Radio
    }

    class Audiobook(
        itemId: String,
        provider: String,
        name: String,
        providerMappings: List<ProviderMapping>?,
        metadata: Metadata?,
        favorite: Boolean?,
        uri: String?,
        image: MediaItemImage?,
        override val duration: Double?,
        val authors: List<String>?,
        val narrators: List<String>?,
        val chapters: List<io.music_assistant.client.data.model.server.MediaItemChapter>?,
        val fullyPlayed: Boolean?,
        val resumePositionMs: Long?,
    ) : AppMediaItem(
        itemId = itemId,
        provider = provider,
        name = name,
        providerMappings = providerMappings,
        metadata = metadata,
        favorite = favorite,
        mediaType = MediaType.AUDIOBOOK,
        uri = uri,
        image = image,
    ), PlayableItem {
        override val subtitle = authors?.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: "Audiobook"
        override val parentName: String? = authors?.firstOrNull()
        override val defaultIcon = Icons.AutoMirrored.Filled.MenuBook
    }

    companion object {
        fun ServerMediaItem.toAppMediaItem(): AppMediaItem? =
            when (mediaType) {
                MediaType.ARTIST -> Artist(
                    itemId = itemId,
                    provider = provider,
                    name = name,
                    providerMappings = providerMappings,
                    metadata = metadata,
                    favorite = favorite,
//                    mediaType = mediaType,
//                    sortName = sortName,
                    uri = uri,
                    image = image,
//                    isPlayable = isPlayable,
//                    timestampAdded = timestampAdded,
//                    timestampModified = timestampModified,
//                    musicbrainzId = musicbrainzId,
                )

                MediaType.ALBUM -> Album(
                    itemId = itemId,
                    provider = provider,
                    name = name,
                    providerMappings = providerMappings,
                    metadata = metadata,
                    favorite = favorite,
//                    mediaType = mediaType,
//                    sortName = sortName,
                    uri = uri,
                    image = image,
//                    isPlayable = isPlayable,
//                    timestampAdded = timestampAdded,
//                    timestampModified = timestampModified,
//                    musicbrainzId = musicbrainzId,
                    //version = version,
//                    year = year,
                    artists = artists?.mapNotNull { it.toAppMediaItem() as? Artist },
//                    albumType = albumType,
                )

                MediaType.TRACK -> Track(
                    itemId = itemId,
                    provider = provider,
                    name = name,
                    providerMappings = providerMappings,
                    metadata = metadata,
                    favorite = favorite,
//                    mediaType = mediaType,
//                    sortName = sortName,
                    uri = uri,
                    image = image,
//                    isPlayable = isPlayable,
//                    timestampAdded = timestampAdded,
//                    timestampModified = timestampModified,
//                    musicbrainzId = musicbrainzId,
                    //version = version,
                    duration = duration,
//                    isrc = isrc,
                    artists = artists?.mapNotNull { it.toAppMediaItem() as? Artist },
                    album = album?.let { it.toAppMediaItem() as? Album },
//                    discNumber = discNumber,
//                    trackNumber = trackNumber,
//                    position = position,
                )

                MediaType.PLAYLIST -> Playlist(
                    itemId = itemId,
                    provider = provider,
                    name = name,
                    providerMappings = providerMappings,
                    metadata = metadata,
                    favorite = favorite,
//                    mediaType = mediaType,
//                    sortName = sortName,
                    uri = uri,
                    image = image,
                    //isPlayable = isPlayable,
//                    timestampAdded = timestampAdded,
//                    timestampModified = timestampModified,
//                    owner = owner,
                    isEditable = isEditable,
                )

                MediaType.FOLDER -> RecommendationFolder(
                    itemId = itemId,
                    provider = provider,
                    name = name,
                    providerMappings = providerMappings,
                    uri = uri,
                    image = image,
                    items = items?.toAppMediaItemList()
                )

                MediaType.PODCAST -> Podcast(
                    itemId = itemId,
                    provider = provider,
                    name = name,
                    providerMappings = providerMappings,
                    metadata = metadata,
                    favorite = favorite,
                    uri = uri,
                    image = image,
                )

                MediaType.PODCAST_EPISODE -> PodcastEpisode(
                    itemId = itemId,
                    provider = provider,
                    name = name,
                    providerMappings = providerMappings,
                    metadata = metadata,
                    favorite = favorite,
                    uri = uri,
                    image = image,
                    duration = duration,
                    podcast = podcast?.let { it.toAppMediaItem() as? Podcast },
                    fullyPlayed = fullyPlayed,
                    resumePositionMs = resumePositionMs,
                )

                MediaType.RADIO -> RadioStation(
                    itemId = itemId,
                    provider = provider,
                    name = name,
                    providerMappings = providerMappings,
                    metadata = metadata,
                    favorite = favorite,
                    uri = uri,
                    image = image,
                )

                MediaType.AUDIOBOOK -> Audiobook(
                    itemId = itemId,
                    provider = provider,
                    name = name,
                    providerMappings = providerMappings,
                    metadata = metadata,
                    favorite = favorite,
                    uri = uri,
                    image = image,
                    duration = duration,
                    authors = authors,
                    narrators = narrators,
                    chapters = metadata?.chapters,
                    fullyPlayed = fullyPlayed,
                    resumePositionMs = resumePositionMs,
                )

                MediaType.FLOW_STREAM,
                MediaType.ANNOUNCEMENT,
                MediaType.UNKNOWN -> null
            }

        fun List<ServerMediaItem>.toAppMediaItemList() =
            mapNotNull { it.toAppMediaItem() }

        fun SearchResult.toAppMediaItemList() =
            artists.toAppMediaItemList() +
                    albums.toAppMediaItemList() +
                    tracks.toAppMediaItemList() +
                    playlists.toAppMediaItemList() +
                    podcasts.toAppMediaItemList() +
                    audiobooks.toAppMediaItemList() +
                    radios.toAppMediaItemList()

        val AudioFormat.description
            get() = listOfNotNull(
                contentType,
                sampleRate?.let { "$it Hz" },
                bitDepth?.let { "$it bit" },
            ).joinToString()

        private data class ProviderHash(val itemId: String, val providerInstance: String)

        private fun ProviderMapping.toHash(): ProviderHash = ProviderHash(itemId, providerInstance)
    }
}
