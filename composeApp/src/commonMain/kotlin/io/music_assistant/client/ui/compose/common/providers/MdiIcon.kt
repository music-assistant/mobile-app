package io.music_assistant.client.ui.compose.common.providers

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.mdi_webfont
import org.jetbrains.compose.resources.Font
import org.koin.compose.koinInject

/** Default size used when [MdiIcon] is placed without a bounded size constraint. */
private val DefaultMdiSize = 24.dp

/** The Material Design Icons community webfont (all ~7.4k glyphs in one family). */
@Composable
private fun mdiFontFamily(): FontFamily {
    val font = Font(Res.font.mdi_webfont)
    return remember(font) { FontFamily(font) }
}

/**
 * Strips the server's `mdi-` / `mdi:` / `mdi_` prefixes to the bare icon name used as the
 * key in the codepoint table (e.g. "mdi-speaker" -> "speaker"). Pure; unit-tested.
 */
fun normalizeMdiName(raw: String): String =
    raw.trim()
        .removePrefix("mdi-")
        .removePrefix("mdi:")
        .removePrefix("mdi_")
        .trim()

/**
 * Surrogate-safe Unicode code point -> String. MDI glyphs live in the supplementary
 * Private Use Area (> 0xFFFF, e.g. U+F04C3), so a plain `toChar()` would corrupt them.
 * Pure Kotlin (no JVM `Character.toChars`) to stay common across Android + iOS.
 */
fun codePointToString(codePoint: Int): String =
    if (codePoint <= PRIVATE_AREA_LENGTH) {
        codePoint.toChar().toString()
    } else {
        val offset = codePoint - 0x10000
        val high = (0xD800 + (offset shr 10)).toChar()
        val low = (0xDC00 + (offset and 0x3FF)).toChar()
        charArrayOf(high, low).concatToString()
    }

/**
 * Renders a Material Design Icons community-pack glyph by its server-provided [name]
 * (e.g. "mdi-speaker"). Resolves the name -> codepoint via the once-loaded [MdiCodepoints]
 * table and draws the glyph as a font character sized to the layout constraints, so it
 * honors a caller-supplied size [modifier] like a regular `Icon`.
 *
 * Shows [fallback] when [name] is null/unknown or until the table has loaded (default:
 * draws nothing). Callers wanting a guaranteed glyph pass a fallback to render in that
 * window. Accepting a nullable [name] keeps absent-icon handling out of every call site.
 */
@Composable
fun MdiIcon(
    name: String?,
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    fallback: @Composable () -> Unit = {},
) {
    val table by koinInject<MdiCodepoints>().table.collectAsStateWithLifecycle()
    val codePoint = name?.let { table[normalizeMdiName(it)] }
    if (codePoint == null) {
        fallback()
        return
    }

    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val side: Dp = when {
            maxWidth == Dp.Infinity && maxHeight == Dp.Infinity -> DefaultMdiSize
            maxWidth == Dp.Infinity -> maxHeight
            maxHeight == Dp.Infinity -> maxWidth
            else -> minOf(maxWidth, maxHeight)
        }
        val sizeSp = with(LocalDensity.current) { side.toSp() }
        Text(
            text = codePointToString(codePoint),
            fontFamily = mdiFontFamily(),
            color = tint,
            fontSize = sizeSp,
            lineHeight = sizeSp,
            maxLines = 1,
        )
    }
}

private const val PRIVATE_AREA_LENGTH = 0xFFFF
