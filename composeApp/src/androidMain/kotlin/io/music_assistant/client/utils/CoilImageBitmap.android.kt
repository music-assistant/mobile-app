package io.music_assistant.client.utils

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import coil3.Image
import coil3.toBitmap

// The request disables hardware bitmaps upstream, so toBitmap() yields a CPU-readable bitmap.
internal actual fun Image.toImageBitmap(): ImageBitmap? =
    runCatching { toBitmap().asImageBitmap() }.getOrNull()
