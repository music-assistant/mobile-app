package io.music_assistant.client.api

import io.music_assistant.client.data.model.client.ArtistType
import io.music_assistant.client.data.model.client.GenreContentType
import io.music_assistant.client.data.model.client.LibraryFilters
import io.music_assistant.client.data.model.client.MediaType
import io.music_assistant.client.data.model.client.genreMediaTypeFilter
import io.music_assistant.client.utils.myJson
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Pins the wire shape of the list filters. A typo in an argument name fails silently: the server
 * drops unknown arguments into `**kwargs` and answers the unfiltered list.
 */
class LibraryFilterRequestTest {
    @Test
    fun authorsListingSendsTheArtistType() {
        val request = Request.Artist.listLibrary(artistType = ArtistType.AUTHOR.serverValue)

        assertEquals("music/artists/library_items", request.command)
        assertEquals(JsonPrimitive("author"), request.args?.get("artist_type"))
    }

    @Test
    fun artistsListingOmitsTheArtistType() {
        assertNull(Request.Artist.listLibrary().args?.get("artist_type"))
    }

    @Test
    fun libraryTypesIsACommandWithoutArguments() {
        assertEquals("music/artists/library_artist_types", Request.Artist.libraryTypes().command)
    }

    @Test
    fun genresListingSendsTheContentType() {
        val request = Request.Genre.listLibrary(contentType = GenreContentType.PODCAST.serverValue)

        assertEquals(JsonPrimitive("podcast"), request.args?.get("content_type"))
        assertNull(Request.Genre.listLibrary().args?.get("content_type"))
    }

    @Test
    fun episodesListingSendsFullyPlayedOnlyWhenAsked() {
        val filtered = Request.Podcast.getEpisodes("1", "library", fullyPlayed = false)

        assertEquals("music/podcasts/podcast_episodes", filtered.command)
        assertEquals(JsonPrimitive(false), filtered.args?.get("fully_played"))
        assertNull(Request.Podcast.getEpisodes("1", "library").args?.get("fully_played"))
    }

    // A media type saved before the content type existed must not narrow the other taxonomies.
    @Test
    fun genreMediaTypeAppliesToTheMusicTaxonomyOnly() {
        val stored = LibraryFilters(genreMediaType = MediaType.ALBUM)

        assertNull(stored.genreMediaTypeFilter)
        assertNull(stored.copy(genreContentType = GenreContentType.PODCAST).genreMediaTypeFilter)
        assertEquals(MediaType.ALBUM, stored.copy(genreContentType = GenreContentType.MUSIC).genreMediaTypeFilter)
    }

    // Filters saved by an older app version have no entry for the new fields.
    @Test
    fun storedFiltersWithoutTheNewFieldsDecodeToTheDefaults() {
        val filters = myJson.decodeFromString<LibraryFilters>("""{"favorite":true}""")

        assertEquals(LibraryFilters(favorite = true), filters)
        assertNull(filters.audiobookArtistType)
        assertNull(filters.genreContentType)
    }
}
