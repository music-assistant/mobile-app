package io.music_assistant.client.ui.compose.common.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val ShuffleOffIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "ShuffleOff",
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
            // Bottom line
            moveTo(21f, 17f)
            horizontalLineToRelative(-18f)
            // Top arrow head
            moveTo(18f, 4f)
            lineToRelative(3f, 3f)
            lineToRelative(-3f, 3f)
            // Bottom arrow head
            moveTo(18f, 20f)
            lineToRelative(3f, -3f)
            lineToRelative(-3f, -3f)
            // Top line
            moveTo(21f, 7f)
            horizontalLineToRelative(-18f)
        }
    }.build()
}
