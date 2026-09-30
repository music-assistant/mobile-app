package io.music_assistant.client.imageloader

import coil3.disk.DiskCache
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.ForwardingFileSystem
import okio.Path
import okio.Source
import okio.buffer
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ArtworkDiskStoreMetadataTest {
    @Test
    fun metadata_lookup_warms_without_body_read_and_preserves_mime() = runTest {
        val real = RealArtworkCache(maxBytes = 16L * 1024L * 1024L)
        try {
            val identity = artworkIdentity("metadata-only")
            val bytes = artworkBytes(64, 3)
            assertNotNull(real.store.write(identity, bytes, "image/jpeg", 1_000L, 10_000L))
            val metadata = real.store.readMetadata(identity, 2_000L)
            assertNotNull(metadata)
            assertEquals("image/jpeg", metadata.mimeType)
            assertContentEquals(bytes, real.store.read(identity, 2_000L)?.bytes)
        } finally {
            real.close()
        }
    }

    @Test
    fun read_uses_replacement_snapshot_instead_of_cached_metadata() = runTest {
        val real = RealArtworkCache(maxBytes = 16L * 1024L * 1024L)
        try {
            val identity = artworkIdentity("replacement")
            val oldBytes = artworkBytes(64, 1)
            val newBytes = artworkBytes(64, 2)
            assertNotNull(real.store.write(identity, oldBytes, "image/png", 1_000L, 10_000L))
            assertNotNull(real.store.readMetadata(identity, 2_000L))
            val replacementStore = ArtworkDiskStore(real.cache, now = { 1_000L })
            assertNotNull(replacementStore.write(identity, newBytes, "image/webp", 2_000L, 11_000L))
            assertContentEquals(newBytes, real.store.read(identity, 2_000L)?.bytes)
            assertEquals("image/webp", real.store.read(identity, 2_000L)?.mimeType)
        } finally {
            real.close()
        }
    }

    @Test
    fun metadata_rejects_expiry_and_clock_rollback() = runTest {
        val real = RealArtworkCache(maxBytes = 16L * 1024L * 1024L)
        try {
            val identity = artworkIdentity("freshness")
            assertNotNull(real.store.write(identity, artworkBytes(64), "image/png", 1_000L, 2_000L))
            assertNotNull(real.store.readMetadata(identity, 1_000L))
            assertNull(real.store.readMetadata(identity, 999L))
            assertNull(real.store.readMetadata(identity, 2_000L))
            assertNull(real.store.read(identity, 2_000L))
        } finally {
            real.close()
        }
    }

    @Test
    fun matching_invalidation_clears_cached_token_after_disk_removal() = runTest {
        val real = RealArtworkCache(maxBytes = 16L * 1024L * 1024L)
        try {
            val identity = artworkIdentity("evicted-token")
            val bytes = artworkBytes(120, 7)
            val stored = assertNotNull(real.store.write(identity, bytes, "image/png", 1_000L, 10_000L))
            assertEquals(artworkSha256Hex(bytes), stored.digest)
            assertNotNull(real.store.readMetadata(identity, 2_000L))
            real.store.write(artworkIdentity("one"), artworkBytes(120, 1), "image/png", 1_000L, 10_000L)
            real.store.write(artworkIdentity("two"), artworkBytes(120, 2), "image/png", 1_000L, 10_000L)
            real.store.write(artworkIdentity("three"), artworkBytes(120, 3), "image/png", 1_000L, 10_000L)
            real.cache.remove(identity.key)
            assertNull(real.store.read(identity, 2_000L))
            assertFalse(real.store.invalidate(identity, stored.digest))
            assertNull(real.store.readMetadata(identity, 2_000L))
            assertFalse(real.store.invalidate(identity, "wrong"))
        } finally {
            real.close()
        }
    }

    @Test
    fun metadata_lookup_does_not_read_body_and_warm_lookup_does_not_read_sidecar() = runTest {
        val real = RealArtworkCache(maxBytes = 16L * 1024L * 1024L)
        try {
            val identity = artworkIdentity("instrumented-lookup")
            val fs = InstrumentedFileSystem()
            val store = ArtworkDiskStore(real.cache, fs, now = { 1_000L })
            assertNotNull(store.write(identity, artworkBytes(64, 3), "image/jpeg", 1_000L, 10_000L))
            trackSnapshot(real.cache, identity, fs)
            val coldStore = ArtworkDiskStore(real.cache, fs, now = { 1_000L })
            fs.resetCounters()
            assertNotNull(coldStore.readMetadata(identity, 2_000L))
            assertEquals(1, fs.metadataSourceReads)
            assertEquals(0, fs.dataSourceReads)
            fs.resetCounters()
            assertNotNull(store.readMetadata(identity, 2_000L))
            assertEquals(0, fs.metadataSourceReads)
            assertEquals(0, fs.dataSourceReads)
        } finally {
            real.close()
        }
    }

    @Test
    fun metadata_cache_falls_back_to_oldest_sidecar_after_1025_entries() = runTest {
        val real = RealArtworkCache(maxBytes = 32L * 1024L * 1024L)
        try {
            val first = artworkIdentity("bounded-first")
            val fs = InstrumentedFileSystem()
            val store = ArtworkDiskStore(real.cache, fs, now = { 1_000L })
            assertNotNull(store.write(first, artworkBytes(32, 1), "image/png", 1_000L, 10_000L))
            repeat(1_025) { index ->
                assertNotNull(
                    store.write(
                        identity = artworkIdentity("bounded-$index"),
                        bytes = artworkBytes(32, index.toByte()),
                        mimeType = "image/png",
                        fetchedAtMs = 1_000L,
                        expiresAtMs = 10_000L,
                    ),
                )
            }
            trackSnapshot(real.cache, first, fs)
            fs.resetCounters()
            assertNotNull(store.readMetadata(first, 2_000L))
            assertEquals(1, fs.metadataSourceReads)
            assertEquals(0, fs.dataSourceReads)
        } finally {
            real.close()
        }
    }

    @Test
    fun corrupt_sidecar_warm_invalidation_preserves_unknown_disk_version() = runTest {
        val real = RealArtworkCache(maxBytes = 16L * 1024L * 1024L)
        try {
            val identity = artworkIdentity("corrupt-sidecar")
            val fs = InstrumentedFileSystem()
            val store = ArtworkDiskStore(real.cache, fs, now = { 1_000L })
            val stored = assertNotNull(store.write(identity, artworkBytes(64, 3), "image/png", 1_000L, 10_000L))
            assertNotNull(store.readMetadata(identity, 2_000L))
            val snapshot = real.cache.openSnapshot(identity.key)
            assertNotNull(snapshot)
            val metadataPath = snapshot.metadata
            snapshot.close()
            FileSystem.SYSTEM.write(metadataPath) { writeUtf8("malformed") }
            assertFalse(store.invalidate(identity, stored.digest))
            val preserved = real.cache.openSnapshot(identity.key)
            assertNotNull(preserved)
            preserved.use { assertTrue(FileSystem.SYSTEM.metadata(it.data).isRegularFile) }
            assertNull(store.readMetadata(identity, 2_000L))
        } finally {
            real.close()
        }
    }

    @Test
    fun full_read_with_missing_metadata_fails_closed() = runTest {
        val real = RealArtworkCache(maxBytes = 16L * 1024L * 1024L)
        try {
            val identity = artworkIdentity("missing-metadata")
            val fs = InstrumentedFileSystem(metadataReadFailure = true)
            val store = ArtworkDiskStore(real.cache, fs, now = { 1_000L })
            assertNotNull(store.write(identity, artworkBytes(64, 3), "image/png", 1_000L, 10_000L))
            trackSnapshot(real.cache, identity, fs)
            fs.resetCounters()
            assertNull(store.read(identity, 2_000L))
            assertEquals(0, fs.dataSourceReads)
        } finally {
            real.close()
        }
    }

    @Test
    fun invalidation_epoch_prevents_stale_metadata_republish_after_body_read() = runTest {
        val real = RealArtworkCache(maxBytes = 16L * 1024L * 1024L)
        try {
            val identity = artworkIdentity("epoch-gate")
            val bodyStarted = CompletableDeferred<Unit>()
            val releaseBody = CompletableDeferred<Unit>()
            val fs = InstrumentedFileSystem(bodyStarted, releaseBody)
            val store = ArtworkDiskStore(real.cache, fs, now = { 1_000L })
            val stored = assertNotNull(store.write(identity, artworkBytes(64, 3), "image/png", 1_000L, 10_000L))
            trackSnapshot(real.cache, identity, fs)
            val readJob = launch { assertNotNull(store.read(identity, 2_000L)) }
            bodyStarted.await()
            assertTrue(store.invalidate(identity, stored.digest))
            releaseBody.complete(Unit)
            readJob.join()
            assertNull(store.readMetadata(identity, 2_000L))
        } finally {
            real.close()
        }
    }
}

