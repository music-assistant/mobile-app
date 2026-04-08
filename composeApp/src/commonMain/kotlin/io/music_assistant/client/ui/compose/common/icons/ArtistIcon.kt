package io.music_assistant.client.ui.compose.common.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val ArtistIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Artist",
        defaultWidth = 18.dp,
        defaultHeight = 18.dp,
        viewportWidth = 26f,
        viewportHeight = 24f
    ).apply {
        // Person
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            // Head circle
            moveTo(12f, 13f)
            curveTo(14.7614f, 13f, 17f, 10.7614f, 17f, 8f)
            curveTo(17f, 5.2386f, 14.7614f, 3f, 12f, 3f)
            curveTo(9.2386f, 3f, 7f, 5.2386f, 7f, 8f)
            curveTo(7f, 10.7614f, 9.2386f, 13f, 12f, 13f)
            close()
            // Body arcs
            moveTo(12f, 13f)
            curveTo(14.1217f, 13f, 16.1566f, 13.8429f, 17.6569f, 15.3431f)
            moveTo(12f, 13f)
            curveTo(9.8783f, 13f, 7.8434f, 13.8429f, 6.3431f, 15.3431f)
            curveTo(4.8429f, 16.8434f, 4f, 18.8783f, 4f, 21f)
        }
        // Music note
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            // Note head
            moveTo(21f, 21f)
            curveTo(21f, 22.1046f, 20.1046f, 23f, 19f, 23f)
            curveTo(17.8954f, 23f, 17f, 22.1046f, 17f, 21f)
            curveTo(17f, 19.8954f, 17.8954f, 19f, 19f, 19f)
            curveTo(20.1046f, 19f, 21f, 19.8954f, 21f, 21f)
            close()
            // Stem and flag
            moveTo(21f, 21f)
            verticalLineTo(13f)
            lineTo(24.5f, 15f)
        }
    }.build()
}
