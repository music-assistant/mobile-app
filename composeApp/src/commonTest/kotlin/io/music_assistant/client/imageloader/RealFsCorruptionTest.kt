package io.music_assistant.client.imageloader

import kotlinx.coroutines.test.runTest
import okio.FileSystem
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RealFsCorruptionTest {
    @Test
    fun disk_corruption_and_io_failure() = runTest {
        val real = RealArtworkCache(maxBytes = 640L)
        try {
            val identity = artworkIdentity("corrupt")
            val bytes = artworkBytes(64, 3)
            assertTrue(real.store.write(identity, bytes, "image/png", 1_000L, 10_000L) != null)
            val snapshot = real.cache.openSnapshot(identity.key)
            assertTrue(snapshot != null)
            val snapshotData = snapshot.data
            snapshot.close()
            assertFalse(runCatching { FileSystem.SYSTEM.metadata(snapshotData) }.isFailure)
            FileSystem.SYSTEM.write(snapshotData) { write(artworkBytes(64, 4)) }
            assertNull(real.store.read(identity, 2_000L))
            assertNull(real.store.read(artworkIdentity("missing"), 2_000L))
        } finally {
            real.close()
        }

        val broken = RealArtworkCache(maxBytes = 640L)
        try {
            val identity = artworkIdentity("write-failure")
            val brokenStore = ArtworkDiskStore(
                cache = broken.cache,
                fileSystem = FailingArtworkFileSystem,
                now = { 1_000L },
            )
            assertNull(brokenStore.write(identity, artworkBytes(64), "image/png", 1_000L, 10_000L))
            assertNull(brokenStore.read(identity, 2_000L))
        } finally {
            broken.close()
        }
    }
}

private object FailingArtworkFileSystem : FileSystem() {
    override fun canonicalize(path: okio.Path): okio.Path = path

    override fun metadataOrNull(path: okio.Path): okio.FileMetadata? = error("storage unavailable")

    override fun list(dir: okio.Path): List<okio.Path> = error("storage unavailable")

    override fun listOrNull(dir: okio.Path): List<okio.Path>? = null

    override fun openReadOnly(file: okio.Path): okio.FileHandle = error("storage unavailable")

    override fun openReadWrite(file: okio.Path, mustCreate: Boolean, mustExist: Boolean): okio.FileHandle =
        error("storage unavailable")

    override fun atomicMove(source: okio.Path, target: okio.Path) = error("storage unavailable")

    override fun createDirectory(dir: okio.Path, mustCreate: Boolean) = error("storage unavailable")

    override fun delete(path: okio.Path, mustExist: Boolean) = error("storage unavailable")

    override fun source(file: okio.Path): okio.Source = error("storage unavailable")

    override fun sink(file: okio.Path, mustCreate: Boolean): okio.Sink = error("storage unavailable")

    override fun appendingSink(file: okio.Path, mustExist: Boolean): okio.Sink =
        error("storage unavailable")

    override fun createSymlink(source: okio.Path, target: okio.Path) = error("storage unavailable")
}
