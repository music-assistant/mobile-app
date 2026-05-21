package io.music_assistant.client.imageloader

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.Uri
import coil3.disk.DiskCache
import coil3.fetch.Fetcher
import coil3.memory.MemoryCache
import coil3.svg.SvgDecoder
import okio.Path

internal fun buildAppImageLoader(
    context: PlatformContext,
    webrtcFetcherFactory: Fetcher.Factory<Uri>? = null,
): ImageLoader =
    ImageLoader.Builder(context)
        .memoryCache {
            // Coil's default is ~20% of available memory; we leave the default in place but
            // build it explicitly so it survives any future builder reset.
            MemoryCache.Builder().maxSizePercent(context).build()
        }
        .diskCache {
            // Persist proxied (`mawebrtc://`) artwork across cold starts. Stable URIs
            // (`checksum=""` in `buildWebRTCImageProxyUrl`) make this near-100% hit-rate
            // for revisited screens. Direct-HTTPS artwork benefits too.
            DiskCache.Builder()
                .directory(imageDiskCacheDir(context))
                .maxSizeBytes(DISK_CACHE_MAX_BYTES)
                .build()
        }
        .components {
            webrtcFetcherFactory?.let { add(it) }
            add(SvgDecoder.Factory())
        }
        .build()

internal expect fun imageDiskCacheDir(context: PlatformContext): Path

private const val DISK_CACHE_MAX_BYTES = 256L * 1024L * 1024L
