package io.music_assistant.client.ui.compose.common.items

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Shape that cuts a vertical strip from the right side.
 * Used for album vinyl record effect.
 */
class CutStripShape(private val stripWidth: Dp) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        return Outline.Generic(Path().apply {
            val stripPx = with(density) { stripWidth.toPx() }

            // Defines the album cover area, excluding the rightmost strip
            moveTo(0f, 0f)
            lineTo(size.width - stripPx, 0f)
            lineTo(size.width - stripPx, size.height)
            lineTo(0f, size.height)
            close()
        })
    }
}

/**
 * Shape that cuts a circular hole in the center.
 * Used for album vinyl record effect.
 */
class HoleShape(private val holeRadius: Dp) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val radiusPx = with(density) { holeRadius.toPx() }
        val center = size.width / 2f

        // Define the full cover path
        val coverPath = Path().apply {
            addRect(Rect(Offset.Zero, size))
        }

        // Define the hole path
        val holePath = Path().apply {
            val rect = Rect(
                left = center - radiusPx,
                top = center - radiusPx,
                right = center + radiusPx,
                bottom = center + radiusPx
            )
            addOval(oval = rect)
        }

        // Subtract hole from cover
        val finalPath = Path.combine(
            operation = PathOperation.Difference,
            path1 = coverPath,
            path2 = holePath
        )

        return Outline.Generic(finalPath)
    }
}

/**
 * Shape that cuts a vertical strip from the left side.
 * Used for notebook/playlist spiral binding effect.
 */
class NotebookCutShape(private val stripWidth: Dp) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        return Outline.Generic(Path().apply {
            val stripPx = with(density) { stripWidth.toPx() }

            // Defines the content area, excluding the leftmost strip
            moveTo(stripPx, 0f)
            lineTo(size.width, 0f)
            lineTo(size.width, size.height)
            lineTo(stripPx, size.height)
            close()
        })
    }
}

/**
 * Shape that cuts the top-left corner.
 * Used for podcast concentric circles effect.
 */
class CornerCutShape(private val cutSize: Dp) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        return Outline.Generic(Path().apply {
            val cutPx = with(density) { cutSize.toPx() }

            // Start from top-left corner after the cut
            moveTo(cutPx, 0f)
            lineTo(size.width, 0f)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            lineTo(0f, cutPx)
            close()
        })
    }
}

/**
 * Shape that cuts a vertical strip from the left with a rounded spine.
 * Used for audiobook items to resemble a book cover.
 */
class BookSpineShape(private val spineWidth: Dp) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        return Outline.Generic(Path().apply {
            val spinePx = with(density) { spineWidth.toPx() }
            val cornerRadius = spinePx / 2f

            // Content area excluding the spine strip on the left
            moveTo(spinePx, 0f)
            lineTo(size.width, 0f)
            lineTo(size.width, size.height)
            lineTo(spinePx, size.height)
            close()
        })
    }
}

/**
 * Wavy hexagon shape with sinusoidal sides.
 * Used for radio station items.
 */
class WavyHexagonShape : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        return Outline.Generic(Path().apply {
            val width = size.width
            val height = size.height
            val centerX = width / 2f
            val centerY = height / 2f
            val radius = minOf(width, height) / 2f

            // Calculate 6 points of hexagon
            val points = List(6) { i ->
                val angle = (i * 60f - 30f) * (PI / 180f).toFloat()
                Offset(
                    centerX + radius * cos(angle),
                    centerY + radius * sin(angle)
                )
            }

            // Start at first point
            moveTo(points[0].x, points[0].y)

            // Draw sinusoidal edges between points
            for (i in 0 until 6) {
                val current = points[i]
                val next = points[(i + 1) % 6]

                // Create sinusoidal wave along the edge
                val segments = 20 // Number of line segments for the sine wave
                val amplitude = radius * 0.04f // Wave amplitude
                val frequency = 3.0 // Number of complete waves per edge

                for (j in 1..segments) {
                    val t = j.toFloat() / segments

                    // Linear interpolation along the edge
                    val baseX = current.x + (next.x - current.x) * t
                    val baseY = current.y + (next.y - current.y) * t

                    // Calculate perpendicular offset for sine wave
                    val edgeAngle = atan2(next.y - current.y, next.x - current.x)
                    val perpAngle = edgeAngle + PI.toFloat() / 2f
                    val waveOffset = amplitude * sin((t * frequency * 2.0 * PI).toFloat())

                    val x = baseX + waveOffset * cos(perpAngle)
                    val y = baseY + waveOffset * sin(perpAngle)

                    lineTo(x, y)
                }
            }

            close()
        })
    }
}
