package io.music_assistant.client.imageloader

import coil3.disk.DiskCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.buffer
import okio.use

internal data class ArtworkIdentity(val key: String, val url: String, val serverId: String?)

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
    suspend fun read(identity: ArtworkIdentity, currentTimeMs: Long = now()): StoredArtwork? = withContext(
        Dispatchers.Default,
    ) {
        val snapshot = bestEffort { cache.openSnapshot(identity.key) } ?: return@withContext null
        try {
            val metadata = bestEffort {
                fileSystem.source(snapshot.metadata).buffer().use { it.readUtf8() }
            } ?: return@withContext null
            val fields = metadata.split('|')
            if (fields.size != METADATA_FIELD_COUNT || fields[0] != VERSION) return@withContext null
            val expiresAt = fields[EXPIRY_FIELD].toLongOrNull() ?: return@withContext null
            val fetchedAt = fields[FETCHED_AT_FIELD].toLongOrNull() ?: return@withContext null
            if (currentTimeMs < 0L || currentTimeMs < fetchedAt) {
                return@withContext null
            }
            if (currentTimeMs >= expiresAt) {
                ArtworkDiagnostics.cacheExpired(identity.key)
                return@withContext null
            }
            val bytes = bestEffort { fileSystem.read(snapshot.data) { readByteArray() } }
                ?: run {
                    ArtworkDiagnostics.cacheFailure("data-read", identity.key)
                    return@withContext null
                }
            val digest = artworkSha256Hex(bytes)
            if (digest != fields[DIGEST_FIELD]) {
                ArtworkDiagnostics.cacheFailure("digest-mismatch", identity.key)
                bestEffort { cache.remove(identity.key) }
                return@withContext null
            }
            StoredArtwork(identity, bytes, fields[MIME_FIELD].takeUnless { it == "-" }, digest, expiresAt)
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
    ): StoredArtwork? = withContext(Dispatchers.Default) {
        if (bytes.isEmpty() || bytes.size > ARTWORK_MAX_BODY_BYTES) return@withContext null
        val payloadDigest = digest ?: artworkSha256Hex(bytes)
        val editor = bestEffort { cache.openEditor(identity.key) } ?: return@withContext null
        try {
            beforeWrite?.invoke()
            fileSystem.write(editor.data) { write(bytes) }
            fileSystem.write(editor.metadata) {
                writeUtf8("$VERSION|$fetchedAtMs|$expiresAtMs|$payloadDigest|${mimeType ?: "-"}")
            }
            editor.commit()
            StoredArtwork(identity, bytes.copyOf(), mimeType, payloadDigest, expiresAtMs)
        } catch (error: CancellationException) {
            bestEffort { editor.abort() }
            throw error
        } catch (_: Throwable) {
            bestEffort { editor.abort() }
            null
        }
    }

    suspend fun invalidate(identity: ArtworkIdentity, digest: String): Boolean = withContext(Dispatchers.Default) {
        val snapshot = bestEffort { cache.openSnapshot(identity.key) } ?: return@withContext false
        try {
            val metadata = bestEffort {
                fileSystem.source(snapshot.metadata).buffer().use { it.readUtf8() }
            } ?: return@withContext false
            val fields = metadata.split('|')
            if (fields.size != METADATA_FIELD_COUNT || fields[0] != VERSION) return@withContext false
            if (fields[DIGEST_FIELD] != digest) return@withContext false
            bestEffort { cache.remove(identity.key) } ?: false
        } finally {
            snapshot.close()
        }
    }

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
        const val ARTWORK_MAX_BODY_BYTES = 16L * 1024L * 1024L
    }
}
