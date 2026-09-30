package io.music_assistant.client.imageloader

import coil3.disk.DiskCache
import io.music_assistant.client.utils.ARTWORK_MAX_BODY_BYTES
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.buffer
import okio.use

internal data class ArtworkIdentity(val key: String, val url: String, val serverId: String?)

internal data class ArtworkMetadata(
    val identity: ArtworkIdentity,
    val digest: String,
    val fetchedAtMs: Long,
    val expiresAtMs: Long,
    val mimeType: String?,
)

internal data class StoredArtwork(
    val identity: ArtworkIdentity,
    val bytes: ByteArray,
    val mimeType: String?,
    val digest: String,
    val expiresAtMs: Long,
)

internal class ArtworkDiskStore(
    private val cache: DiskCache,
    private val fileSystem: FileSystem = cache.fileSystem,
    private val now: () -> Long = { 0L },
    private val beforeWrite: (suspend () -> Unit)? = null,
) {
    private val metadataMutex = Mutex()
    private val metadataCache = LinkedHashMap<String, ArtworkMetadata>()
    private var metadataEpoch = 0L

    suspend fun readMetadata(identity: ArtworkIdentity, currentTimeMs: Long = now()): ArtworkMetadata? = withContext(
        Dispatchers.IO,
    ) {
        metadataMutex.withLock {
            metadataCache.remove(identity.key)?.let { cached ->
                metadataCache[identity.key] = cached
                return@withLock cached.takeIf { it.isFreshAt(currentTimeMs) }
                    ?: run {
                        metadataCache.remove(identity.key)
                        null
                    }
            }
            val snapshot = bestEffort { cache.openSnapshot(identity.key) } ?: return@withLock null
            try {
                val metadata = readMetadata(snapshot, identity)?.takeIf { it.isFreshAt(currentTimeMs) }
                metadata?.let { publishMetadata(it) }
                metadata
            } finally {
                snapshot.close()
            }
        }
    }

    suspend fun read(identity: ArtworkIdentity, currentTimeMs: Long = now()): StoredArtwork? = withContext(
        Dispatchers.IO,
    ) {
        val snapshotAndMetadata = metadataMutex.withLock {
            val snapshot = bestEffort { cache.openSnapshot(identity.key) } ?: return@withLock null
            val metadata = readMetadata(snapshot, identity)
            if (metadata == null || !metadata.isFreshAt(currentTimeMs)) {
                snapshot.close()
                if (metadataCache[identity.key]?.let { it == metadata } == true) metadataCache.remove(identity.key)
                return@withLock null
            }
            SnapshotRead(snapshot, metadata, metadataEpoch)
        } ?: return@withContext null

        val (snapshot, metadata, epoch) = snapshotAndMetadata
        try {
            val bytes = bestEffort { fileSystem.read(snapshot.data) { readByteArray() } }
                ?: run {
                    ArtworkDiagnostics.cacheFailure("data-read", identity.key)
                    return@withContext null
                }
            val digest = artworkSha256Hex(bytes)
            if (digest != metadata.digest) {
                ArtworkDiagnostics.cacheFailure("digest-mismatch", identity.key)
                metadataMutex.withLock {
                    if (metadataEpoch == epoch) {
                        metadataCache.removeIfMatching(identity.key, metadata.digest)
                        bestEffort { cache.remove(identity.key) }
                        metadataEpoch++
                    }
                }
                return@withContext null
            }
            val result = StoredArtwork(identity, bytes, metadata.mimeType, digest, metadata.expiresAtMs)
            metadataMutex.withLock {
                if (metadataEpoch == epoch) publishMetadata(metadata)
            }
            result
        } finally {
            snapshot.close()
        }
    }

    suspend fun write(
        identity: ArtworkIdentity,
        bytes: ByteArray,
        mimeType: String?,
        fetchedAtMs: Long,
        expiresAtMs: Long,
        digest: String? = null,
    ): StoredArtwork? = withContext(Dispatchers.IO) {
        if (bytes.isEmpty() || bytes.size > ARTWORK_MAX_BODY_BYTES) return@withContext null
        val payloadDigest = digest ?: artworkSha256Hex(bytes)
        metadataMutex.withLock {
            val editor = bestEffort { cache.openEditor(identity.key) } ?: return@withLock null
            try {
                beforeWrite?.invoke()
                fileSystem.write(editor.data) { write(bytes) }
                fileSystem.write(editor.metadata) {
                    writeUtf8("$VERSION|$fetchedAtMs|$expiresAtMs|$payloadDigest|${mimeType ?: "-"}")
                }
                editor.commit()
                metadataEpoch++
                publishMetadata(ArtworkMetadata(identity, payloadDigest, fetchedAtMs, expiresAtMs, mimeType))
                StoredArtwork(identity, bytes.copyOf(), mimeType, payloadDigest, expiresAtMs)
            } catch (error: CancellationException) {
                bestEffort { editor.abort() }
                throw error
            } catch (_: Throwable) {
                bestEffort { editor.abort() }
                null
            }
        }
    }

    suspend fun invalidate(identity: ArtworkIdentity, digest: String): Boolean = withContext(Dispatchers.IO) {
        metadataMutex.withLock {
            val cached = metadataCache[identity.key]
            if (cached != null && cached.digest != digest) return@withLock false
            val snapshot = bestEffort { cache.openSnapshot(identity.key) }
            try {
                val diskMetadata = snapshot?.let { readMetadata(it, identity) }
                if (snapshot != null && diskMetadata == null) {
                    metadataCache.remove(identity.key)
                    metadataEpoch++
                    return@withLock false
                }
                if (diskMetadata != null && diskMetadata.digest != digest) return@withLock false
                if (cached?.digest != digest && diskMetadata?.digest != digest) return@withLock false
                metadataCache.remove(identity.key)
                metadataEpoch++
                bestEffort { cache.remove(identity.key) } ?: false
            } finally {
                snapshot?.close()
            }
        }
    }

    private fun readMetadata(snapshot: DiskCache.Snapshot, identity: ArtworkIdentity): ArtworkMetadata? {
        val text = try {
            fileSystem.source(snapshot.metadata).buffer().use { it.readUtf8() }
        } catch (_: Throwable) {
            return null
        }
        val fields = text.split('|')
        if (fields.size != METADATA_FIELD_COUNT || fields[0] != VERSION) return null
        val fetchedAt = fields[FETCHED_AT_FIELD].toLongOrNull() ?: return null
        val expiresAt = fields[EXPIRY_FIELD].toLongOrNull() ?: return null
        val digest = fields[DIGEST_FIELD]
        if (digest.isEmpty()) return null
        return ArtworkMetadata(
            identity = identity,
            digest = digest,
            fetchedAtMs = fetchedAt,
            expiresAtMs = expiresAt,
            mimeType = fields[MIME_FIELD].takeUnless { it == "-" },
        )
    }

    private fun publishMetadata(metadata: ArtworkMetadata) {
        metadataCache.remove(metadata.identity.key)
        metadataCache[metadata.identity.key] = metadata
        while (metadataCache.size > METADATA_CACHE_LIMIT) metadataCache.remove(metadataCache.entries.first().key)
    }

    private fun ArtworkMetadata.isFreshAt(currentTimeMs: Long): Boolean =
        fetchedAtMs <= currentTimeMs && currentTimeMs < expiresAtMs

    private fun MutableMap<String, ArtworkMetadata>.removeIfMatching(key: String, digest: String) {
        if (this[key]?.digest == digest) remove(key)
    }

    private data class SnapshotRead(
        val snapshot: DiskCache.Snapshot,
        val metadata: ArtworkMetadata,
        val epoch: Long,
    )

    private companion object {
        suspend fun <T> bestEffort(block: suspend () -> T): T? = try {
            block()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            null
        }

        const val VERSION = "artwork-v1"
        const val METADATA_FIELD_COUNT = 5
        const val FETCHED_AT_FIELD = 1
        const val EXPIRY_FIELD = 2
        const val MIME_FIELD = 4
        const val DIGEST_FIELD = 3
        const val METADATA_CACHE_LIMIT = 1024
    }
}
