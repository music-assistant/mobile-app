package io.music_assistant.client.imageloader

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RealFsBudgetTest {
    @Test
    fun real_disk_budget_and_lru() = runTest {
        val first = RealArtworkCache(maxBytes = 640L)
        try {
            val one = artworkIdentity("one")
            val two = artworkIdentity("two")
            val three = artworkIdentity("three")
            val firstBytes = artworkBytes(120, 1)
            val secondBytes = artworkBytes(120, 2)
            val thirdBytes = artworkBytes(120, 3)
            assertNotNull(first.store.write(one, firstBytes, "image/png", 1_000L, 10_000L))
            assertNotNull(first.store.write(two, secondBytes, "image/png", 1_000L, 10_000L))
            assertNotNull(first.store.read(one, 2_000L))
            assertNotNull(first.store.write(three, thirdBytes, "image/png", 1_000L, 10_000L))
            first.settle()
            assertTrue(first.cache.size <= first.cache.maxSize)
            assertNotNull(first.store.read(one, 2_000L))
            assertNotNull(first.store.read(three, 2_000L))
            assertNull(first.store.read(two, 2_000L))
            assertContentEquals(firstBytes, first.store.read(one, 2_000L)?.bytes)
            assertContentEquals(thirdBytes, first.store.read(three, 2_000L)?.bytes)
        } finally {
            first.close()
        }

        val persisted = artworkIdentity("persisted")
        val persistedBytes = artworkBytes(120, 7)
        val original = RealArtworkCache(maxBytes = 640L)
        val directory = original.directory
        var originalShutdown = false
        try {
            assertNotNull(original.store.write(persisted, persistedBytes, "image/png", 1_000L, 10_000L))
            original.settle()
            original.cache.shutdown()
            originalShutdown = true
            val secondCache = coil3.disk.DiskCache.Builder()
                .directory(directory)
                .maxSizeBytes(640L)
                .build()
            try {
                val secondStore = ArtworkDiskStore(secondCache, now = { 1_000L })
                assertContentEquals(persistedBytes, secondStore.read(persisted, 2_000L)?.bytes)
                val editor = secondCache.openEditor(artworkIdentity("active").key)
                assertNotNull(editor)
                FileSystemForTests.write(editor.data, artworkBytes(240, 8))
                assertTrue(secondCache.size >= 0L)
                editor.abort()
                secondStore.write(artworkIdentity("settled"), artworkBytes(120, 9), "image/png", 1_000L, 10_000L)
                awaitDiskCacheWithinBudget(secondCache)
                assertTrue(secondCache.size <= secondCache.maxSize)
            } finally {
                secondCache.shutdown()
            }
        } finally {
            if (!originalShutdown) {
                original.cache.shutdown()
            }
            runCatching { FileSystemForTests.delete(directory) }
        }
    }
}

private object FileSystemForTests {
    private val fileSystem = okio.FileSystem.SYSTEM

    fun write(path: okio.Path, bytes: ByteArray) {
        fileSystem.write(path) { write(bytes) }
    }

    fun delete(path: okio.Path) {
        fileSystem.deleteRecursively(path)
    }
}
