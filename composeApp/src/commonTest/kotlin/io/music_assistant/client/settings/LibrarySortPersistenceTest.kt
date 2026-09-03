package io.music_assistant.client.settings

import com.russhwolf.settings.MapSettings
import io.music_assistant.client.data.model.client.MediaType
import io.music_assistant.client.data.model.client.SortConfig
import io.music_assistant.client.data.model.client.SortField
import io.music_assistant.client.data.model.client.SortOption
import io.music_assistant.client.data.model.client.SubItemContext
import kotlin.test.Test
import kotlin.test.assertEquals

class LibrarySortPersistenceTest {
    @Test
    fun `defaults to the media type default when nothing stored`() {
        val repo = SettingsRepository(MapSettings(), MapSettings())
        assertEquals(SortConfig.defaultFor(MediaType.ALBUM), repo.getSortOption(MediaType.ALBUM))
        assertEquals(
            SortOption(SortField.DATE_ADDED, descending = true),
            repo.getSortOption(MediaType.PODCAST),
        )
    }

    @Test
    fun `set then read back the field and the direction`() {
        val repo = SettingsRepository(MapSettings(), MapSettings())
        val option = SortOption(SortField.DATE_ADDED, descending = true)
        repo.setSortOption(MediaType.ALBUM, option)
        assertEquals(option, repo.getSortOption(MediaType.ALBUM))
    }

    @Test
    fun `each media type keeps its own sort`() {
        val repo = SettingsRepository(MapSettings(), MapSettings())
        repo.setSortOption(MediaType.ALBUM, SortOption(SortField.YEAR))
        repo.setSortOption(MediaType.TRACK, SortOption(SortField.DURATION, descending = true))
        assertEquals(SortOption(SortField.YEAR), repo.getSortOption(MediaType.ALBUM))
        assertEquals(SortOption(SortField.DURATION, descending = true), repo.getSortOption(MediaType.TRACK))
    }

    @Test
    fun `a field that the media type no longer offers falls back to the default`() {
        val settings = MapSettings("library_sort_TRACK" to "YEAR:false")
        val repo = SettingsRepository(settings, MapSettings())
        assertEquals(SortConfig.defaultFor(MediaType.TRACK), repo.getSortOption(MediaType.TRACK))
    }

    @Test
    fun `a corrupt stored value falls back to the default`() {
        val settings = MapSettings("library_sort_ALBUM" to "garbage")
        val repo = SettingsRepository(settings, MapSettings())
        assertEquals(SortConfig.defaultFor(MediaType.ALBUM), repo.getSortOption(MediaType.ALBUM))
    }

    @Test
    fun `a stale playlist sort is ignored`() {
        // Written by a build before the playlist sort options were removed.
        val settings = MapSettings("sort_sub_PLAYLIST_ITEMS" to "ARTIST_NAME:false")
        val repo = SettingsRepository(settings, MapSettings())
        assertEquals(
            SortConfig.defaultFor(SubItemContext.PLAYLIST_ITEMS),
            repo.getSortOption(SubItemContext.PLAYLIST_ITEMS),
        )
    }

    @Test
    fun `a stale descending original sort is ignored`() {
        // ORIGINAL is still a legal field, so only the fixed-order guard catches the direction.
        val settings = MapSettings("sort_sub_PLAYLIST_ITEMS" to "ORIGINAL:true")
        val repo = SettingsRepository(settings, MapSettings())
        assertEquals(
            SortOption(SortField.ORIGINAL),
            repo.getSortOption(SubItemContext.PLAYLIST_ITEMS),
        )
    }

    @Test
    fun `a stale album track sort is ignored`() {
        // A non-ORIGINAL field here also suppresses the multi-disc headers on the album page.
        val settings = MapSettings("sort_sub_ALBUM_TRACKS" to "NAME:false")
        val repo = SettingsRepository(settings, MapSettings())
        assertEquals(
            SortConfig.defaultFor(SubItemContext.ALBUM_TRACKS),
            repo.getSortOption(SubItemContext.ALBUM_TRACKS),
        )
    }

    @Test
    fun `a sortable sub item context still round trips`() {
        val repo = SettingsRepository(MapSettings(), MapSettings())
        val option = SortOption(SortField.DURATION, descending = true)
        repo.setSortOption(SubItemContext.PODCAST_EPISODES, option)
        assertEquals(option, repo.getSortOption(SubItemContext.PODCAST_EPISODES))
    }

    @Test
    fun `library sort and detail sort do not share a key`() {
        val repo = SettingsRepository(MapSettings(), MapSettings())
        repo.setSortOption(MediaType.ALBUM, SortOption(SortField.YEAR, descending = true))
        assertEquals(
            SortConfig.defaultFor(SubItemContext.ARTIST_ALBUMS),
            repo.getSortOption(SubItemContext.ARTIST_ALBUMS),
        )
    }
}
