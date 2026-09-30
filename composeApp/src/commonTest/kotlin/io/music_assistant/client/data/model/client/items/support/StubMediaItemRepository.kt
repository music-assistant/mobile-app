package io.music_assistant.client.data.model.client.items.support

import io.music_assistant.client.api.Request
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.repository.MediaItemChange
import io.music_assistant.client.data.repository.MediaItemRepository
import io.music_assistant.client.data.repository.SearchResultData
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.serialization.json.JsonObject
import kotlin.collections.set

class StubMediaItemRepository : MediaItemRepository {
    private val itemsResults = mutableMapOf<Pair<String, JsonObject?>, Result<List<AppMediaItem>>>()

    override suspend fun fetchMediaItems(request: Request): Result<List<AppMediaItem>> {
        return itemsResults[request.key()] ?: error("No stub for: $request")
    }

    private val _itemChanges = MutableSharedFlow<MediaItemChange>()
    override val itemChanges: SharedFlow<MediaItemChange> = _itemChanges

    override suspend fun search(request: Request): Result<SearchResultData> {
        TODO("Not yet implemented")
    }

    override suspend fun fetchMediaItem(request: Request): Result<AppMediaItem?> {
        TODO("Not yet implemented")
    }

    override fun publishLocalChange(change: MediaItemChange) {
        TODO("Not yet implemented")
    }

    fun setItemsResult(request: Request, result: Result<List<AppMediaItem>>) {
        itemsResults[request.key()] = result
    }

    suspend fun fireChange(change: MediaItemChange) {
        _itemChanges.emit(change)
    }

    fun Request.key(): Pair<String, JsonObject?> {
        return Pair(this.command, this.args)
    }
}
