package io.music_assistant.client.ui.compose.common

import androidx.collection.LruCache
import androidx.lifecycle.ViewModel
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.kmpalette.generatePalette
import io.music_assistant.client.ui.compose.common.DominantColorViewModel.Companion.MAX_CACHE_SIZE
import io.music_assistant.client.utils.disableHardwareBitmaps
import io.music_assistant.client.utils.toImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * App-wide cache of extracted dominant colors keyed by image URL.
 * Pulls bitmaps from Coil's unified memory cache, runs kmpalette on the result,
 * pre-computes both light- and dark-surface tint variants so callers pay no
 * per-recomposition extraction or HSL-readjustment cost.
 *
 * LRU eviction at [MAX_CACHE_SIZE]; misses are silent (caller falls back).
 * Concurrent calls for the same URL may extract twice; result is identical so
 * last-writer-wins is harmless.
 */
class DominantColorViewModel : ViewModel() {
    private val cache = LruCache<String, ExtractedColors>(MAX_CACHE_SIZE)

    suspend fun getColors(context: PlatformContext, imageUrl: String): ExtractedColors? {
        cache[imageUrl]?.let { return it }
        val extracted = withContext(Dispatchers.Default) {
            runCatching { extract(context, imageUrl) }.getOrNull()
        } ?: return null
        cache.put(imageUrl, extracted)
        return extracted
    }

    private suspend fun extract(context: PlatformContext, url: String): ExtractedColors? {
        val request = ImageRequest.Builder(context)
            .data(url)
            .disableHardwareBitmaps()
            .memoryCachePolicy(CachePolicy.ENABLED)
            .memoryCacheKey(url)
            .build()
        val result = SingletonImageLoader.get(context).execute(request) as? SuccessResult
            ?: return null
        val bitmap = result.image.toImageBitmap() ?: return null
        val palette = bitmap.generatePalette()
        val color = palette.getBestColor()
        return if (color != null) {
            ExtractedColors(
                dominant = color,
                tintOnDark = color.ensureReadable(onDarkSurface = true),
                tintOnLight = color.ensureReadable(onDarkSurface = false),
            )
        } else {
            null
        }
    }

    private companion object {
        const val MAX_CACHE_SIZE = 200
    }
}
