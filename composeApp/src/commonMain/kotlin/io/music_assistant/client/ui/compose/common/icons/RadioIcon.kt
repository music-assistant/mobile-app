package io.music_assistant.client.ui.compose.common.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val RadioIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Radio",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        // Right inner arc
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(16.247f, 7.761f)
            arcTo(6f, 6f, 0f, false, true, 16.247f, 16.239f)
        }
        // Right outer arc
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(19.075f, 4.933f)
            arcTo(10f, 10f, 0f, false, true, 19.075f, 19.067f)
        }
        // Left outer arc
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(4.925f, 19.067f)
            arcTo(10f, 10f, 0f, false, true, 4.925f, 4.933f)
        }
        // Left inner arc
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(7.753f, 16.239f)
            arcTo(6f, 6f, 0f, false, true, 7.753f, 7.761f)
        }
        // Center dot
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(14f, 12f)
            arcTo(2f, 2f, 0f, true, true, 10f, 12f)
            arcTo(2f, 2f, 0f, true, true, 14f, 12f)
        }
    }.build()
}
