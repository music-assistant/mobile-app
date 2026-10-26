package io.music_assistant.client.data.model.client.items

import io.music_assistant.client.data.model.client.AppMediaItemFixtures
import io.music_assistant.client.data.model.server.ProviderMapping
import io.music_assistant.client.data.model.server.RefreshItemAccess
import io.music_assistant.client.data.model.server.ServerProviderInstance
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The refresh entry follows the web frontend: availability decides between the two labels. */
class RefreshItemActionTest {
    private val spotify = ServerProviderInstance(instanceId = "spotify--1", name = "Spotify", domain = "spotify")

    private fun artist(vararg mappings: ProviderMapping) =
        AppMediaItemFixtures.artist(providerMappings = mappings.toList())

    private fun mapping(instance: String = spotify.instanceId, available: Boolean = true) =
        ProviderMapping("a1", "spotify", instance, available)

    @Test
    fun availableItemGetsRefreshForAManagerOnly() {
        val item = artist(mapping())

        assertEquals(RefreshItemAction.REFRESH, item.refreshItemAction(RefreshItemAccess.ALL, listOf(spotify)))
        assertNull(item.refreshItemAction(RefreshItemAccess.FIND_ONLY, listOf(spotify)))
    }

    @Test
    fun unavailableMappingGetsFind() {
        val item = artist(mapping(available = false))

        assertEquals(
            RefreshItemAction.FIND_ON_MUSIC_SERVICES,
            item.refreshItemAction(RefreshItemAccess.FIND_ONLY, listOf(spotify)),
        )
    }

    @Test
    fun mappingOfAnUnavailableOrRemovedProviderGetsFind() {
        val item = artist(mapping())

        assertEquals(
            RefreshItemAction.FIND_ON_MUSIC_SERVICES,
            item.refreshItemAction(RefreshItemAccess.ALL, listOf(spotify.copy(available = false))),
        )
        assertEquals(
            RefreshItemAction.FIND_ON_MUSIC_SERVICES,
            item.refreshItemAction(RefreshItemAccess.ALL, emptyList()),
        )
    }

    @Test
    fun oneAvailableMappingIsEnough() {
        val item = artist(mapping(instance = "tidal--1"), mapping())

        assertEquals(RefreshItemAction.REFRESH, item.refreshItemAction(RefreshItemAccess.ALL, listOf(spotify)))
    }

    @Test
    fun hiddenWithoutAccessOrBeforeTheProvidersLoad() {
        val item = artist(mapping(available = false))

        assertNull(item.refreshItemAction(RefreshItemAccess.NONE, listOf(spotify)))
        assertNull(item.refreshItemAction(RefreshItemAccess.ALL, null))
    }

    @Test
    fun hiddenForAnItemWithoutAStoredCopy() {
        val episode = AppMediaItemFixtures.episodes(listOf("Episode")).first()

        assertNull(episode.refreshItemAction(RefreshItemAccess.ALL, emptyList()))
    }
}
