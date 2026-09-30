package io.music_assistant.client.data.model.client.items

import io.music_assistant.client.api.Request
import io.music_assistant.client.data.model.client.AppMediaItemFixtures
import io.music_assistant.client.data.model.client.items.support.StubMediaItemRepository
import io.music_assistant.client.data.repository.MediaItemChange
import io.music_assistant.client.ui.compose.common.DataState
import io.music_assistant.client.ui.compose.common.StaleReason
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

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
        val items = listOf(AppMediaItemFixtures.album())
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
        val items = listOf(AppMediaItemFixtures.album())

        val mediator = MediaItemDataMediator(initial = DataState.Loading(), mediaItemRepository)
        mediator.set(items, request)
        assertEquals(DataState.Data(items), mediator.asFlow().value)
    }

    @Test
    fun `setStale sets data to stale`() {
        val mediator = MediaItemDataMediator(initial = DataState.Loading(), mediaItemRepository)
        val items = listOf(AppMediaItemFixtures.album())
        mediator.set(items, Request.Album.listLibrary())

        mediator.setStale(0L, StaleReason.RECONNECTING)
        assertEquals(DataState.Stale(items, 0L, StaleReason.RECONNECTING), mediator.asFlow().value)
    }

    @Test
    fun `setStale does not change data when it is still loading`() {
        val mediator = MediaItemDataMediator(initial = DataState.Loading(), mediaItemRepository)
        mediator.setStale(0L, StaleReason.RECONNECTING)
        assertIs<DataState.Loading<List<AppMediaItem>>>(mediator.asFlow().value)
    }

    @Test
    fun `updateOn updates stored items when Updated happens`() = runTest {
        val request = Request.Album.listLibrary()
        val item = AppMediaItemFixtures.album()
        val items = listOf(item)

        val mediator = MediaItemDataMediator(initial = DataState.Loading(), mediaItemRepository)
            .updateOn(unconfinedScope)

        mediator.set(items, request)

        val updatedItem = item.copy(name = "changed!")
        mediaItemRepository.fireChange(MediaItemChange.Updated(updatedItem))
        assertEquals(DataState.Data(listOf(updatedItem)), mediator.asFlow().value)
    }

    @Test
    fun `updateOn updates stale stored items when Updated happens`() = runTest {
        val request = Request.Album.listLibrary()
        val item = AppMediaItemFixtures.album()
        val items = listOf(item)

        val mediator = MediaItemDataMediator(initial = DataState.Loading(), mediaItemRepository)
            .updateOn(unconfinedScope)

        mediator.set(items, request)
        mediator.setStale(0L, StaleReason.RECONNECTING)

        val updatedItem = item.copy(name = "changed!")
        mediaItemRepository.fireChange(MediaItemChange.Updated(updatedItem))
        assertEquals(
            DataState.Stale(listOf(updatedItem), 0L, StaleReason.RECONNECTING),
            mediator.asFlow().value,
        )
    }

    @Test
    fun `updateOn reloads items when Added happens`() = runTest {
        val request = Request.Album.listLibrary()
        val item = AppMediaItemFixtures.album(itemId = "1", name = "Original Album")
        val items = listOf(item)

        val mediator = MediaItemDataMediator(initial = DataState.Loading(), mediaItemRepository)
            .updateOn(unconfinedScope)

        mediator.set(items, request)

        val newItem = AppMediaItemFixtures.album(itemId = "2", name = "New Album")
        // Add extra item to ensure we're reloading from MediaItemRepository
        val updatedItems = listOf(item, newItem, AppMediaItemFixtures.album())
        mediaItemRepository.setItemsResult(request, Result.success(updatedItems))

        mediaItemRepository.fireChange(MediaItemChange.Added(newItem))
        assertEquals(DataState.Data(updatedItems), mediator.asFlow().value)
    }

    @Test
    fun `updateOn reloads items when Deleted happens`() = runTest {
        val request = Request.Album.listLibrary()
        val item1 = AppMediaItemFixtures.album()
        val item2 = AppMediaItemFixtures.album()
        val items = listOf(item1, item2)

        val mediator = MediaItemDataMediator(initial = DataState.Loading(), mediaItemRepository)
            .updateOn(unconfinedScope)

        mediator.set(items, request)

        // Add extra item to ensure we're reloading from MediaItemRepository
        val remainingItems = listOf(item1, AppMediaItemFixtures.album())
        mediaItemRepository.setItemsResult(request, Result.success(remainingItems))

        mediaItemRepository.fireChange(MediaItemChange.Deleted(item2))
        assertEquals(DataState.Data(remainingItems), mediator.asFlow().value)
    }
}
