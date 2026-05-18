package io.music_assistant.client.data.factory

import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments
import io.ktor.http.encodeURLQueryComponent
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.data.model.client.Chapter
import io.music_assistant.client.data.model.client.ImageInfo
import io.music_assistant.client.data.model.client.ImageType
import io.music_assistant.client.data.model.client.MediaType
import io.music_assistant.client.data.model.client.Metadata
import io.music_assistant.client.data.model.client.items.Album
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.Artist
import io.music_assistant.client.data.model.client.items.Audiobook
import io.music_assistant.client.data.model.client.items.Genre
import io.music_assistant.client.data.model.client.items.Playlist
import io.music_assistant.client.data.model.client.items.Podcast
import io.music_assistant.client.data.model.client.items.PodcastEpisode
import io.music_assistant.client.data.model.client.items.RadioStation
import io.music_assistant.client.data.model.client.items.RecommendationFolder
import io.music_assistant.client.data.model.client.items.Track
import io.music_assistant.client.data.model.server.SearchResult
import io.music_assistant.client.data.model.server.ServerMediaItem
import io.music_assistant.client.data.model.server.ServerMediaItemChapter
import io.music_assistant.client.data.model.server.ServerMediaItemImage
import io.music_assistant.client.data.model.server.ServerMetadata
import io.music_assistant.client.data.repository.SearchResultData

/**
 * Maps server-side [ServerMediaItem] DTOs into typed client [AppMediaItem] subtypes.
 *
 * Single concrete dispatcher — keep all type-switching here so subtypes stay dumb data classes.
 * Pure & stateless; safe to register as a Koin `single`.
 */
