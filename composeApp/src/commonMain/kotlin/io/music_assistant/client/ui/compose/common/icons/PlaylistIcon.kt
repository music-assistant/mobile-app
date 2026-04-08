package io.music_assistant.client.ui.compose.common.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val PlaylistIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Playlist",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
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
            // List lines
            moveTo(16f, 5f)
            horizontalLineTo(3f)
            moveTo(11f, 12f)
            horizontalLineTo(3f)
            moveTo(11f, 19f)
            horizontalLineTo(3f)
            // Note stem
            moveTo(21f, 16f)
            verticalLineTo(5f)
        }
        // Note head
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(21f, 16f)
            arcTo(3f, 3f, 0f, true, true, 15f, 16f)
            arcTo(3f, 3f, 0f, true, true, 21f, 16f)
        }
    }.build()
}
