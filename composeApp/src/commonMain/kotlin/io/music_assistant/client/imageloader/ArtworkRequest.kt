package io.music_assistant.client.imageloader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import coil3.PlatformContext
import coil3.compose.LocalPlatformContext
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import io.music_assistant.client.utils.disableHardwareBitmaps

/**
 * Pixel size every artwork is decoded at on color surfaces. Comfortably above kmpalette's internal
 * ~112x112 quantization area, so the extracted palette is identical regardless of how large the
 * artwork is drawn — and large enough that the hero now-playing art stays crisp.
 */
const val ARTWORK_DECODE_SIZE = 512

private fun artworkCacheKey(url: String) = "artwork@$ARTWORK_DECODE_SIZE:$url"

/**
 * Canonical Coil request driving BOTH artwork display and on-device color extraction. A single
 * fixed decode size plus a shared memory-cache key means Coil decodes the bitmap exactly once and
 * serves it to the AsyncImage and the palette extractor alike (no second decode). The fixed size
 * makes the palette deterministic — independent of the widget's on-screen size and of which
 * surface loaded first. Software bitmaps are forced so the extractor can read pixels.
 */
fun artworkImageRequest(context: PlatformContext, url: String): ImageRequest =
    ImageRequest.Builder(context)
        .data(url)
        .size(ARTWORK_DECODE_SIZE)
        .disableHardwareBitmaps()
        .memoryCacheKey(artworkCacheKey(url))
        .memoryCachePolicy(CachePolicy.ENABLED)
        .build()

/**
 * Compose entry point for displaying artwork: the model every artwork `AsyncImage` should use so
 * its decode is shared with color extraction. Returns null for a null [url] (AsyncImage then shows
 * its placeholder/fallback). Remembered per url so the request isn't rebuilt each recomposition.
 */
@Composable
fun rememberArtworkRequest(url: String?): ImageRequest? {
    val context = LocalPlatformContext.current
    return remember(url, context) { url?.let { artworkImageRequest(context, it) } }
}