class MediaItemFactory(
    private val apiClient: ServiceClient,
) {
    fun create(server: ServerMediaItem): AppMediaItem? = with(server) {
        when (MediaType.fromServer(mediaType)) {
            MediaType.ARTIST -> Artist(
                itemId = itemId,
                provider = provider,
                name = name,
                providerMappings = providerMappings,
                metadata = createMetadata(metadata),
                favorite = favorite,
                sortName = sortName,
                uri = uri,
                images = resolveImageInfo(image, metadata),
            )

            MediaType.ALBUM -> Album(
                itemId = itemId,
                provider = provider,
                name = name,
                providerMappings = providerMappings,
                metadata = createMetadata(metadata),
                favorite = favorite,
                sortName = sortName,
                uri = uri,
                images = resolveImageInfo(image, metadata),
                version = version,
                year = year,
                artists = artists?.mapNotNull { create(it) as? Artist } ?: emptyList(),
            )

            MediaType.TRACK -> Track(
                itemId = itemId,
                provider = provider,
                name = name,
                providerMappings = providerMappings,
                metadata = createMetadata(metadata),
                favorite = favorite,
                sortName = sortName,
                uri = uri,
                images = resolveImageInfo(image, metadata),
                duration = duration,
                artists = artists?.mapNotNull { create(it) as? Artist } ?: emptyList(),
                album = album?.let { create(it) as? Album },
                discNumber = discNumber,
                trackNumber = trackNumber,
                position = position,
                version = version,
            )

            MediaType.PLAYLIST -> Playlist(
                itemId = itemId,
                provider = provider,
                name = name,
                providerMappings = providerMappings,
                metadata = createMetadata(metadata),
                favorite = favorite,
                sortName = sortName,
                uri = uri,
                images = resolveImageInfo(image, metadata),
                isEditable = isEditable == true,
                isDynamic = isDynamic == true,
            )

            MediaType.FOLDER -> RecommendationFolder(
                itemId = itemId,
                provider = provider,
                name = name,
                uri = uri,
                images = resolveImageInfo(image, metadata),
                items = items?.let { createList(it) },
            )

            MediaType.PODCAST -> Podcast(
                itemId = itemId,
                provider = provider,
                name = name,
                providerMappings = providerMappings,
                metadata = createMetadata(metadata),
                favorite = favorite,
                sortName = sortName,
                uri = uri,
                images = resolveImageInfo(image, metadata),
            )

            MediaType.PODCAST_EPISODE -> PodcastEpisode(
                itemId = itemId,
                provider = provider,
                name = name,
                providerMappings = providerMappings,
                metadata = createMetadata(metadata),
                favorite = favorite,
                sortName = sortName,
                uri = uri,
                images = resolveImageInfo(image, metadata),
                duration = duration,
                podcast = podcast?.let { create(it) as? Podcast },
                fullyPlayed = fullyPlayed,
                resumePositionMs = resumePositionMs,
                releaseDate = metadata?.releaseDate,
                version = version,
            )

            MediaType.RADIO -> RadioStation(
                itemId = itemId,
                provider = provider,
                name = name,
                providerMappings = providerMappings,
                metadata = createMetadata(metadata),
                favorite = favorite,
                sortName = sortName,
                uri = uri,
                images = resolveImageInfo(image, metadata),
                version = version,
            )

            MediaType.AUDIOBOOK -> Audiobook(
                itemId = itemId,
                provider = provider,
                name = name,
                providerMappings = providerMappings,
                metadata = createMetadata(metadata),
                favorite = favorite,
                sortName = sortName,
                uri = uri,
                images = resolveImageInfo(image, metadata),
                duration = duration,
                authors = authors,
                narrators = narrators,
                chapters = metadata?.chapters?.map(::createChapter),
                fullyPlayed = fullyPlayed,
                resumePositionMs = resumePositionMs,
                version = version,
            )

            MediaType.GENRE -> Genre(
                itemId = itemId,
                provider = provider,
                name = name,
                providerMappings = providerMappings,
                metadata = createMetadata(metadata),
                favorite = favorite,
                sortName = sortName,
                uri = uri,
                images = resolveImageInfo(image, metadata),
            )

            MediaType.FLOW_STREAM,
            MediaType.ANNOUNCEMENT,
            MediaType.UNKNOWN,
            null,
                -> null
        }
    }

    fun createList(servers: List<ServerMediaItem>): List<AppMediaItem> =
        servers.mapNotNull { create(it) }

    fun createSearchResult(search: SearchResult): SearchResultData = SearchResultData(
        artists = search.artists.mapNotNull { create(it) as? Artist },
        albums = search.albums.mapNotNull { create(it) as? Album },
        tracks = search.tracks.mapNotNull { create(it) as? Track },
        playlists = search.playlists.mapNotNull { create(it) as? Playlist },
        audiobooks = search.audiobooks.mapNotNull { create(it) as? Audiobook },
        podcasts = search.podcasts.mapNotNull { create(it) as? Podcast },
        radios = search.radio.mapNotNull { create(it) as? RadioStation },
        genres = search.genres.mapNotNull { create(it) as? Genre },
    )

    private fun createMetadata(server: ServerMetadata?): Metadata? = server?.let {
        Metadata(
            explicit = it.explicit == true,
            images = it.images?.map(::createImageInfo).orEmpty(),
            releaseDate = it.releaseDate,
            chapters = it.chapters?.map(::createChapter).orEmpty(),
        )
    }

    private fun createChapter(server: ServerMediaItemChapter): Chapter =
        Chapter(
            position = server.position,
            name = server.name,
            start = server.start,
            end = server.end,
        )

    private fun createImageInfo(server: ServerMediaItemImage): ImageInfo =
        ImageInfo(
            type = ImageType.fromServer(server.type),
            path = server.path,
            isRemotelyAccessible = server.remotelyAccessible,
            provider = server.provider,
            url = buildImageUrl(server.path, server.provider, server.remotelyAccessible),
        )

    private fun buildImageUrl(
        path: String,
        provider: String,
        isRemotelyAccessible: Boolean,
    ): String? =
        path.takeIf { isRemotelyAccessible && it.startsWith("https") }
            ?: apiClient.serverBaseUrl.value?.let { server ->
                URLBuilder(server).apply {
                    appendPathSegments("imageproxy")
                    parameters.apply {
                        append("path", path.encodeURLQueryComponent())
                        append("provider", provider)
                        append("checksum", "")
                    }
                }.buildString()
            }

    private fun resolveImageInfo(
        image: ServerMediaItemImage?,
        metadata: ServerMetadata?,
    ) = buildMap {
        image?.let { put(ImageType.MAIN, createImageInfo(it)) }
        metadata?.images?.map { createImageInfo(it) }
            ?.forEach { if (get(it.type) == null) put(it.type, it) }
    }
}
