// Color analysis tuning constants (luminance thresholds, blend ratios) — extracting them to
// named constants doesn't aid readability; the values are tuning knobs that only make sense
// when read alongside the formula.
@file:Suppress("MagicNumber")

package io.music_assistant.client.ui.compose.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import coil3.compose.LocalPlatformContext
import io.music_assistant.client.data.model.server.MediaItemPalette
import io.music_assistant.client.data.model.server.RgbColor
import org.koin.compose.koinInject

/**
 * Theme-independent extraction result kept in [DominantColorViewModel]'s cache.
 * A single vivid [background] serves both themes; the readable tint is pre-computed
 * per surface luminance so consumers select cheaply.
 */
data class ExtractedColors(
    val background: Color,
    val tintOnDark: Color,
    val tintOnLight: Color,
)

private fun RgbColor.toColor() = Color(r, g, b) // Compose Color(Int, Int, Int) expects 0..255

/**
 * Build extraction colors from a palette — server-provided or locally derived (see
 * [derivePalette]). Uses the vivid `primary` (falling back to `accent`) as the single background
 * for both themes, and the spec's WCAG-clean `on_dark`/`on_light` as the readable control tint
 * per surface, falling back to [ensureReadable] only when a slot is absent. The muted
 * `background_*` slots are intentionally unused (the wash keeps the vivid `primary`). Returns null
 * when neither vivid slot is present, so the caller falls back to local extraction.
 */
fun MediaItemPalette.toExtractedColors(): ExtractedColors? {
    val base = (primary ?: accent)?.toColor() ?: return null
    return ExtractedColors(
        background = base,
        tintOnDark = onDark?.toColor() ?: base.ensureReadable(onDarkSurface = true),
        tintOnLight = onLight?.toColor() ?: base.ensureReadable(onDarkSurface = false),
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
    fallback: Color,
    fetchColors: ExtractedColorsFetcher,
): State<PlayerColors> {
    val onDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    val extracted by produceState<ExtractedColors?>(
        initialValue = null,
        key1 = imageUrl,
    ) {
        value = imageUrl?.let { fetchColors(it) }
    }

    val targetDominant = extracted?.background ?: fallback
    val targetTint = extracted
        ?.let { if (onDark) it.tintOnDark else it.tintOnLight }
        ?: fallback.ensureReadable(onDarkSurface = onDark)

    val animatedDominant by rememberAnimatedColorAsState(
        targetValue = targetDominant,
        animationSpec = tween(durationMillis = 500),
    )
    val animatedTint by rememberAnimatedColorAsState(
        targetValue = targetTint,
        animationSpec = tween(durationMillis = 500),
    )

    return derivedStateOf {
        PlayerColors(animatedDominant, animatedTint)
    }
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

@Composable
private fun rememberAnimatedColorAsState(
    targetValue: Color,
    animationSpec: AnimationSpec<Color>,
): State<Color> {
    var animated by rememberSaveable(targetValue) { mutableStateOf(false) }

    return if (!animated) {
        animateColorAsState(targetValue, animationSpec) { animated = true }
    } else {
        mutableStateOf(targetValue)
    }
}
