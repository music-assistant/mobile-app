package io.music_assistant.client.ui.compose.common.icons

import androidx.compose.foundation.Canvas
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.PathParser

/** The web frontend's autoplay glyph: a stroked infinity loop, in its 24x24 viewport. */
private const val AUTOPLAY_PATH = "M6 16c5 0 7-8 12-8a4 4 0 0 1 0 8c-5 0-7-8-12-8a4 4 0 1 0 0 8"

/** Spark length as a share of the loop (`stroke-dasharray: 10 90` over `pathLength=100`). */
private const val AUTOPLAY_SPARK_FRACTION = 0.1f

/**
 * Autoplay glyph. While [active], the loop dims and a glowing spark travels around it,
 * matching the web frontend's `AutoplayIcon.vue`; otherwise it is a plain stroked loop.
 *
 * Colored from [LocalContentColor], like `Icon`, so it follows the badge pill's inversion.
 * The caller sizes it; it has no intrinsic size.
 */
@Composable
fun AnimatedAutoplayIcon(active: Boolean, modifier: Modifier = Modifier) {
    val color = LocalContentColor.current
    val path = remember { PathParser().parsePathString(AUTOPLAY_PATH).toPath() }
    val length = remember(path) { path.measuredLength() }
    // Only an active glyph runs the animation clock.
    val progress = if (active) rememberSparkProgress() else null

    Canvas(modifier) {
        inGlyphViewport {
            drawPath(
                path = path,
                color = color,
                alpha = if (progress != null) DIMMED_GLYPH_ALPHA else 1f,
                style = GlyphStroke,
            )
            progress?.let {
                drawSpark(path, length, AUTOPLAY_SPARK_FRACTION, it.value, color)
            }
        }
    }
}
