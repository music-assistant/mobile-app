package io.music_assistant.client.ui.compose.common.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val BookAudioIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "BookAudio",
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
            // Audio bars
            moveTo(12f, 6f)
            verticalLineToRelative(7f)
            moveTo(16f, 8f)
            verticalLineToRelative(3f)
            moveTo(8f, 8f)
            verticalLineToRelative(3f)
            // Book shape
            moveTo(4f, 19.5f)
            verticalLineToRelative(-15f)
            arcTo(2.5f, 2.5f, 0f, false, true, 6.5f, 2f)
            horizontalLineTo(19f)
            arcTo(1f, 1f, 0f, false, true, 20f, 3f)
            verticalLineToRelative(18f)
            arcTo(1f, 1f, 0f, false, true, 19f, 22f)
            horizontalLineTo(6.5f)
            arcTo(1f, 1f, 0f, false, true, 6.5f, 17f)
            horizontalLineTo(20f)
        }
    }.build()
}
