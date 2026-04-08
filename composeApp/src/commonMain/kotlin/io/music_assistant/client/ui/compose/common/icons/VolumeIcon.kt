package io.music_assistant.client.ui.compose.common.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val VolumeIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Volume",
        defaultWidth = 22.dp,
        defaultHeight = 22.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        // Speaker body
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(11f, 4.702f)
            arcToRelative(0.705f, 0.705f, 0f, false, false, -1.203f, -0.498f)
            lineTo(6.413f, 7.587f)
            arcTo(1.4f, 1.4f, 0f, false, true, 5.416f, 8f)
            horizontalLineTo(3f)
            arcTo(1f, 1f, 0f, false, false, 2f, 9f)
            verticalLineToRelative(6f)
            arcTo(1f, 1f, 0f, false, false, 3f, 16f)
            horizontalLineToRelative(2.416f)
            arcTo(1.4f, 1.4f, 0f, false, true, 6.413f, 16.413f)
            lineToRelative(3.383f, 3.384f)
            arcTo(0.705f, 0.705f, 0f, false, false, 11f, 19.298f)
            close()
        }
        // Inner sound wave
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(16f, 9f)
            arcTo(5f, 5f, 0f, false, true, 16f, 15f)
        }
        // Outer sound wave
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(19.364f, 18.364f)
            arcTo(9f, 9f, 0f, false, false, 19.364f, 5.636f)
        }
    }.build()
}