private class InstrumentedFileSystem(
    private val bodyStarted: CompletableDeferred<Unit>? = null,
    private val releaseBody: CompletableDeferred<Unit>? = null,
    private val metadataReadFailure: Boolean = false,
) : ForwardingFileSystem(FileSystem.SYSTEM) {
    var metadataPath: Path? = null
    var dataPath: Path? = null
    var metadataSourceReads = 0
    var dataSourceReads = 0

    override fun source(file: Path): Source {
        if (file == metadataPath && metadataReadFailure) error("metadata unavailable")
        if (file == metadataPath) metadataSourceReads++
        if (file == dataPath) dataSourceReads++
        val source = super.source(file)
        return if (file == dataPath && bodyStarted != null && releaseBody != null) {
            source.buffer().let { buffered ->
                object : Source by buffered {
                    override fun read(sink: okio.Buffer, byteCount: Long): Long {
                        bodyStarted.complete(Unit)
                        runBlocking { releaseBody.await() }
                        return buffered.read(sink, byteCount)
                    }
                }
            }
        } else {
            source
        }
    }

    fun resetCounters() {
        metadataSourceReads = 0
        dataSourceReads = 0
    }
}

private fun trackSnapshot(cache: DiskCache, identity: ArtworkIdentity, fs: InstrumentedFileSystem) {
    val snapshot = checkNotNull(cache.openSnapshot(identity.key))
    fs.metadataPath = snapshot.metadata
    fs.dataPath = snapshot.data
    snapshot.close()
}
