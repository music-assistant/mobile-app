package io.music_assistant.client.utils

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import coil3.Image
import coil3.compose.ImagePainter

/**
 * Rasterize a Coil [Image] into a Compose [ImageBitmap] using only commonMain primitives.
 * Used to bridge Coil's decoded bitmap into kmpalette without expect/actual.
 * Allocates a one-shot duplicate bitmap; intended for offline analysis (palette extraction),
 * not for hot-path display use.
 */
internal fun Image.toImageBitmap(): ImageBitmap? {
    val w = width.takeIf { it > 0 } ?: return null
    val h = height.takeIf { it > 0 } ?: return null
    val target = ImageBitmap(w, h)
    val canvas = Canvas(target)
    val painter = ImagePainter(this)
    val size = Size(w.toFloat(), h.toFloat())
    CanvasDrawScope().draw(
        density = Density(1f),
        layoutDirection = LayoutDirection.Ltr,
        canvas = canvas,
        size = size,
    ) {
        with(painter) { draw(size) }
    }
    return target
}
