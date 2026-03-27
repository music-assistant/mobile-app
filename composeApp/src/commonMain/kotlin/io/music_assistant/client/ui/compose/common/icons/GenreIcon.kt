package io.music_assistant.client.ui.compose.common.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val GenreIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Genre",
        defaultWidth = 52.dp,
        defaultHeight = 57.dp,
        viewportWidth = 52f,
        viewportHeight = 57f
    ).apply {
        // Stacked layers
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 4f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(49.8333f, 33.6316f)
            curveTo(49.8365f, 34.0359f, 49.722f, 34.4324f, 49.5038f, 34.7727f)
            curveTo(49.2855f, 35.113f, 48.9729f, 35.3825f, 48.6042f, 35.5482f)
            lineTo(30.7292f, 43.6732f)
            curveTo(30.1892f, 43.9178f, 29.6032f, 44.0443f, 29.0104f, 44.0443f)
            curveTo(28.4176f, 44.0443f, 27.8317f, 43.9178f, 27.2917f, 43.6732f)
            lineTo(12f, 36.7209f)

            moveTo(8.1667f, 44.0482f)
            curveTo(8.1657f, 44.4467f, 8.279f, 44.8371f, 8.4932f, 45.1731f)
            curveTo(8.7073f, 45.5092f, 9.0134f, 45.7767f, 9.375f, 45.9441f)
            lineTo(27.2917f, 54.0899f)
            curveTo(27.8317f, 54.3344f, 28.4176f, 54.4609f, 29.0104f, 54.4609f)
            curveTo(29.6032f, 54.4609f, 30.1892f, 54.3344f, 30.7292f, 54.0899f)
            lineTo(48.6042f, 45.9649f)
            curveTo(48.9729f, 45.7992f, 49.2855f, 45.5297f, 49.5038f, 45.1894f)
            curveTo(49.722f, 44.849f, 49.8365f, 44.4525f, 49.8333f, 44.0482f)

            moveTo(19f, 29.478f)
            lineTo(27.2917f, 33.2566f)
            curveTo(27.8345f, 33.5042f, 28.4242f, 33.6323f, 29.0208f, 33.6323f)
            curveTo(29.6175f, 33.6323f, 30.2072f, 33.5042f, 30.75f, 33.2566f)
            lineTo(48.625f, 25.1316f)
            curveTo(48.9947f, 24.9686f, 49.309f, 24.7016f, 49.5297f, 24.3631f)
            curveTo(49.7503f, 24.0247f, 49.8678f, 23.6294f, 49.8678f, 23.2253f)
            curveTo(49.8678f, 22.8213f, 49.7503f, 22.426f, 49.5297f, 22.0875f)
            curveTo(49.309f, 21.7491f, 48.9947f, 21.4821f, 48.625f, 21.3191f)
            lineTo(30.7292f, 13.1732f)
            curveTo(30.1863f, 12.9256f, 29.5966f, 12.7975f, 29f, 12.7975f)
            curveTo(28.4034f, 12.7975f, 27.8137f, 12.9256f, 27.2708f, 13.1732f)
            lineTo(22f, 15.6316f)
        }
        // Music note
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 4f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(13.2f, 24.1053f)
            curveTo(13.2f, 27.1574f, 10.6928f, 29.6316f, 7.6f, 29.6316f)
            curveTo(4.5072f, 29.6316f, 2f, 27.1574f, 2f, 24.1053f)
            curveTo(2f, 21.0532f, 4.5072f, 18.5789f, 7.6f, 18.5789f)
            curveTo(10.6928f, 18.5789f, 13.2f, 21.0532f, 13.2f, 24.1053f)
            close()
            moveTo(13.2f, 24.1053f)
            verticalLineTo(2f)
            lineTo(23f, 7.5263f)
        }
    }.build()
}
