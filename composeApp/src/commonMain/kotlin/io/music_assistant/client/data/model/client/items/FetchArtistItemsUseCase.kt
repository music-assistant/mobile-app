package io.music_assistant.client.data.model.client.items

import io.music_assistant.client.data.model.server.ProviderMapping
import io.music_assistant.client.data.repository.MediaItemRepository
import io.music_assistant.client.data.repository.fetchMediaItems
import io.music_assistant.client.ui.compose.common.getOrEmptyList
import io.music_assistant.client.ui.compose.item.ItemList
import io.music_assistant.client.ui.compose.item.toRequests

class FetchArtistItemsUseCase(private val mediaItemRepository: MediaItemRepository) {
    suspend fun run(
        artist: Artist,
        itemListBuilder: (List<ProviderMapping>) -> ItemList,
    ): ArtistItems? {
        if (artist.providerMappings.isNullOrEmpty()) {
            return null
        }

        val providers = artist.providerMappings.groupBy { it.providerInstance }.map { it.value }
        val itemLists = providers.map { itemListBuilder(it) }
        for (itemList in itemLists) {
            val items = mediaItemRepository.fetchMediaItems(itemList.toRequests()).getOrEmptyList()
            if (items.isNotEmpty()) {
                return ArtistItems(items, itemList, itemLists)
            }
        }

        return null
    }

    data class ArtistItems(
        val items: List<AppMediaItem>,
        val itemList: ItemList,
        val options: List<ItemList>,
    )
}
