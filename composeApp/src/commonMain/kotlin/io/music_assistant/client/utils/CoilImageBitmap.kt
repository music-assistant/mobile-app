package io.music_assistant.client.utils

import androidx.compose.ui.graphics.ImageBitmap
import coil3.Image

/**
 * Rasterize a decoded Coil [Image] into a Compose [ImageBitmap] for offline analysis (palette
 * extraction), not hot-path display.
 *
 * Each platform routes through Coil's own `toBitmap()` plus the platform `ImageBitmap` conversion
 * — the same supported path [coil3.compose.Image.asPainter] uses for display. The previous
 * commonMain implementation drew the image through a generic [coil3.compose.ImagePainter] into an
 * offscreen canvas; on iOS that resolves to `org.jetbrains.skia.Canvas.writePixels`, which does
 * not reliably blit into an offscreen Compose canvas, so extraction silently produced a blank
 * bitmap (artwork still displayed, but colors fell back). Returns null if conversion fails.
 */
internal expect fun Image.toImageBitmap(): ImageBitmap?
