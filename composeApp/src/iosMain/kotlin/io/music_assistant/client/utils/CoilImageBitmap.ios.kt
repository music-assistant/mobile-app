package io.music_assistant.client.utils

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import coil3.Image
import coil3.toBitmap

// Skia bitmaps are always CPU-readable; asComposeImageBitmap wraps it so kmpalette can readPixels.
internal actual fun Image.toImageBitmap(): ImageBitmap? =
    runCatching { toBitmap().asComposeImageBitmap() }.getOrNull()
