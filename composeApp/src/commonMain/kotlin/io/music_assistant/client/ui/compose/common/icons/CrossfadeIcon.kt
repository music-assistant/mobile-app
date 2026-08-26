package io.music_assistant.client.ui.compose.common.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Two overlapping outlined circles — the web frontend's crossfade glyph.
 *
 * Stroked, not filled, so the overlap reads as two tracks bleeding into each other.
 * The stroke color is a placeholder: every call site tints it, either through
 * `Icon(tint = ...)` or `LocalContentColor`.
 */
val CrossfadeIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Crossfade",
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
            // Leading circle: cx=9, cy=9, r=7, drawn as two semicircular arcs.
            moveTo(2f, 9f)
            arcToRelative(7f, 7f, 0f, true, true, 14f, 0f)
            arcToRelative(7f, 7f, 0f, true, true, -14f, 0f)
            // Trailing circle: cx=15, cy=15, r=7.
            moveTo(8f, 15f)
            arcToRelative(7f, 7f, 0f, true, true, 14f, 0f)
            arcToRelative(7f, 7f, 0f, true, true, -14f, 0f)
        }
    }.build()
}
