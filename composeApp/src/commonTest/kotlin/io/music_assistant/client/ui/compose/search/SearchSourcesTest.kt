package io.music_assistant.client.ui.compose.search

import io.music_assistant.client.data.model.server.ServerProviderInstance
import io.music_assistant.client.ui.compose.common.SelectOption
import kotlin.test.Test
import kotlin.test.assertEquals

class SearchSourcesTest {
    private fun provider(
        instanceId: String,
        name: String,
        domain: String = instanceId,
        streaming: Boolean = false,
        available: Boolean = true,
        features: List<String> = listOf("search"),
    ) = ServerProviderInstance(
        instanceId = instanceId,
        name = name,
        domain = domain,
        available = available,
        supportedFeatures = features,
        isStreamingProvider = streaming,
    )

    @Test
    fun `a streaming service is one target by domain and a local provider one target per instance`() {
        val sources = SearchSources.of(
            providers = listOf(
                provider("spotify--a", "Spotify", domain = "spotify", streaming = true),
                provider("spotify--b", "Spotify (Kim)", domain = "spotify", streaming = true),
                provider("filesystem--1", "Music folder", domain = "filesystem_local"),
                provider("filesystem--2", "Audiobooks folder", domain = "filesystem_local"),
            ),
            saved = emptyList(),
        )

        assertEquals(
            listOf(
                SelectOption("filesystem--2", "Audiobooks folder"),
                SelectOption("filesystem--1", "Music folder"),
                SelectOption("spotify", "Spotify"),
            ),
            sources.providers,
        )
    }

    @Test
    fun `a provider that is unavailable or cannot search is not a target`() {
        val sources = SearchSources.of(
            providers = listOf(
                provider("tidal", "Tidal", available = false),
                provider("radiobrowser", "Radio Browser", features = listOf("browse")),
            ),
            saved = emptyList(),
        )

        assertEquals(emptyList(), sources.providers)
    }

    @Test
    fun `a saved provider that is gone is dropped and the library stays`() {
        val sources = SearchSources.of(
            providers = listOf(provider("tidal", "Tidal")),
            saved = listOf("library", "tidal", "deezer"),
        )

        assertEquals(listOf("library", "tidal"), sources.selected)
    }

    @Test
    fun `the saved choice stays whole while the provider list loads`() {
        val sources = SearchSources.of(providers = null, saved = listOf("library", "deezer"))

        assertEquals(listOf("library", "deezer"), sources.selected)
        assertEquals(null, sources.providers)
    }
}
