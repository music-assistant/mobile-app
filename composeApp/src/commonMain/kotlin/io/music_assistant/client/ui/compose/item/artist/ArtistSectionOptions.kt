package io.music_assistant.client.ui.compose.item.artist

import io.music_assistant.client.data.model.client.items.Artist
import io.music_assistant.client.data.model.server.ProviderMapping
import io.music_assistant.client.data.model.server.ServerProviderInstance
import io.music_assistant.client.ui.compose.item.ItemList

internal const val ARTIST_TOPTRACKS_FEATURE = "artist_toptracks"

/** One group per provider instance: an instance can hold several mappings of the same artist. */
internal fun Artist.providerGroups(): List<List<ProviderMapping>> =
    providerMappings.orEmpty().groupBy { it.providerInstance }.values.toList()

/**
 * Top-tracks sources, as the web offers them. A library artist gets the merged library listing
 * first, then each mapped music provider and each metadata or plugin provider that serves top
 * tracks. A provider artist only has its own provider.
 */
internal fun Artist.topTracksOptions(providers: List<ServerProviderInstance>): List<ItemList> {
    if (!isInLibrary) return providerGroups().map { ItemList.ArtistTopTracks(it) }

    val mapped = providerMappings.orEmpty().map { it.providerInstance }.toSet()
    val capable = providers.filter { it.available && ARTIST_TOPTRACKS_FEATURE in it.supportedFeatures }
    val music = capable.filter { it.type == "music" && it.instanceId in mapped }
    val discovery = capable.filter { it.type == "metadata" || it.type == "plugin" }
    return listOf(ItemList.ArtistLibraryTopTracks(itemId)) +
        (music + discovery).map { ItemList.ArtistLibraryTopTracks(itemId, it.instanceId) }
}
