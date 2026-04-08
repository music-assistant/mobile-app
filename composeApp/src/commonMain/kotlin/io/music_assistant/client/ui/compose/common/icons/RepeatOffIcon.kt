package io.music_assistant.client.ui.compose.common.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val RepeatOffIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "RepeatOff",
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
            // Top path (partial)
            moveTo(4f, 12f)
            verticalLineToRelative(-3f)
            curveTo(4f, 7.664f, 4.873f, 6.532f, 6.08f, 6.144f)
            moveTo(10f, 6f)
            horizontalLineToRelative(10f)
            moveTo(17f, 3f)
            lineToRelative(3f, 3f)
            lineToRelative(-3f, 3f)
            // Bottom path (partial)
            moveTo(20f, 12f)
            verticalLineToRelative(3f)
            arcTo(3f, 3f, 0f, false, true, 19.867f, 15.886f)
            moveTo(17.877f, 17.87f)
            arcTo(3f, 3f, 0f, false, true, 17f, 18f)
            horizontalLineTo(4f)
            moveTo(7f, 21f)
            lineToRelative(-3f, -3f)
            lineToRelative(3f, -3f)
            // Strikethrough
            moveTo(3f, 3f)
            lineToRelative(18f, 18f)
        }
    }.build()
}
