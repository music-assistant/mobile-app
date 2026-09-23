package io.music_assistant.client.imageloader

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.svg.SvgDecoder
import okio.Path

private const val ARTWORK_DISK_CACHE_MAX_BYTES = 256L * 1024L * 1024L

internal fun buildArtworkDiskCache(context: PlatformContext): DiskCache =
    DiskCache.Builder()
        .directory(imageDiskCacheDir(context))
        .maxSizeBytes(ARTWORK_DISK_CACHE_MAX_BYTES)
        .build()

internal fun buildAppImageLoader(
    context: PlatformContext,
    repository: ArtworkRepository,
): ImageLoader =
    ImageLoader.Builder(context)
        // ArtworkRepository owns the persistent cache; never create Coil's lazy disk cache.
        .diskCache(null)
        .memoryCache {
            MemoryCache.Builder().maxSizePercent(context).build()
        }
        .components {
            add(ArtworkResolvingInterceptor(repository))
            add(ArtworkKeyer())
            add(ArtworkPayloadFetcher.Factory())
            add(SvgDecoder.Factory())
        }
        .build()

internal expect fun imageDiskCacheDir(context: PlatformContext): Path
