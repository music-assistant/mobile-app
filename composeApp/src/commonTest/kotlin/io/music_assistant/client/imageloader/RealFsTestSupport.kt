package io.music_assistant.client.imageloader

import coil3.disk.DiskCache
import io.ktor.http.Headers
import io.ktor.http.HeadersBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
internal class RealArtworkCache(
    private val maxBytes: Long = 256L,
) : AutoCloseable {
    val directory: Path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "ma-artwork-${Uuid.random()}"
    val cache: DiskCache
    val store: ArtworkDiskStore

    init {
        FileSystem.SYSTEM.createDirectories(directory)
        cache = DiskCache.Builder()
            .directory(directory)
            .maxSizeBytes(maxBytes)
            .build()
        store = ArtworkDiskStore(cache, now = { 1_000L })
    }

    suspend fun settle() {
        awaitDiskCacheWithinBudget(cache)
    }

    override fun close() {
        cache.shutdown()
        runCatching { FileSystem.SYSTEM.deleteRecursively(directory) }
    }
}

internal suspend fun awaitDiskCacheWithinBudget(
    cache: DiskCache,
    timeoutMs: Long = 5_000L,
) {
    withContext(Dispatchers.Default) {
        withTimeout(timeoutMs) {
            while (cache.size > cache.maxSize) {
                delay(REAL_FS_SETTLE_POLL_MS)
            }
        }
    }
}

private const val REAL_FS_SETTLE_POLL_MS = 10L

internal fun ktorHeaders(vararg values: Pair<String, String>): Headers = HeadersBuilder().apply {
    values.forEach { append(it.first, it.second) }
}.build()

internal fun artworkIdentity(name: String): ArtworkIdentity = ArtworkIdentity(
    key = "artwork-v1-$name",
    url = "https://example.test/$name.png",
    serverId = null,
)

internal fun artworkBytes(size: Int, value: Byte = 1): ByteArray = ByteArray(size) { value }

internal fun reusableHeaders(): Headers = HeadersBuilder().apply {
    append("Content-Type", "image/png")
    append("Cache-Control", "max-age=3600")
}.build()

internal fun Path.child(name: String): Path = (toString() + "/" + name).toPath()
