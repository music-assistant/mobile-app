package io.music_assistant.client.ui.compose.common.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val TrackIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Track",
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
            // Note head
            moveTo(12f, 18f)
            arcTo(4f, 4f, 0f, true, true, 4f, 18f)
            arcTo(4f, 4f, 0f, true, true, 12f, 18f)
            // Stem and flag
            moveTo(12f, 18f)
            verticalLineTo(2f)
            lineTo(19f, 6f)
        }
    }.build()
}
