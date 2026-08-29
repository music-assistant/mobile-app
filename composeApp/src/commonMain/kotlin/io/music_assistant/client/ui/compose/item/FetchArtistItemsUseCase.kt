package io.music_assistant.client.ui.compose.item

import io.music_assistant.client.api.Request
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.Artist
import io.music_assistant.client.data.model.server.ProviderMapping
import io.music_assistant.client.data.repository.MediaItemRepository

class FetchArtistItemsUseCase(private val mediaItemRepository: MediaItemRepository) {
    suspend fun run(
        artist: Artist,
        request: (itemId: String, providerInstance: String) -> Request,
    ): ItemsWithMappings<AppMediaItem>? {
        if (artist.providerMappings.isNullOrEmpty()) {
            return null
        }

        for (mapping in artist.providerMappings) {
            val itemId = mapping.itemId
            val providerInstance = mapping.providerInstance
            val result = mediaItemRepository.fetchMediaItems(request(itemId, providerInstance))
            val items = result.getOrNull() ?: emptyList()
            if (items.isNotEmpty()) {
                return ItemsWithMappings(items, mapping)
            }
        }

        return ItemsWithMappings(emptyList(), artist.providerMappings.first())
    }

    data class ItemsWithMappings<T : AppMediaItem>(
        val items: List<T>,
        val mapping: ProviderMapping,
    )
}
