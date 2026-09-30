package io.music_assistant.client.ui.compose.item

import io.music_assistant.client.api.Request
import io.music_assistant.client.data.model.client.MediaType
import io.music_assistant.client.data.model.client.SubItemContext
import io.music_assistant.client.data.model.server.ProviderMapping
import io.music_assistant.client.data.model.server.ServerMediaItem
import kotlinx.serialization.Serializable

@Serializable
sealed interface ItemList {
    val mediaType: MediaType
    val providerDomain: String

    /** Keys the remembered sort, so each artist list keeps its own. */
    val sortContext: SubItemContext

    @Serializable
    data class ArtistAlbums(val providerMappings: List<ProviderMapping>) : ItemList {
        override val mediaType: MediaType = MediaType.ALBUM
        override val providerDomain: String = providerMappings.first().providerDomain
        override val sortContext: SubItemContext get() = SubItemContext.ARTIST_ALL_ALBUMS

        constructor(providerMapping: ProviderMapping) : this(providerMappings = listOf(providerMapping))
    }

    @Serializable
    data class ArtistTopTracks(val providerMappings: List<ProviderMapping>) : ItemList {
        override val mediaType: MediaType = MediaType.TRACK
        override val providerDomain: String = providerMappings.first().providerDomain
        override val sortContext: SubItemContext get() = SubItemContext.ARTIST_TOP_TRACKS
    }

    @Serializable
    data class ArtistLibrary(val artistId: String) : ItemList {
        override val mediaType: MediaType = MediaType.ALBUM
        override val providerDomain: String = ServerMediaItem.LIBRARY_PROVIDER
        override val sortContext: SubItemContext get() = SubItemContext.ARTIST_LIBRARY_ALBUMS
    }
}

fun ItemList.toRequests(): List<Request> {
    return when (this) {
        is ItemList.ArtistAlbums -> {
            this.providerMappings.map {
                Request.Artist.getAlbums(it.itemId, it.providerInstance)
            }
        }

        is ItemList.ArtistTopTracks -> {
            this.providerMappings.map {
                Request.Artist.getTopTracks(it.itemId, it.providerInstance)
            }
        }

        is ItemList.ArtistLibrary -> listOf(
            Request.Artist.getAlbums(
                this.artistId,
                ServerMediaItem.LIBRARY_PROVIDER,
            ),
        )
    }
}
