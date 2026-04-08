package io.music_assistant.client.ui.compose.common.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val SkipBackIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "SkipBack",
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
            // Triangle
            moveTo(17.971f, 4.285f)
            arcTo(2f, 2f, 0f, false, true, 21f, 6f)
            verticalLineToRelative(12f)
            arcTo(2f, 2f, 0f, false, true, 17.971f, 19.715f)
            lineToRelative(-9.997f, -5.998f)
            arcTo(2f, 2f, 0f, false, true, 7.971f, 10.285f)
            close()
            // Start bar
            moveTo(3f, 20f)
            verticalLineTo(4f)
        }
    }.build()
}
