package io.music_assistant.client.data.model.client.items

import io.music_assistant.client.data.repository.MediaItemRepository
import io.music_assistant.client.data.repository.fetchMediaItems
import io.music_assistant.client.ui.compose.common.getOrEmptyList
import io.music_assistant.client.ui.compose.item.ItemList
import io.music_assistant.client.ui.compose.item.toRequests
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class FetchArtistItemsUseCase(private val mediaItemRepository: MediaItemRepository) {
    /**
     * Fetches every option in parallel and keeps only those with items, so an empty source is
     * never offered. The first kept option is the current one. Null when all are empty.
     */
    suspend fun run(options: List<ItemList>): ArtistItems? = coroutineScope {
        val nonEmpty = options
            .map { async { it to mediaItemRepository.fetchMediaItems(it.toRequests()).getOrEmptyList() } }
            .awaitAll()
            .filter { (_, items) -> items.isNotEmpty() }
        nonEmpty.firstOrNull()?.let { (itemList, items) ->
            ArtistItems(items, itemList, nonEmpty.map { it.first })
        }
    }

    data class ArtistItems(
        val items: List<AppMediaItem>,
        val itemList: ItemList,
        val options: List<ItemList>,
    )
}
