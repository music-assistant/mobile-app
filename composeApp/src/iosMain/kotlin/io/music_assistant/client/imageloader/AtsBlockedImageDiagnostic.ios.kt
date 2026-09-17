@file:OptIn(ExperimentalForeignApi::class)

package io.music_assistant.client.imageloader

import co.touchlab.kermit.Logger
import coil3.EventListener
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import io.ktor.client.engine.darwin.DarwinHttpRequestException
import kotlinx.atomicfu.atomic
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSURLErrorAppTransportSecurityRequiresSecureConnection
import platform.Foundation.NSURLErrorDomain

// Process-wide so recreating the image loader does not repeat the warning.
private val hasReportedAtsBlock = atomic(false)
private val logger = Logger.withTag("ImageLoader")
private const val MAX_CAUSE_DEPTH = 8

internal actual fun createAtsBlockedImageEventListener(): EventListener = object : EventListener() {
    override fun onError(request: ImageRequest, result: ErrorResult) {
        if (isAtsBlockedImageError(result.throwable) && hasReportedAtsBlock.compareAndSet(false, true)) {
            logger.w {
                "An image request was blocked by iOS App Transport Security (ATS). " +
                    "Check the image server's HTTPS configuration or the app's ATS exceptions."
            }
        }
    }
}

internal fun isAtsBlockedImageError(error: Throwable): Boolean =
    generateSequence(error) { it.cause }.take(MAX_CAUSE_DEPTH).any {
        val nativeError = (it as? DarwinHttpRequestException)?.origin
        nativeError?.domain == NSURLErrorDomain &&
            nativeError?.code == NSURLErrorAppTransportSecurityRequiresSecureConnection
    }
