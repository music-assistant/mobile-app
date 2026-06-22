package io.music_assistant.client.ui.compose.common

import androidx.collection.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
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

    /** Synchronous cache hit, or null on miss. Lets callers apply a known color without animating. */
    fun peekColors(imageUrl: String): ExtractedColors? = cache[imageUrl]

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
        val result = SingletonImageLoader.get(context).execute(request) as? SuccessResult ?: return null
        val bitmap = result.image.toImageBitmap() ?: return null
        val filteredPalette = bitmap.generatePalette {
            maximumColorCount(QUANTIZE_COLORS)
        }
        val unfilteredPalette = bitmap.generatePalette {
            maximumColorCount(QUANTIZE_COLORS)
            // Keep a fallback path for genuinely black/white/monochrome covers. kmpalette's
            // default filter rejects near-black/near-white pixels; without this fallback those
            // covers can produce no local colors at all.
            clearFilters()
        }
        val filteredSwatches = filteredPalette.swatches.sortedByDescending { it.population }
        val swatches = filteredSwatches.ifEmpty { unfilteredPalette.swatches.sortedByDescending { it.population } }
        val paletteCandidates = swatches.map { it.rgb.toRgbColor() } // approximates MMCQ's dominant-first order
        val pixelAccentCandidates = bitmap.brightChromaticCandidates()
        // Keep the dominant palette swatch first, then allow bright pixel accents to compete
        // before lower-ranked quantized swatches.
        val candidates = (
            paletteCandidates.take(1) +
                pixelAccentCandidates +
                paletteCandidates.drop(1)
            ).distinct()
        val meaningful = meaningfulCandidates(candidates)
        val derived = derivePalette(meaningful)
        return derived.toExtractedColors()
    }

    private fun ImageBitmap.brightChromaticCandidates(): List<RgbColor> {
        val pixelMap = toPixelMap()
        val buckets = linkedMapOf<RgbColor, Int>()
        val stepX = (width / PIXEL_SAMPLE_TARGET).coerceAtLeast(1)
        val stepY = (height / PIXEL_SAMPLE_TARGET).coerceAtLeast(1)
        for (y in 0 until height step stepY) {
            for (x in 0 until width step stepX) {
                val color = pixelMap[x, y]
                val r = (color.red * RGB_MAX).toInt().coerceIn(0, RGB_MAX)
                val g = (color.green * RGB_MAX).toInt().coerceIn(0, RGB_MAX)
                val b = (color.blue * RGB_MAX).toInt().coerceIn(0, RGB_MAX)
                val max = maxOf(r, g, b)
                val min = minOf(r, g, b)
                if (max < BRIGHT_PIXEL_MIN_CHANNEL || max - min < BRIGHT_PIXEL_MIN_CHROMA) continue
                val bucket = RgbColor(
                    (r / PIXEL_BUCKET_SIZE) * PIXEL_BUCKET_SIZE,
                    (g / PIXEL_BUCKET_SIZE) * PIXEL_BUCKET_SIZE,
                    (b / PIXEL_BUCKET_SIZE) * PIXEL_BUCKET_SIZE,
                )
                buckets[bucket] = (buckets[bucket] ?: 0) + 1
            }
        }
        return buckets.entries
            .sortedByDescending { it.value }
            .take(MAX_PIXEL_ACCENT_CANDIDATES)
            .map { it.key }
    }

    // Swatch.rgb is a packed ARGB ColorInt; drop alpha and split channels.
    @Suppress("MagicNumber")
    private fun Int.toRgbColor() = RgbColor((this shr 16) and 0xFF, (this shr 8) and 0xFF, this and 0xFF)

    private companion object {
        const val MAX_CACHE_SIZE = 200
        const val RGB_MAX = 255
        const val PIXEL_SAMPLE_TARGET = 128
        const val PIXEL_BUCKET_SIZE = 16
        const val BRIGHT_PIXEL_MIN_CHANNEL = 160
        const val BRIGHT_PIXEL_MIN_CHROMA = 64
        const val MAX_PIXEL_ACCENT_CANDIDATES = 3
    }
}
