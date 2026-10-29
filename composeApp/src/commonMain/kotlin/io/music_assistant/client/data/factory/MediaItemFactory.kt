package io.music_assistant.client.data.factory

import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.data.model.client.AlbumType
import io.music_assistant.client.data.model.client.ArtistType
import io.music_assistant.client.data.model.client.Chapter
import io.music_assistant.client.data.model.client.ImageInfo
import io.music_assistant.client.data.model.client.ImageType
import io.music_assistant.client.data.model.client.MediaType
import io.music_assistant.client.data.model.client.Metadata
import io.music_assistant.client.data.model.client.items.Album
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.Artist
import io.music_assistant.client.data.model.client.items.AudioSource
import io.music_assistant.client.data.model.client.items.Audiobook
import io.music_assistant.client.data.model.client.items.Genre
import io.music_assistant.client.data.model.client.items.MediaCollection
import io.music_assistant.client.data.model.client.items.Playlist
import io.music_assistant.client.data.model.client.items.PlaylistAccess
import io.music_assistant.client.data.model.client.items.PlaylistSharing
import io.music_assistant.client.data.model.client.items.Podcast
import io.music_assistant.client.data.model.client.items.PodcastEpisode
import io.music_assistant.client.data.model.client.items.RadioStation
import io.music_assistant.client.data.model.client.items.RecommendationFolder
import io.music_assistant.client.data.model.client.items.SoundEffect
import io.music_assistant.client.data.model.client.items.Track
import io.music_assistant.client.data.model.server.SearchResult
import io.music_assistant.client.data.model.server.ServerMediaItem
import io.music_assistant.client.data.model.server.ServerMediaItemChapter
import io.music_assistant.client.data.model.server.ServerMediaItemImage
import io.music_assistant.client.data.model.server.ServerMetadata
import io.music_assistant.client.data.model.server.ServerPlaylistAccess
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
                artistType = ArtistType.fromServer(artistType),
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
                albumType = AlbumType.fromServer(albumType),
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
                isPlayable = isPlayable == true,
                artists = artists?.mapNotNull { create(it) as? Artist } ?: emptyList(),
                album = album?.let { create(it) as? Album },
                discNumber = discNumber,
                trackNumber = trackNumber,
                position = intPosition,
                version = version,
                source = server,
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
                supportedMediaTypes = supportedMediatypes?.mapNotNull(MediaType::fromServer)?.toSet(),
                owner = owner,
                access = access?.let(::createPlaylistAccess),
            )

            MediaType.FOLDER -> RecommendationFolder(
                itemId = itemId,
                provider = provider,
                name = name,
                uri = uri,
                images = resolveImageInfo(image, metadata),
                items = items?.let { createList(it) },
                path = path,
                isPlayable = isPlayable == true,
            )

            MediaType.COLLECTION -> {
                val children = items?.let { createList(it) }.orEmpty()
                MediaCollection(
                    itemId = itemId,
                    provider = provider,
                    name = name,
                    uri = uri,
                    // The server gives a collection no artwork; borrow the first child's.
                    images = resolveImageInfo(image, metadata)
                        .ifEmpty { children.firstOrNull { it.images.isNotEmpty() }?.images.orEmpty() },
                    items = children,
                )
            }

            MediaType.PODCAST -> Podcast(
                itemId = itemId,
                provider = provider,
                name = name,
                providerMappings = providerMappings,
                metadata = createMetadata(metadata),
                favorite = favorite,
                sortName = sortName,
                // The server's feed parser puts the feed's website link in `uri`; build the MA uri instead.
                uri = "$provider://${MediaType.PODCAST.serverValue}/$itemId",
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
                isPlayable = isPlayable == true,
                podcast = podcast?.let { create(it) as? Podcast },
                fullyPlayed = fullyPlayed,
                resumePositionMs = resumePositionMs,
                releaseDate = metadata?.releaseDate,
                episodeNumber = episodeNumber,
                season = season,
                version = version,
                position = intPosition,
                source = server,
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
                isPlayable = isPlayable == true,
                isDynamic = isDynamic == true,
                isEndless = isEndless,
                position = intPosition,
            )

            MediaType.SOUND_EFFECT -> SoundEffect(
                itemId = itemId,
                provider = provider,
                name = name,
                providerMappings = providerMappings,
                metadata = createMetadata(metadata),
                sortName = sortName,
                images = resolveImageInfo(image, metadata),
                duration = duration,
                uri = uri,
            )

            MediaType.AUDIO_SOURCE -> AudioSource(
                itemId = itemId,
                provider = provider,
                name = name,
                providerMappings = providerMappings,
                metadata = createMetadata(metadata),
                sortName = sortName,
                uri = uri,
                images = resolveImageInfo(image, metadata),
                isPlayable = isPlayable == true,
                canPlayPause = canPlayPause == true,
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
                isPlayable = isPlayable == true,
                authors = authors,
                narrators = narrators,
                publisher = publisher,
                chapters = metadata?.chapters?.map(::createChapter),
                fullyPlayed = fullyPlayed,
                resumePositionMs = resumePositionMs,
                version = version,
                position = intPosition,
                source = server,
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

    private val ServerMediaItem.intPosition: Int?
        get() = position?.takeIf { it in 0L..Int.MAX_VALUE.toLong() }?.toInt()

    private fun createMetadata(server: ServerMetadata?): Metadata? = server?.let {
        Metadata(
            explicit = it.explicit == true,
            images = it.images?.map(::createImageInfo).orEmpty(),
            releaseDate = it.releaseDate,
            chapters = it.chapters?.map(::createChapter).orEmpty(),
            lyrics = it.lyrics,
            lrcLyrics = it.lrcLyrics,
            hasTranscript = it.hasTranscript,
            description = it.description,
            review = it.review,
        )
    }

    private fun createPlaylistAccess(server: ServerPlaylistAccess): PlaylistAccess =
        PlaylistAccess(
            owner = server.owner,
            sharing = PlaylistSharing.fromServer(server.sharing),
            sharedUsers = server.sharedUsers,
            collaborative = server.collaborative,
        )

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
            url = apiClient.resolveImageUrl(server.path, server.provider, server.remotelyAccessible, server.proxyId),
        )

    private fun resolveImageInfo(
        image: ServerMediaItemImage?,
        metadata: ServerMetadata?,
    ) = buildMap {
        image?.let { put(ImageType.MAIN, createImageInfo(it)) }
        metadata?.images?.map { createImageInfo(it) }
            ?.forEach { if (get(it.type) == null) put(it.type, it) }
    }
}
