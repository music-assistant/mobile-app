package io.music_assistant.client.ui.compose.search

import io.music_assistant.client.data.model.client.MediaType
import io.music_assistant.client.ui.compose.search.SearchViewModel.Companion.searchRequest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class SearchRequestTest {
    private fun state(vararg selected: MediaType, libraryOnly: Boolean = false) =
        SearchViewModel.SearchState(
            query = "jazz",
            mediaTypes = MediaType.entries.map { SearchViewModel.MediaTypeSelect(it, it in selected) },
            libraryOnly = libraryOnly,
        )

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
}
