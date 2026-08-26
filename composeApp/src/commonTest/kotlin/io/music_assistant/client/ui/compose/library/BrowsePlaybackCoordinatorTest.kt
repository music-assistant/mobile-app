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
                folder("filesystem_local--test://c", "Charlie"),
                folder("filesystem_local--test://a", "Alpha"),
                folder("filesystem_local--test://b", "Bravo"),
            )

        assertEquals(
            listOf(
                "filesystem_local--test://c",
                "filesystem_local--test://a",
            ),
            orderedBrowseContinuationFolders(
                "filesystem_local--test://b",
                folders,
            ).map { it.path },
        )
    }

    @Test
    fun `current folder is never appended again`() {
        val duplicateCurrent =
            listOf(
                folder("filesystem_local--test://a", "Alpha"),
                folder("filesystem_local--test://a", "Alpha duplicate"),
                folder("filesystem_local--test://b", "Bravo"),
            )

        assertEquals(
            listOf("filesystem_local--test://b"),
            orderedBrowseContinuationFolders(
                "filesystem_local--test://a",
                duplicateCurrent,
            ).map { it.path },
        )
    }

    @Test
    fun `continuation excludes folders from other hierarchy levels`() {
        val folders =
            listOf(
                folder("filesystem_local--test://anastazie", "anastazie"),
                folder("filesystem_local--test://award wining dj", "award wining dj"),
                folder("filesystem_local--test://abba/disc 1", "disc 1"),
                folder("filesystem_local--other://gigi", "gigi"),
            )

        assertEquals(
            listOf("filesystem_local--test://award wining dj", "filesystem_local--test://abba/disc 1"),
            orderedBrowseContinuationFolders(
                "filesystem_local--test://anastazie",
                folders,
            ).map { it.path },
        )
    }

    @Test
    fun `nested sole child continues with next album folder`() {
        val folders =
            listOf(
                folder("filesystem_local--test://anastazie/misc", "Misc"),
                folder("filesystem_local--test://award-winning-dj/cd1", "CD1"),
                folder("filesystem_local--other://gigi", "Gigi"),
            )

        assertEquals(
            listOf("filesystem_local--test://award-winning-dj/cd1"),
            orderedBrowseContinuationFolders(
                "filesystem_local--test://anastazie/misc",
                folders,
            ).map { it.path },
        )
    }

    @Test
    fun `unknown folder does not append an unrelated library`() {
        assertTrue(
            orderedBrowseContinuationFolders(
                currentPath = "missing",
                folders =
                    listOf(
                        folder("filesystem_local--test://a", "Alpha"),
                        folder("filesystem_local--test://b", "Bravo"),
                    ),
            ).isEmpty(),
        )
    }

    @Test
    fun `only non-root local filesystem folders are cacheable`() {
        assertTrue(folder("filesystem_local--test://ABBA", "ABBA").isLocalFilesystemFolder())
        assertTrue(!folder("filesystem_local--test://", "Filesystem").isLocalFilesystemFolder())
        assertTrue(
            !SettingsRepository.CachedBrowseFolder(
                path = "tunein--test://category/solomon-islands",
                itemId = "solomon-islands",
                provider = "tunein--test",
                name = "Solomon Islands",
            ).isLocalFilesystemFolder(),
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
