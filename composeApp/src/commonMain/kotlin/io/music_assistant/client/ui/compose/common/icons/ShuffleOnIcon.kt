package io.music_assistant.client.ui.compose.common.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val ShuffleOnIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "ShuffleOn",
        defaultWidth = 20.dp,
        defaultHeight = 20.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            // Bottom arrow head
            moveTo(18f, 14f)
            lineToRelative(4f, 4f)
            lineToRelative(-4f, 4f)
            // Top arrow head
            moveTo(18f, 2f)
            lineToRelative(4f, 4f)
            lineToRelative(-4f, 4f)
            // Cross path (top-left to bottom-right)
            moveTo(2f, 18f)
            horizontalLineToRelative(1.973f)
            arcTo(4f, 4f, 0f, false, false, 7.273f, 16.3f)
            lineToRelative(5.454f, -8.6f)
            arcTo(4f, 4f, 0f, false, true, 16.027f, 6f)
            horizontalLineTo(22f)
            // Top-left stub
            moveTo(2f, 6f)
            horizontalLineToRelative(1.972f)
            arcTo(4f, 4f, 0f, false, true, 7.572f, 8.2f)
            // Bottom-right stub
            moveTo(22f, 18f)
            horizontalLineToRelative(-6.041f)
            arcTo(4f, 4f, 0f, false, true, 12.659f, 16.2f)
            lineToRelative(-0.359f, -0.45f)
        }
    }.build()
}
