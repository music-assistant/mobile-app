// Color analysis tuning constants (luminance thresholds, blend ratios) — extracting them to
// named constants doesn't aid readability; the values are tuning knobs that only make sense
// when read alongside the formula.
@file:Suppress("MagicNumber")

package io.music_assistant.client.ui.compose.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import co.touchlab.kermit.Logger
import coil3.compose.LocalPlatformContext
import com.kmpalette.palette.graphics.Palette
import io.music_assistant.client.data.model.server.MediaItemPalette
import io.music_assistant.client.data.model.server.RgbColor
import org.koin.compose.koinInject

/**
 * Theme-independent extraction result kept in [DominantColorViewModel]'s cache.
 * Background and tint variants are pre-computed per surface luminance so consumers
 * select cheaply. Local extraction yields the same background for both themes; a
 * server palette splits them ([MediaItemPalette.toExtractedColors]).
 */
data class ExtractedColors(
    val backgroundOnDark: Color,
    val backgroundOnLight: Color,
    val tintOnDark: Color,
    val tintOnLight: Color,
)

private fun RgbColor.toColor() = Color(r, g, b) // Compose Color(Int, Int, Int) expects 0..255

/**
 * Build extraction colors from a server-provided palette, mapping background_dark/light →
 * background and on_dark/light → tint. Returns null if any required slot is missing, so the
 * caller falls back to local artwork extraction (all-or-nothing).
 */
fun MediaItemPalette.toExtractedColors(): ExtractedColors? {
    val bgDark = backgroundDark ?: return null
    val bgLight = backgroundLight ?: return null
    val tintDark = onDark ?: return null
    val tintLight = onLight ?: return null
    return ExtractedColors(
        backgroundOnDark = bgDark.toColor(),
        backgroundOnLight = bgLight.toColor(),
        tintOnDark = tintDark.toColor(),
        tintOnLight = tintLight.toColor(),
    )
}

/**
 * Suspending fetcher used by [rememberAnimatedPlayerColors] — supplied by the screen
 * so the composable doesn't depend on Koin and is trivially testable with a fake.
 */
typealias ExtractedColorsFetcher = suspend (imageUrl: String) -> ExtractedColors?

@Composable
fun rememberExtractedColorsFetcher(): ExtractedColorsFetcher {
    val viewModel: DominantColorViewModel = koinInject()
    val platformContext = LocalPlatformContext.current
    return remember(viewModel, platformContext) {
        {
            url ->
                viewModel.getColors(platformContext, url)
            }
    }
}

/**
 * Dominant color extracted from artwork plus its theme-adjusted control tint.
 * Both fields are animated; [controlTint] is the variant matching the current
 * surface luminance so call sites can drop their per-recomposition `asControlTint()`.
 */
data class PlayerColors(
    val dominant: Color,
    val controlTint: Color,
)

@Composable
fun rememberAnimatedPlayerColors(
    imageUrl: String?,
    palette: MediaItemPalette?,
    fallback: Color,
    fetchColors: ExtractedColorsFetcher,
): State<PlayerColors> {
    Logger.withTag("PALETTE").e { "$palette" }
    val onDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    val serverColors = remember(palette) { palette?.toExtractedColors() }
    val localColors by produceState<ExtractedColors?>(
        initialValue = null,
        key1 = imageUrl,
        key2 = serverColors,
    ) {
        value = if (serverColors != null) null else imageUrl?.let { fetchColors(it) }
    }
    val extracted = serverColors ?: localColors

    val targetDominant = extracted
        ?.let { if (onDark) it.backgroundOnDark else it.backgroundOnLight } ?: fallback
    val targetTint = extracted
        ?.let { if (onDark) it.tintOnDark else it.tintOnLight }
        ?: fallback.ensureReadable(onDarkSurface = onDark)

    val animatedDominant by animateColorAsState(
        targetValue = targetDominant,
        animationSpec = tween(durationMillis = 500),
    )
    val animatedTint by animateColorAsState(
        targetValue = targetTint,
        animationSpec = tween(durationMillis = 500),
    )

    val state = remember { mutableStateOf(PlayerColors(targetDominant, targetTint)) }
    state.value = PlayerColors(animatedDominant, animatedTint)
    return state
}

/**
 * This tries to identify the "best" color: if the "dominant" color is very close to black or
 * white (which will cause control color problems), fallback to the "vibrant" color. For
 * completely black/white images (see Weezer, Spinal Tap, The Beatles or Metallica for
 * examples), a vibrant color might not exist and in that case we still use the dominant one.
 */
fun Palette.getBestColor(): Color? {
    val dominantColor = this.dominantSwatch?.let { Color(it.rgb) }
    val vibrantColor = this.vibrantSwatch?.let { Color(it.rgb) }
    val color =
        if (dominantColor != null && (dominantColor.luminance() in 0.01..0.99 || vibrantColor == null)) {
            dominantColor
        } else {
            vibrantColor
        }

    return color
}

/**
 * Clamp HSL lightness so the color stays readable against a dark or light surface
 * while preserving hue and saturation. Used for foreground tints derived from artwork.
 */
fun Color.ensureReadable(
    onDarkSurface: Boolean,
    minLightnessOnDark: Float = 0.60f,
    maxLightnessOnLight: Float = 0.45f,
): Color {
    val max = maxOf(red, green, blue)
    val min = minOf(red, green, blue)
    val l = (max + min) / 2f
    val clampedL = if (onDarkSurface) {
        l.coerceAtLeast(minLightnessOnDark)
    } else {
        l.coerceAtMost(maxLightnessOnLight)
    }
    if (clampedL == l) return this

    val d = max - min
    val s = when {
        d == 0f -> 0f
        l > 0.5f -> d / (2f - max - min)
        else -> d / (max + min)
    }
    val h = when {
        d == 0f -> 0f
        max == red -> ((green - blue) / d + if (green < blue) 6f else 0f) / 6f
        max == green -> ((blue - red) / d + 2f) / 6f
        else -> ((red - green) / d + 4f) / 6f
    }

    if (s == 0f) return Color(clampedL, clampedL, clampedL, alpha)
    val q = if (clampedL < 0.5f) clampedL * (1f + s) else clampedL + s - clampedL * s
    val p = 2f * clampedL - q
    return Color(
        red = hueToRgb(p, q, h + 1f / 3f),
        green = hueToRgb(p, q, h),
        blue = hueToRgb(p, q, h - 1f / 3f),
        alpha = alpha,
    )
}

private fun hueToRgb(p: Float, q: Float, t: Float): Float {
    val tt = (t + 1f) % 1f
    return when {
        tt < 1f / 6f -> p + (q - p) * 6f * tt
        tt < 1f / 2f -> q
        tt < 2f / 3f -> p + (q - p) * (2f / 3f - tt) * 6f
        else -> p
    }
}
