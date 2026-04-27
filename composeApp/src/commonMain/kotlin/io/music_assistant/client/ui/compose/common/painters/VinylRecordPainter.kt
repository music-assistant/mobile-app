package io.music_assistant.client.ui.compose.common.painters

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp

class VinylRecordPainter(
    private val recordColor: Color,
    private val labelColor: Color,
    private val holeColor: Color,
    private val grooveColor: Color = labelColor.copy(alpha = 0.4f), // Subtle white for grooves
    private val grooveCount: Int = 6, // Number of grooves to draw
) : Painter() {
    private var cachedSize: Size? = null
    private var cachedGrooveRadii: List<Float>? = null

    override val intrinsicSize: Size = Size.Unspecified

    override fun DrawScope.onDraw() {
        val diameter = size.minDimension
        val radius = diameter / 2f
        val center = Offset(size.width / 2f, size.height / 2f)

        drawCircle(
            color = recordColor,
            radius = radius,
            center = center,
            style = Fill,
        )

        val labelRadius = radius * 0.45f
        drawCircle(
            color = labelColor,
            radius = labelRadius,
            center = center,
            style = Fill,
        )

        val innerCircleRadius = radius * 0.38f
        drawCircle(
            color = recordColor,
            radius = innerCircleRadius,
            center = center,
            style = Fill,
        )

        drawCircle(
            color = labelColor,
            radius = innerCircleRadius * 0.8f,
            center = center,
            style = Fill,
        )

        drawCircle(
            color = holeColor,
            radius = radius * 0.08f,
            center = center,
            style = Fill,
        )

        val cache = cachedGrooveRadii
        val grooveRadii = if (cachedSize == size && cache != null) {
            cache
        } else {
            calculateGrooveRadii(labelRadius, radius).also {
                cachedSize = size
                cachedGrooveRadii = it
            }
        }

        val strokeWidth = 0.5.dp.toPx()
        for (grooveRadius in grooveRadii) {
            drawCircle(
                color = grooveColor,
                radius = grooveRadius,
                center = center,
                style = Stroke(width = strokeWidth),
            )
        }
    }

    private fun DrawScope.calculateGrooveRadii(labelRadius: Float, radius: Float): List<Float> {
        val grooveStartRadius = labelRadius + 1.dp.toPx()
        val grooveEndRadius = radius - 1.dp.toPx()

        return if (grooveEndRadius > grooveStartRadius) {
            val grooveSpacing = (grooveEndRadius - grooveStartRadius) / grooveCount
            List(grooveCount) { i ->
                grooveStartRadius + (i * grooveSpacing)
            }
        } else {
            emptyList()
        }
    }
}

@Composable
fun rememberVinylRecordPainter(
    labelColor: Color,
    backgroundColor: Color,
) = remember(backgroundColor, labelColor) {
    VinylRecordPainter(
        recordColor = Color.DarkGray,
        labelColor = labelColor,
        holeColor = backgroundColor,
    )
}
