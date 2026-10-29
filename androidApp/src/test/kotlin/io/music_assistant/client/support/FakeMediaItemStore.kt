package io.music_assistant.client.support

import io.music_assistant.client.data.model.client.MediaType
import io.music_assistant.client.data.model.server.ServerMediaItem
import io.music_assistant.client.utils.UniqueIdGenerator

internal class FakeMediaItemStore {
    private val uniqueIdGenerator = UniqueIdGenerator()
    private val mediaItems = mutableSetOf<ServerMediaItem>()
    private val libraryIds = mutableMapOf<Pair<String, String>, String>()
    private val playlistItems = mutableMapOf<String, List<String>>()
    private val topTracks = mutableMapOf<Pair<String, String>, List<String>>()

    // The audiobooks an author or narrator is credited on, by the artist's global id.
    private val artistAudiobooks = mutableMapOf<Pair<String, String>, List<String>>()

    // The server's `content_type` of a genre; a genre without an entry is in the music taxonomy.
    private val genreContentTypes = mutableMapOf<Pair<String, String>, String>()

    /**
     * [artistType] narrows artists to one `artist_type`. [genreContentType] narrows genres to one
     * taxonomy; `"music"` is the one the server stores as null.
     */
    fun query(
        query: String? = null,
        mediaType: MediaType? = null,
        inLibraryOnly: Boolean = false,
        favoritesOnly: Boolean = false,
        artistType: String? = null,
        genreContentType: String? = null,
    ): List<ServerMediaItem> {
        return mediaItems
            .filter { mediaType == null || it.mediaType == mediaType.serverValue }
            .filter { !inLibraryOnly || it.isInLibrary() }
            .filter { !favoritesOnly || it.favorite ?: false }
            .filter { query == null || it.name.contains(query, ignoreCase = true) }
            .filter { artistType == null || artistTypeOf(it) == artistType }
            .filter { genreContentType == null || genreContentTypeOf(it) == genreContentType }
    }

    /** Mirrors `library_artist_types`: the distinct types of the library artists. */
    fun libraryArtistTypes(): List<String> =
        query(mediaType = MediaType.ARTIST, inLibraryOnly = true).map(::artistTypeOf).distinct()

    fun setGenreContentType(genre: ServerMediaItem, contentType: String) {
        genreContentTypes[genre.globalId()] = contentType
    }

    // An artist without a type is a singer, like on the server.
    private fun artistTypeOf(artist: ServerMediaItem): String = artist.artistType ?: SINGER

    fun setArtistAudiobooks(artist: ServerMediaItem, vararg audiobooks: ServerMediaItem) {
        artistAudiobooks[artist.globalId()] = audiobooks.map { it.itemId }
    }

    fun getAudiobooksByArtist(artist: ServerMediaItem): List<ServerMediaItem> {
        return mediaItems
            .filter { it.mediaType == MediaType.AUDIOBOOK.serverValue }
            .filter { artistAudiobooks[artist.globalId()]?.contains(it.itemId) ?: false }
    }

    /**
     * Mirrors `artist_audiobooks` for a library artist: the audiobooks of every provider artist
     * matched to it, narrowed to the library ones by [inLibraryOnly].
     */
    fun getAudiobooksByLibraryArtist(libraryId: String, inLibraryOnly: Boolean): List<ServerMediaItem> {
        return libraryIds.entries
            .filter { it.value == libraryId }
            .map { it.key }
            .flatMap { globalId ->
                mediaItems.filter { it.mediaType == MediaType.ARTIST.serverValue && it.globalId() == globalId }
            }
            .flatMap { getAudiobooksByArtist(it) }
            .filter { !inLibraryOnly || it.isInLibrary() }
            .distinct()
    }

    private fun genreContentTypeOf(genre: ServerMediaItem): String = genreContentTypes[genre.globalId()] ?: MUSIC

    fun getEpisodesByPodcast(podcast: ServerMediaItem): List<ServerMediaItem> {
        return mediaItems
            .filter { it.mediaType == MediaType.PODCAST_EPISODE.serverValue }
            .filter { it.podcast == podcast }
    }

    fun getItem(itemId: String, provider: String): ServerMediaItem {
        return if (provider == "library") {
            val globalId = libraryIds.entries.first { it.value == itemId }.key
            mediaItems.first { it.globalId() == globalId }
        } else {
            mediaItems
                .filter { it.isInProvider(provider) }
                .first { it.itemId == itemId }
        }
    }

    fun getByUri(uri: String): ServerMediaItem? {
        return mediaItems.find { it.uri == uri }
    }

    fun getAlbumsByArtist(artist: ServerMediaItem, provider: String): List<ServerMediaItem> {
        return mediaItems
            .filter { it.isInProvider(provider) }
            .filter { it.mediaType == MediaType.ALBUM.serverValue }
            .filter { it.artists?.contains(artist) ?: false }
    }

