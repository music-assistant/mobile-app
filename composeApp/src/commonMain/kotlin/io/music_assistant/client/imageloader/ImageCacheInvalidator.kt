package io.music_assistant.client.imageloader

import co.touchlab.kermit.Logger
import coil3.SingletonImageLoader
import io.music_assistant.client.player.PlatformContext

/**
 * Evicts `mawebrtc://` entries from the Coil memory cache after a WebRTC reconnect.
 *
 * Why: `WebRTCHttpProxy.cancelAll()` fails every in-flight request on transport tear-down.
 * Coil treats those as fetch errors and caches the *failure* in memory — so the broken
 * tile stays broken until something invalidates it. The disk cache is untouched (the bytes
 * we already had are still valid); we only clear the in-memory error state so visible
 * tiles re-render through the freshly-opened data channel.
 */
class ImageCacheInvalidator(private val platformContext: PlatformContext) {
    private val logger = Logger.withTag("ImageCacheInvalidator")

    fun evictWebRTCEntries() {
        val loader = SingletonImageLoader.get(toCoilPlatformContext(platformContext))
        val memoryCache = loader.memoryCache ?: return
        val toRemove = memoryCache.keys.filter { it.key.startsWith("mawebrtc://") }
        if (toRemove.isEmpty()) return
        toRemove.forEach { memoryCache.remove(it) }
        logger.i { "Evicted ${toRemove.size} mawebrtc:// entries from Coil memory cache" }
    }
}

internal expect fun toCoilPlatformContext(p: PlatformContext): coil3.PlatformContext
