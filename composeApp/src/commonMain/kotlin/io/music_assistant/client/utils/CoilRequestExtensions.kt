package io.music_assistant.client.utils

import coil3.request.ImageRequest

/**
 * Force software (CPU-readable) bitmaps on platforms that distinguish.
 * On Android, this disables hardware bitmaps — required when downstream code
 * reads pixels (palette extraction, MediaSession metadata). On iOS this is a no-op
 * since Skia bitmaps are always CPU-readable.
 */
internal expect fun ImageRequest.Builder.disableHardwareBitmaps(): ImageRequest.Builder
