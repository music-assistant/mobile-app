package io.music_assistant.client.data.model.client.items

import io.music_assistant.client.api.Request
import io.music_assistant.client.data.model.client.AppMediaItemFixtures
import io.music_assistant.client.data.repository.MediaItemChange
import io.music_assistant.client.data.repository.MediaItemRepository
import io.music_assistant.client.data.repository.SearchResultData
import io.music_assistant.client.ui.compose.common.DataState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class MediaItemDataMediatorTest {
    private val mediaItemRepository = StubMediaItemRepository()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val unconfinedTestDispatcher = UnconfinedTestDispatcher()
    private val unconfinedScope = CoroutineScope(unconfinedTestDispatcher)

    @AfterTest
    fun teardown() {
        unconfinedScope.cancel()
    }

    @Test
    fun `set retrieves and stores items`() = runTest {
        val request = Request.Album.listLibrary()
        val items = listOf<AppMediaItem>(AppMediaItemFixtures.album())
        mediaItemRepository.setItemsResult(
            request,
            Result.success(items),
        )

        val mediator = MediaItemDataMediator(initial = DataState.Loading(), mediaItemRepository)
        mediator.set(request)
        assertEquals(DataState.Data(items), mediator.asFlow().value)
    }

    @Test
    fun `set with items stores items`() = runTest {
        val request = Request.Album.listLibrary()
        val items = listOf<AppMediaItem>(AppMediaItemFixtures.album())

        val mediator = MediaItemDataMediator(initial = DataState.Loading(), mediaItemRepository)
        mediator.set(items, request)
        assertEquals(DataState.Data(items), mediator.asFlow().value)
    }

    @Test
    fun `updateOn updates stored items when Updated happens`() = runTest {
        val request = Request.Album.listLibrary()
        val item = AppMediaItemFixtures.album()
        val items = listOf<AppMediaItem>(item)

        val mediator = MediaItemDataMediator(initial = DataState.Loading(), mediaItemRepository)
            .updateOn(unconfinedScope)

        mediator.set(items, request)

        val updatedItem = item.copy(name = "changed!")
        mediaItemRepository.fireChange(MediaItemChange.Updated(updatedItem))
        assertEquals(DataState.Data(listOf<AppMediaItem>(updatedItem)), mediator.asFlow().value)
    }

    @Test
    fun `updateOn reloads items when Added happens`() = runTest {
        val request = Request.Album.listLibrary()
        val item = AppMediaItemFixtures.album(itemId = "1", name = "Original Album")
        val items = listOf<AppMediaItem>(item)

        val mediator = MediaItemDataMediator(initial = DataState.Loading(), mediaItemRepository)
            .updateOn(unconfinedScope)

        mediator.set(items, request)

        val newItem = AppMediaItemFixtures.album(itemId = "2", name = "New Album")
        // Add extra item to ensure we're reloading from MediaItemRepository
        val updatedItems = listOf<AppMediaItem>(item, newItem, AppMediaItemFixtures.album())
        mediaItemRepository.setItemsResult(request, Result.success(updatedItems))

        mediaItemRepository.fireChange(MediaItemChange.Added(newItem))
        assertEquals(DataState.Data(updatedItems), mediator.asFlow().value)
    }

    @Test
    fun `updateOn reloads items when Deleted happens`() = runTest {
        val request = Request.Album.listLibrary()
        val item1 = AppMediaItemFixtures.album()
        val item2 = AppMediaItemFixtures.album()
        val items = listOf<AppMediaItem>(item1, item2)

        val mediator = MediaItemDataMediator(initial = DataState.Loading(), mediaItemRepository)
            .updateOn(unconfinedScope)

        mediator.set(items, request)

        // Add extra item to ensure we're reloading from MediaItemRepository
        val remainingItems = listOf<AppMediaItem>(item1, AppMediaItemFixtures.album())
        mediaItemRepository.setItemsResult(request, Result.success(remainingItems))

        mediaItemRepository.fireChange(MediaItemChange.Deleted(item2))
        assertEquals(DataState.Data(remainingItems), mediator.asFlow().value)
    }
}

private class StubMediaItemRepository : MediaItemRepository {
    private val itemsResults = mutableMapOf<Request, Result<List<AppMediaItem>>>()

    override suspend fun fetchMediaItems(request: Request): Result<List<AppMediaItem>> {
        return itemsResults[request]!!
    }

    private val _itemChanges = MutableSharedFlow<MediaItemChange>()
    override val itemChanges: SharedFlow<MediaItemChange> = _itemChanges

    override suspend fun search(request: Request): Result<SearchResultData> {
        TODO("Not yet implemented")
    }

    override suspend fun fetchMediaItem(request: Request): Result<AppMediaItem?> {
        TODO("Not yet implemented")
    }

    override fun supportsRecommendationRowItems(): Boolean {
        TODO("Not yet implemented")
    }

    override fun publishLocalChange(change: MediaItemChange) {
        TODO("Not yet implemented")
    }

    fun setItemsResult(request: Request, result: Result<List<AppMediaItem>>) {
        itemsResults[request] = result
    }

    suspend fun fireChange(change: MediaItemChange) {
        _itemChanges.emit(change)
    }
}