    fun getTracksByAlbum(album: ServerMediaItem): List<ServerMediaItem> {
        return mediaItems
            .filter { it.mediaType == MediaType.TRACK.serverValue }
            .filter { it.album == album }
    }

    fun getTracksByPlaylist(playlist: ServerMediaItem): List<ServerMediaItem> {
        return mediaItems
            .filter { it.mediaType == MediaType.TRACK.serverValue }
            .filter { playlistItems[playlist.itemId]?.contains(it.itemId) ?: false }
    }

    fun getTracksByArtist(artist: ServerMediaItem, topOnly: Boolean = false): List<ServerMediaItem> {
        return mediaItems
            .filter { it.mediaType == MediaType.TRACK.serverValue }
            .filter { it.artists?.contains(artist) ?: false }
            .filter { !topOnly || topTracks[artist.globalId()]?.contains(it.itemId) ?: false }
    }

    /**
     * Mirrors the server's library listing: the top tracks of every provider artist matched to
     * the library artist, narrowed to one provider instance by [providerFilter].
     */
    fun getTopTracksByLibraryArtist(libraryId: String, providerFilter: String?): List<ServerMediaItem> {
        return libraryIds.entries
            .filter { it.value == libraryId }
            .map { it.key }
            .filter { (instance, _) -> providerFilter == null || instance == providerFilter }
            .flatMap { globalId ->
                mediaItems.filter { it.mediaType == MediaType.ARTIST.serverValue && it.globalId() == globalId }
            }
            .flatMap { getTracksByArtist(it, topOnly = true) }
            .distinct()
    }

    fun addItems(vararg items: ServerMediaItem) {
        val itemsToAdd = items.toList()
        itemsToAdd.forEach { item ->
            item.artists?.let {
                mediaItems.addAll(it)
            }

            item.album?.let {
                mediaItems.add(it)
            }
        }

        mediaItems.addAll(itemsToAdd)
    }

    fun addToLibrary(item: ServerMediaItem) {
        libraryIds[item.globalId()] = uniqueIdGenerator.nextInt().toString()

        when (MediaType.fromServer(item.mediaType)) {
            MediaType.ALBUM -> {
                mediaItems.filter { it.album == this }.forEach {
                    addToLibrary(it)
                }

                item.artists?.forEach {
                    addToLibrary(it)
                }
            }

            MediaType.PLAYLIST -> Unit
            MediaType.ARTIST -> Unit
            MediaType.TRACK -> Unit
            MediaType.RADIO -> Unit
            MediaType.AUDIOBOOK -> Unit
            MediaType.PODCAST -> Unit
            MediaType.PODCAST_EPISODE -> Unit
            MediaType.GENRE -> Unit
            MediaType.FOLDER -> Unit
            MediaType.COLLECTION -> Unit
            MediaType.FLOW_STREAM -> Unit
            MediaType.ANNOUNCEMENT -> Unit
            MediaType.SOUND_EFFECT -> Unit
            MediaType.AUDIO_SOURCE -> Unit
            MediaType.UNKNOWN -> Unit
            null -> Unit
        }
    }

    fun matchItem(libraryItem: ServerMediaItem, providerItem: ServerMediaItem) {
        val libraryId = libraryIds[libraryItem.globalId()]
        require(libraryId != null) { "Can't add match for item not in library!" }
        libraryIds[providerItem.globalId()] = libraryId
    }

    fun setPlaylist(playlist: ServerMediaItem, vararg tracks: ServerMediaItem) {
        playlistItems[playlist.itemId] = tracks.map { it.itemId }
    }

    fun setTopTracks(artist: ServerMediaItem, vararg tracks: ServerMediaItem) {
        topTracks[artist.globalId()] = tracks.map { it.itemId }
    }

    fun enrichLibraryItem(item: ServerMediaItem): ServerMediaItem {
        val libraryId = libraryIds[item.globalId()]

        return if (libraryId != null) {
            val matchedItems = libraryIds.entries
                .filter { it.value == libraryId }
                .map { it.key }
                .flatMap { globalId ->
                    mediaItems.filter { it.globalId() == globalId }
                }

            val providerMappings = matchedItems.map { it.providerMappings!![0] }

            item.copy(
                itemId = libraryId,
                provider = "library",
                providerMappings = providerMappings,
            )
        } else {
            item
        }
    }

    private fun ServerMediaItem.isInProvider(provider: String): Boolean {
        return if (provider == ServerMediaItem.LIBRARY_PROVIDER) {
            isInLibrary()
        } else {
            this.providerMappings?.any { mapping ->
                (mapping.providerInstance == provider || mapping.providerDomain == provider) && mapping.itemId == itemId
            } ?: false
        }
    }

    private fun ServerMediaItem.isInLibrary(): Boolean {
        return libraryIds.containsKey(this.globalId())
    }
}

private const val SINGER = "singer"
private const val MUSIC = "music"

private fun ServerMediaItem.globalId(): Pair<String, String> {
    val providerMapping = this.providerMappings!![0]
    return Pair(providerMapping.providerInstance, providerMapping.itemId)
}
