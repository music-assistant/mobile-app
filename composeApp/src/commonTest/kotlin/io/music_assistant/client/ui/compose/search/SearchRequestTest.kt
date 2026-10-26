package io.music_assistant.client.ui.compose.search

import io.music_assistant.client.data.model.client.MediaType
import io.music_assistant.client.ui.compose.search.SearchViewModel.Companion.searchRequest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SearchRequestTest {
    private fun state(
        vararg selected: MediaType,
        libraryOnly: Boolean = false,
        sources: SearchSources? = null,
    ) = SearchViewModel.SearchState(
        query = "jazz",
        mediaTypes = MediaType.entries.map { SearchViewModel.MediaTypeSelect(it, it in selected) },
        libraryOnly = libraryOnly,
        sources = sources,
    )

    private fun sources(vararg selected: String) = SearchSources(providers = emptyList(), selected = selected.toList())

    private fun SearchViewModel.SearchState.providersArg() = searchRequest(this).args?.get("providers")

    private fun SearchViewModel.SearchState.libraryOnlyArg() =
        searchRequest(this).args?.get("library_only")

    @Test
    fun `a genre-only search asks the library only`() {
        val request = searchRequest(state(MediaType.GENRE))

        assertEquals(JsonPrimitive(true), request.args?.get("library_only"))
        assertEquals(JsonArray(listOf(JsonPrimitive("genre"))), request.args?.get("media_types"))
    }

    @Test
    fun `genre with another type still searches the providers`() {
        assertEquals(JsonPrimitive(false), state(MediaType.GENRE, MediaType.ARTIST).libraryOnlyArg())
    }

    @Test
    fun `an unfiltered search still searches the providers`() {
        assertEquals(JsonPrimitive(false), state().libraryOnlyArg())
    }

    @Test
    fun `the library-only switch is kept for any selection`() {
        assertEquals(JsonPrimitive(true), state(MediaType.ARTIST, libraryOnly = true).libraryOnlyArg())
    }

    @Test
    fun `a server with sources gets the selected sources and not the library-only flag`() {
        val request = searchRequest(
            state(MediaType.ARTIST, libraryOnly = true, sources = sources("library", "spotify")),
        )

        assertEquals(
            JsonArray(listOf(JsonPrimitive("library"), JsonPrimitive("spotify"))),
            request.args?.get("providers"),
        )
        assertEquals(JsonPrimitive(false), request.args?.get("library_only"))
    }

    @Test
    fun `no selected source searches everything`() {
        assertNull(state(sources = sources()).providersArg())
    }

    @Test
    fun `a genre-only search on a server with sources asks the library only`() {
        assertEquals(
            JsonArray(listOf(JsonPrimitive(SearchSources.LIBRARY))),
            state(MediaType.GENRE, sources = sources("spotify")).providersArg(),
        )
    }

    @Test
    fun `a server without sources gets no providers argument`() {
        assertNull(state(MediaType.ARTIST).providersArg())
    }
}
