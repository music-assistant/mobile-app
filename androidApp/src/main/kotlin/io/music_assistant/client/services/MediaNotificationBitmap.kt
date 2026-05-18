package io.music_assistant.client.services

import android.content.Context
import android.graphics.Bitmap
import coil3.BitmapImage
import coil3.ImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn

internal suspend fun loadCoilBitmap(
    context: Context,
    imageLoader: ImageLoader,
    url: String,
): Bitmap? = (
    (
        imageLoader.execute(
            ImageRequest.Builder(context)
                .data(url)
                .allowHardware(false)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .memoryCacheKey(url)
                .build(),
        ) as? SuccessResult
        )?.image as? BitmapImage
    )?.bitmap

/**
 * Pairs each upstream [MediaNotificationData] emission with the freshest
 * available bitmap. Bitmap loading runs on its own flow keyed by `imageUrl`;
 * a URL change cancels any in-flight fetch (via `mapLatest`) and starts a
 * new one. Slow or failing artwork never blocks downstream emissions — the
 * upstream data lands immediately paired with the last-known bitmap (or
 * `null` until the first one resolves), and a second emission replaces it
 * once the new bitmap arrives.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun Flow<MediaNotificationData>.withAsyncBitmap(
    scope: CoroutineScope,
    loadBitmap: suspend (String) -> Bitmap?,
): Flow<Pair<MediaNotificationData, Bitmap?>> {
    val bitmap = map { it.imageUrl }
        .distinctUntilChanged()
        .mapLatest { url -> url?.let { loadBitmap(it) } }
        .stateIn(scope, SharingStarted.WhileSubscribed(), null)
    return combine(this, bitmap) { data, bmp -> data to bmp }
}
