package io.music_assistant.client.ui.compose.common.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val PauseIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Pause",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color.Black),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            // Right bar
            moveTo(15f, 3f)
            horizontalLineToRelative(3f)
            arcToRelative(1f, 1f, 0f, false, true, 1f, 1f)
            verticalLineToRelative(16f)
            arcToRelative(1f, 1f, 0f, false, true, -1f, 1f)
            horizontalLineToRelative(-3f)
            arcToRelative(1f, 1f, 0f, false, true, -1f, -1f)
            verticalLineTo(4f)
            arcToRelative(1f, 1f, 0f, false, true, 1f, -1f)
            close()
            // Left bar
            moveTo(6f, 3f)
            horizontalLineToRelative(3f)
            arcToRelative(1f, 1f, 0f, false, true, 1f, 1f)
            verticalLineToRelative(16f)
            arcToRelative(1f, 1f, 0f, false, true, -1f, 1f)
            horizontalLineTo(6f)
            arcToRelative(1f, 1f, 0f, false, true, -1f, -1f)
            verticalLineTo(4f)
            arcToRelative(1f, 1f, 0f, false, true, 1f, -1f)
            close()
        }
    }.build()
}
