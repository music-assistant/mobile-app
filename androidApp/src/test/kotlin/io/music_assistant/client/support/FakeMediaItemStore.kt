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

    fun query(
        query: String? = null,
        mediaType: MediaType? = null,
        inLibraryOnly: Boolean = false,
        favoritesOnly: Boolean = false,
    ): List<ServerMediaItem> {
        return mediaItems
            .filter { mediaType == null || it.mediaType == mediaType.serverValue }
            .filter { !inLibraryOnly || it.isInLibrary() }
            .filter { !favoritesOnly || it.favorite ?: false }
            .filter { query == null || it.name.contains(query, ignoreCase = true) }
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
            MediaType.FLOW_STREAM -> Unit
            MediaType.ANNOUNCEMENT -> Unit
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

private fun ServerMediaItem.globalId(): Pair<String, String> {
    val providerMapping = this.providerMappings!![0]
    return Pair(providerMapping.providerInstance, providerMapping.itemId)
}
