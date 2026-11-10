package io.music_assistant.client.data.model.client.items

import io.music_assistant.client.data.repository.MediaItemRepository
import io.music_assistant.client.data.repository.fetchMediaItems
import io.music_assistant.client.ui.compose.common.getOrEmptyList
import io.music_assistant.client.ui.compose.item.ItemList
import io.music_assistant.client.ui.compose.item.select
import io.music_assistant.client.ui.compose.item.toRequests
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class FetchArtistItemsUseCase(private val mediaItemRepository: MediaItemRepository) {
    /**
     * Fetches every option in parallel and keeps only those with items, so an empty source is
     * never offered. The first kept option is the current one. Null when all are empty.
     */
    suspend fun run(options: List<ItemList>): ArtistItems? = run(options, listOf { it }).single()

    /**
     * [run] for several rows that list the same [sources]: each source is fetched once. A row
     * maps a source to its own option, and keeps the options whose [ItemList.select] is not empty.
     */
    suspend fun run(sources: List<ItemList>, rows: List<(ItemList) -> ItemList>): List<ArtistItems?> = coroutineScope {
        val fetched = sources
            .map { async { mediaItemRepository.fetchMediaItems(it.toRequests()).getOrEmptyList() } }
            .awaitAll()
        rows.map { row ->
            val nonEmpty = sources.zip(fetched)
                .map { (source, items) -> row(source) to items }
                .filter { (itemList, items) -> itemList.select(items).isNotEmpty() }
            nonEmpty.firstOrNull()?.let { (itemList, items) ->
                ArtistItems(items, itemList, nonEmpty.map { it.first })
            }
        }
    }

    /** [items] are all items that the requests of [itemList] answer, before [ItemList.select]. */
    data class ArtistItems(
        val items: List<AppMediaItem>,
        val itemList: ItemList,
        val options: List<ItemList>,
    )
}
