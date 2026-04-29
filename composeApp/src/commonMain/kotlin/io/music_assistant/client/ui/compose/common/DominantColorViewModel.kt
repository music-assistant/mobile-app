package io.music_assistant.client.ui.compose.common

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.kmpalette.generatePalette
import io.music_assistant.client.utils.disableHardwareBitmaps
import io.music_assistant.client.utils.toImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * App-wide cache of extracted dominant colors keyed by image URL.
 * Pulls bitmaps from Coil's unified memory cache, runs kmpalette on the result,
 * pre-computes both light- and dark-surface tint variants so callers pay no
 * per-recomposition extraction or HSL-readjustment cost.
 *
 * FIFO eviction at [MAX_CACHE_SIZE]; misses are silent (caller falls back).
 */
class DominantColorViewModel : ViewModel() {
    data class ExtractedColors(
        val dominant: Color,
        val tintOnDark: Color,
        val tintOnLight: Color,
    )

    private val mutex = Mutex()
    private val cache = LinkedHashMap<String, ExtractedColors>()

    suspend fun getColors(context: PlatformContext, imageUrl: String): ExtractedColors? {
        mutex.withLock { cache[imageUrl] }?.let { return it }
        val extracted = withContext(Dispatchers.Default) {
            runCatching { extract(context, imageUrl) }.getOrNull()
        } ?: return null
        mutex.withLock {
            cache[imageUrl] = extracted
            while (cache.size > MAX_CACHE_SIZE) {
                cache.remove(cache.keys.iterator().next())
            }
        }
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
        val dominantColor = palette.swatches
            .maxByOrNull { it.population }
            ?.let { Color(it.rgb) }
            ?: return null
        return ExtractedColors(
            dominant = dominantColor,
            tintOnDark = dominantColor.ensureReadable(onDarkSurface = true),
            tintOnLight = dominantColor.ensureReadable(onDarkSurface = false),
        )
    }

    private companion object {
        const val MAX_CACHE_SIZE = 200
    }
}
