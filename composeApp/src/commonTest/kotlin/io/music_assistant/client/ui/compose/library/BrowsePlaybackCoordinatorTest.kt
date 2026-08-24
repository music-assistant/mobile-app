package io.music_assistant.client.ui.compose.library

import io.music_assistant.client.settings.SettingsRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BrowsePlaybackCoordinatorTest {
    @Test
    fun `continuation follows alphabetical folders and wraps once`() {
        val folders =
            listOf(
                folder("c", "Charlie"),
                folder("a", "Alpha"),
                folder("b", "Bravo"),
            )

        assertEquals(
            listOf("c", "a"),
            orderedBrowseContinuationFolders("b", folders).map { it.path },
        )
    }

    @Test
    fun `current folder is never appended again`() {
        val duplicateCurrent =
            listOf(
                folder("a", "Alpha"),
                folder("a", "Alpha duplicate"),
                folder("b", "Bravo"),
            )

        assertEquals(
            listOf("b"),
            orderedBrowseContinuationFolders("a", duplicateCurrent).map { it.path },
        )
    }

    @Test
    fun `unknown folder does not append an unrelated library`() {
        assertTrue(
            orderedBrowseContinuationFolders(
                currentPath = "missing",
                folders = listOf(folder("a", "Alpha"), folder("b", "Bravo")),
            ).isEmpty(),
        )
    }

    private fun folder(path: String, name: String) =
        SettingsRepository.CachedBrowseFolder(
            path = path,
            itemId = path,
            provider = "filesystem_local--test",
            name = name,
        )
}
