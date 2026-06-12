package io.music_assistant.client.ui.compose.common

import androidx.collection.LruCache
import androidx.lifecycle.ViewModel
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.SuccessResult
import com.kmpalette.generatePalette
import io.music_assistant.client.data.model.server.RgbColor
import io.music_assistant.client.imageloader.artworkImageRequest
import io.music_assistant.client.ui.compose.common.DominantColorViewModel.Companion.MAX_CACHE_SIZE
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
        // Shared request: same fixed size + cache key as artwork display, so Coil serves the
        // already-decoded bitmap instead of decoding a second time (see artworkImageRequest).
        val request = artworkImageRequest(context, url)
        val result = SingletonImageLoader.get(context).execute(request) as? SuccessResult
            ?: return null
        val bitmap = result.image.toImageBitmap() ?: return null
        val palette = bitmap.generatePalette {
            maximumColorCount(QUANTIZE_COLORS)
            // Match the server's modern_colorthief MMCQ, which does NO filtering. kmpalette's
            // DEFAULT_FILTER rejects near-black/near-white/near-red pixels, so black-heavy
            // artwork yields zero swatches → empty candidates → null palette → fallback color.
            clearFilters()
        }
        val candidates = palette.swatches
            .sortedByDescending { it.population } // approximates MMCQ's dominant-first order
            .map { it.rgb.toRgbColor() }
        // Exclude black/white backgrounds so a small colored figure drives the palette, but keep
        // them when the cover is wholly achromatic so we still emit a palette (never null).
        return derivePalette(meaningfulCandidates(candidates)).toExtractedColors()
    }

    // Swatch.rgb is a packed ARGB ColorInt; drop alpha and split channels.
    @Suppress("MagicNumber")
    private fun Int.toRgbColor() = RgbColor((this shr 16) and 0xFF, (this shr 8) and 0xFF, this and 0xFF)

    private companion object {
        const val MAX_CACHE_SIZE = 200
    }
}
