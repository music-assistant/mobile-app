package io.music_assistant.client.wear.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import co.touchlab.kermit.Logger
import com.materialkolor.quantize.QuantizerCelebi
import com.materialkolor.score.Score
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * A decoded cover plus the colour the rest of the screen is themed from.
 *
 * Both come out of the same fetch: the seed is scored from a thumbnail decode, so the
 * quantizer never walks a full-size image on a watch CPU.
 */
data class Artwork(
    val image: ImageBitmap,
    /** ARGB seed for the colour scheme, or null when the cover yields nothing usable. */
    val seedArgb: Int?,
)

/**
 * Fetches and decodes cover art.
 *
 * The watch shows one cover at a time, so this is a plain fetch-and-decode behind a small
 * LRU rather than a full image pipeline. The server's imageproxy needs no token.
 */
class ArtworkLoader {
    private val logger = Logger.withTag("WearArtwork")

    private val client = HttpClient(OkHttp) {
        install(HttpTimeout) {
            requestTimeoutMillis = REQUEST_TIMEOUT_MS
            connectTimeoutMillis = CONNECT_TIMEOUT_MS
        }
    }

    private val cache = object : LinkedHashMap<String, Artwork>(0, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, Artwork>): Boolean =
            size > CACHE_ENTRIES
    }
    private val cacheLock = Mutex()

    /**
     * Thumbnails for list rows, which are a different job from the cover behind the screen:
     * decoded small, never scored for a colour, and kept by the screenful rather than one at
     * a time. A miss is cached as a null so that a cover the server does not have is not
     * fetched again every time its row scrolls back into view.
     */
    private val thumbnails = object : LinkedHashMap<String, ImageBitmap?>(0, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, ImageBitmap?>): Boolean =
            size > THUMBNAIL_ENTRIES
    }
    private val thumbnailLock = Mutex()

    suspend fun load(url: String): Artwork? {
        cacheLock.withLock { cache[url] }?.let { return it }
        val artwork = fetch(url) ?: return null
        cacheLock.withLock { cache[url] = artwork }
        return artwork
    }

    /** A row-sized cover, or null when there is none to be had. */
    suspend fun loadThumbnail(url: String): ImageBitmap? {
        thumbnailLock.withLock { if (thumbnails.containsKey(url)) return thumbnails[url] }
        val image = fetchBytes(url)
            ?.let { decode(it, THUMBNAIL_SIZE) }
            ?.asImageBitmap()
        thumbnailLock.withLock { thumbnails[url] = image }
        return image
    }

    private suspend fun fetch(url: String): Artwork? {
        val bytes = fetchBytes(url) ?: return null
        return withContext(Dispatchers.IO) {
            val display = decode(bytes, DISPLAY_SIZE) ?: return@withContext null
            val seed = decode(bytes, SEED_SIZE)?.let(::seedArgb)
            Artwork(display.asImageBitmap(), seed)
        }
    }

    private suspend fun fetchBytes(url: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val response: HttpResponse = client.get(url)
            if (!response.status.isSuccess()) {
                logger.w { "Artwork $url: HTTP ${response.status.value}" }
                return@withContext null
            }
            val bytes: ByteArray = response.body()
            if (bytes.isEmpty() || bytes.size > MAX_BYTES) {
                logger.w { "Artwork $url: ${bytes.size} bytes rejected" }
                return@withContext null
            }
            bytes
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            logger.w { "Artwork $url failed: ${e.message}" }
            null
        }
    }

    /** Decodes with the smallest power-of-two subsample that keeps the long edge under [maxEdge]. */
    private fun decode(bytes: ByteArray, maxEdge: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val longEdge = maxOf(bounds.outWidth, bounds.outHeight)
        val options = BitmapFactory.Options().apply {
            inSampleSize = generateSequence(1) { it * 2 }.first { longEdge / it <= maxEdge }
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    /**
     * The cover's most theme-worthy colour, by the same quantize-and-score pass Material You
     * uses on a wallpaper. Null when the image is empty or scores nothing.
     */
    private fun seedArgb(thumbnail: Bitmap): Int? {
        if (thumbnail.width <= 0 || thumbnail.height <= 0) return null
        val pixels = IntArray(thumbnail.width * thumbnail.height)
        thumbnail.getPixels(pixels, 0, thumbnail.width, 0, 0, thumbnail.width, thumbnail.height)
        val quantized = QuantizerCelebi.quantize(pixels, QUANTIZE_COLORS)
        return Score.score(quantized, desired = 1, fallbackColorArgb = null).firstOrNull()
    }

    private companion object {
        const val REQUEST_TIMEOUT_MS = 15_000L
        const val CONNECT_TIMEOUT_MS = 8_000L
        const val MAX_BYTES = 8 * 1024 * 1024
        const val DISPLAY_SIZE = 512
        const val SEED_SIZE = 64
        const val THUMBNAIL_SIZE = 96
        const val QUANTIZE_COLORS = 64
        const val CACHE_ENTRIES = 8
        const val THUMBNAIL_ENTRIES = 64
        const val LOAD_FACTOR = 0.75f
    }
}
