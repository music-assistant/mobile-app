package io.music_assistant.client.ui.compose.common.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val PlayIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Play",
        defaultWidth = 30.dp,
        defaultHeight = 30.dp,
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
            moveTo(5f, 5f)
            arcToRelative(2f, 2f, 0f, false, true, 3.008f, -1.728f)
            lineToRelative(11.997f, 6.998f)
            arcToRelative(2f, 2f, 0f, false, true, 0.003f, 3.458f)
            lineToRelative(-12f, 7f)
            arcTo(2f, 2f, 0f, false, true, 5f, 19f)
            close()
        }
    }.build()
}
