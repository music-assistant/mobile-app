package io.music_assistant.client.data.model.client.items

import io.music_assistant.client.api.Request
import io.music_assistant.client.data.model.client.AppMediaItemFixtures
import io.music_assistant.client.data.model.client.items.FetchArtistItemsUseCase.ArtistItems
import io.music_assistant.client.data.model.client.items.support.StubMediaItemRepository
import io.music_assistant.client.data.model.server.ProviderMapping
import io.music_assistant.client.ui.compose.item.ItemList
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FetchArtistItemsUseCaseTest {
    private val mediaItemRepository = StubMediaItemRepository()

    @Test
    fun `returns first provider's items when it has items`() = runTest {
        val provider1 = ProviderMapping("1", "niflheim", "niflheim-1")
        val provider2 = ProviderMapping("1", "muspelheim", "muspelheim-1")
        val artist = AppMediaItemFixtures.artist(providerMappings = listOf(provider1, provider2))
        val provider1Albums = listOf(AppMediaItemFixtures.album(artist = artist))
        val provider2Albums = listOf(AppMediaItemFixtures.album(artist = artist))

        mediaItemRepository.setItemsResult(
            request = Request.Artist.getAlbums(provider1.itemId, provider1.providerInstance),
            result = Result.success(provider1Albums),
        )

        mediaItemRepository.setItemsResult(
            request = Request.Artist.getAlbums(provider2.itemId, provider2.providerInstance),
            result = Result.success(provider2Albums),
        )

        val useCase = FetchArtistItemsUseCase(mediaItemRepository)
        val artistItems = useCase.run(artist) { ItemList.ArtistAlbums(it) }
        assertEquals(
            ArtistItems(
                items = provider1Albums,
                itemList = ItemList.ArtistAlbums(provider1),
                options = listOf(
                    ItemList.ArtistAlbums(provider1),
                    ItemList.ArtistAlbums(provider2),
                ),
            ),
            artistItems,
        )
    }

    @Test
    fun `returns first non-empty provider's items`() = runTest {
        val provider1 = ProviderMapping("1", "niflheim", "niflheim-1")
        val provider2 = ProviderMapping("1", "muspelheim", "muspelheim-1")
        val artist = AppMediaItemFixtures.artist(providerMappings = listOf(provider1, provider2))
        val provider2Albums = listOf(AppMediaItemFixtures.album(artist = artist))

        mediaItemRepository.setItemsResult(
            request = Request.Artist.getAlbums(provider1.itemId, provider1.providerInstance),
            result = Result.success(emptyList()),
        )

        mediaItemRepository.setItemsResult(
            request = Request.Artist.getAlbums(provider2.itemId, provider2.providerInstance),
            result = Result.success(provider2Albums),
        )

        val useCase = FetchArtistItemsUseCase(mediaItemRepository)
        val artistItems = useCase.run(artist) { ItemList.ArtistAlbums(it) }
        assertEquals(
            ArtistItems(
                items = provider2Albums,
                itemList = ItemList.ArtistAlbums(provider2),
                options = listOf(
                    ItemList.ArtistAlbums(provider1),
                    ItemList.ArtistAlbums(provider2),
                ),
            ),
            artistItems,
        )
    }

    @Test
    fun `returns null if all providers are empty`() = runTest {
        val provider1 = ProviderMapping("1", "niflheim", "niflheim-1")
        val provider2 = ProviderMapping("1", "muspelheim", "muspelheim-1")
        val artist = AppMediaItemFixtures.artist(providerMappings = listOf(provider1, provider2))

        mediaItemRepository.setItemsResult(
            request = Request.Artist.getAlbums(provider1.itemId, provider1.providerInstance),
            result = Result.success(emptyList()),
        )

        mediaItemRepository.setItemsResult(
            request = Request.Artist.getAlbums(provider2.itemId, provider2.providerInstance),
            result = Result.success(emptyList()),
        )

        val useCase = FetchArtistItemsUseCase(mediaItemRepository)
        val artistItems = useCase.run(artist) { ItemList.ArtistAlbums(it) }
        assertNull(artistItems)
    }

    @Test
    fun `returns null when artist has no provider mappings`() = runTest {
        val artist = AppMediaItemFixtures.artist(providerMappings = emptyList())

        val useCase = FetchArtistItemsUseCase(mediaItemRepository)
        val artistItems = useCase.run(artist) { ItemList.ArtistAlbums(it) }
        assertEquals(null, artistItems)
    }

    @Test
    fun `combines mappings if they are the same instance`(): TestResult = runTest {
        val providerDomain = "niflheim"
        val mapping1 = ProviderMapping("1", providerDomain, "niflheim-1")
        val mapping2 = ProviderMapping("2", providerDomain, "niflheim-1")
        val artist = AppMediaItemFixtures.artist(providerMappings = listOf(mapping1, mapping2))
        val mapping1Albums = listOf(AppMediaItemFixtures.album(artist = artist))
        val mapping2Albums = listOf(AppMediaItemFixtures.album(artist = artist))

        mediaItemRepository.setItemsResult(
            request = Request.Artist.getAlbums(mapping1.itemId, mapping1.providerInstance),
            result = Result.success(mapping1Albums),
        )

        mediaItemRepository.setItemsResult(
            request = Request.Artist.getAlbums(mapping2.itemId, mapping2.providerInstance),
            result = Result.success(mapping2Albums),
        )

        val useCase = FetchArtistItemsUseCase(mediaItemRepository)
        val artistItems = useCase.run(artist) { ItemList.ArtistAlbums(it) }

        val expectedItemList = ItemList.ArtistAlbums(providerMappings = listOf(mapping1, mapping2))
        assertEquals(
            ArtistItems(
                items = mapping1Albums + mapping2Albums,
                itemList = expectedItemList,
                options = listOf(expectedItemList),
            ),
            artistItems,
        )
    }
}
