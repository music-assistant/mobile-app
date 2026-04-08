package io.music_assistant.client.ui.compose.common.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val RepeatOnIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "RepeatOn",
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
            // Top path
            moveTo(4f, 12f)
            verticalLineToRelative(-3f)
            arcTo(3f, 3f, 0f, false, true, 7f, 6f)
            horizontalLineToRelative(13f)
            moveTo(17f, 3f)
            lineToRelative(3f, 3f)
            lineToRelative(-3f, 3f)
            // Bottom path
            moveTo(20f, 12f)
            verticalLineToRelative(3f)
            arcTo(3f, 3f, 0f, false, true, 17f, 18f)
            horizontalLineTo(4f)
            moveTo(7f, 21f)
            lineToRelative(-3f, -3f)
            lineToRelative(3f, -3f)
        }
    }.build()
}
