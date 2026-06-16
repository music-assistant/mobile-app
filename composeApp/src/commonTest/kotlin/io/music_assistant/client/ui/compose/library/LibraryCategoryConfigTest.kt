package io.music_assistant.client.ui.compose.library

import io.music_assistant.client.settings.SettingsRepository.LibraryCategoryPref
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LibraryCategoryConfigTest {
    private fun pref(category: LibraryCategory, enabled: Boolean = true) =
        LibraryCategoryPref(category.name, enabled)

    @Test
    fun `null config yields every category enabled in declaration order`() {
        val result = reconcileLibraryCategories(null)

        assertEquals(LibraryCategory.entries.map { it to true }, result)
    }

    @Test
    fun `category missing from a stored config is appended at the end, enabled`() {
        // A pre-BROWSE config: every category except the newly-added one, in a custom order.
        val stored = listOf(
            pref(LibraryCategory.ALBUMS, enabled = true),
            pref(LibraryCategory.ARTISTS, enabled = false),
        )

        val result = reconcileLibraryCategories(stored)

        // Stored entries keep their order and enabled flags...
        assertEquals(LibraryCategory.ALBUMS to true, result[0])
        assertEquals(LibraryCategory.ARTISTS to false, result[1])
        // ...and BROWSE (absent from the stored config) is appended, enabled.
        assertTrue(result.contains(LibraryCategory.BROWSE to true))
        // Every live category is present exactly once.
        assertEquals(LibraryCategory.entries.toSet(), result.map { it.first }.toSet())
        assertEquals(LibraryCategory.entries.size, result.size)
    }

    @Test
    fun `unknown stored names are dropped`() {
        val stored = listOf(
            pref(LibraryCategory.TRACKS),
            LibraryCategoryPref("A_REMOVED_CATEGORY", enabled = true),
        )

        val result = reconcileLibraryCategories(stored)

        assertEquals(LibraryCategory.TRACKS to true, result.first())
        assertTrue(result.none { it.first.name == "A_REMOVED_CATEGORY" })
        // Dropping an unknown name still leaves the full live universe reconciled in.
        assertEquals(LibraryCategory.entries.toSet(), result.map { it.first }.toSet())
    }

    @Test
    fun `a fully-specified config is returned verbatim`() {
        val stored = LibraryCategory.entries.mapIndexed { index, category ->
            pref(category, enabled = index % 2 == 0)
        }

        val result = reconcileLibraryCategories(stored)

        assertEquals(stored.map { LibraryCategory.valueOf(it.name) to it.enabled }, result)
    }
}
