// Drawing geometry / hex color literals — values are inherent to the painter's visual design.
@file:Suppress("MagicNumber")

package io.music_assistant.client.ui.compose.common.painters

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter

private class PlaceholderPainter(
    private val backgroundColor: Color = Color(0xFFE8E8E8),
    private val iconColor: Color = Color(0xFF9E9E9E),
    private val iconPainter: Painter,
) : Painter() {
    override val intrinsicSize: Size = Size.Unspecified

    override fun DrawScope.onDraw() {
        val canvasWidth = size.width
        val canvasHeight = size.height

        // Draw background
        drawRect(
            color = backgroundColor,
            size = Size(canvasWidth, canvasHeight),
        )

        // Draw icon centered
        val iconSize = minOf(canvasWidth, canvasHeight) * 0.5f
        val offsetX = (canvasWidth - iconSize) / 2
        val offsetY = (canvasHeight - iconSize) / 2

        translate(left = offsetX, top = offsetY) {
            with(iconPainter) {
                draw(
                    size = Size(iconSize, iconSize),
                    colorFilter = ColorFilter.tint(iconColor),
                )
            }
        }
    }
}

@Composable
fun rememberPlaceholderPainter(
    backgroundColor: Color = Color(0xFFE8E8E8),
    iconColor: Color = Color(0xFF9E9E9E),
    icon: ImageVector,
): Painter {
    val vectorPainter = rememberVectorPainter(icon)
    return remember(backgroundColor, iconColor, vectorPainter) {
        PlaceholderPainter(
            backgroundColor = backgroundColor,
            iconColor = iconColor,
            iconPainter = vectorPainter,
        )
    }
}
