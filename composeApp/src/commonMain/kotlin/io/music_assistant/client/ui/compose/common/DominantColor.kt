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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
 * Background and tint colors are pre-computed for dark and light surfaces so consumers
 * can select cheaply without re-deriving palette roles during recomposition.
 */
data class ExtractedColors(
    val backgroundOnDark: Color,
    val backgroundOnLight: Color,
    val tintOnDark: Color,
    val tintOnLight: Color,
)

private fun RgbColor.toColor() = Color(r, g, b) // Compose Color(Int, Int, Int) expects 0..255

internal const val MIN_DARK_WASH_CHROMA = 48
internal const val MIN_DARK_WASH_LUMINANCE = 0.12

internal fun RgbColor.chroma(): Int = maxOf(r, g, b) - minOf(r, g, b)

internal fun RgbColor.isDarkBackgroundWashCandidate(): Boolean =
    !isBlackOrWhite() && chroma() >= MIN_DARK_WASH_CHROMA && relativeLuminance(this) >= MIN_DARK_WASH_LUMINANCE

private fun MediaItemPalette.chromaticBackgroundWash(): RgbColor? =
    listOfNotNull(accent, primary).firstOrNull { it.isDarkBackgroundWashCandidate() }

private fun MediaItemPalette.artworkWashColor(): RgbColor? =
    chromaticBackgroundWash()
        ?: listOfNotNull(primary, accent).firstOrNull { !it.isBlackOrWhite() }
        ?: backgroundDark
        ?: primary
        ?: accent

/**
 * Build theme-specific UI colors from a server-provided or locally derived palette.
 * Both dark and light surfaces use the same artwork-identity wash color so the hero hue stays
 * consistent across themes; only foreground/control tints are adapted per surface luminance.
 */
fun MediaItemPalette.toExtractedColors(): ExtractedColors? {
    val wash = artworkWashColor()?.toColor() ?: return null
    val base = (primary ?: accent)?.toColor() ?: wash
    val darkTint = onDark?.toColor() ?: base.ensureReadable(onDarkSurface = true)
    val lightTint = onLight?.toColor() ?: base.ensureReadable(onDarkSurface = false)
    return ExtractedColors(
        backgroundOnDark = wash,
        backgroundOnLight = wash,
        tintOnDark = darkTint,
        tintOnLight = lightTint,
    )
}

/**
 * Color source used by [rememberAnimatedPlayerColors] — supplied by the screen so the
 * composable doesn't depend on Koin and is trivially testable with a fake.
 *
 * [peek] is a synchronous cache hit (or null) so an already-known color can be applied on
 * first composition without animating; [fetch] is the suspending extract-or-cache path.
 */
interface ExtractedColorsSource {
    fun peek(imageUrl: String): ExtractedColors?
    suspend fun fetch(imageUrl: String): ExtractedColors?
}

@Composable
fun rememberExtractedColorsSource(): ExtractedColorsSource {
    val viewModel: DominantColorViewModel = koinInject()
    val platformContext = LocalPlatformContext.current
    return remember(viewModel, platformContext) {
        object : ExtractedColorsSource {
            override fun peek(imageUrl: String) = viewModel.peekColors(imageUrl)
            override suspend fun fetch(imageUrl: String) = viewModel.getColors(platformContext, imageUrl)
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
    source: ExtractedColorsSource,
): State<PlayerColors> {
    val onDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    // Synchronous cache hit (consecutive launch): seed the value so the first target is already
    // final and animateColorAsState renders it without a tween. A miss seeds null → fallback and
    // animates once fetch() resolves. remember(imageUrl) is required over produceState here:
    // produceState doesn't re-apply initialValue when its key changes, so a cached value would
    // never replace the previous image's colors.
    val cached = remember(imageUrl) { imageUrl?.let { source.peek(it) } }
    var extracted by remember(imageUrl) { mutableStateOf(cached) }
    LaunchedEffect(imageUrl) {
        if (cached == null) extracted = imageUrl?.let { source.fetch(it) }
    }

    val targetDominant = extracted
        ?.let { if (onDark) it.backgroundOnDark else it.backgroundOnLight }
        ?: fallback
    val targetTint = extracted
        ?.let { if (onDark) it.tintOnDark else it.tintOnLight }
        ?: fallback.ensureReadable(onDarkSurface = onDark)

    // Animate the fallback → extracted transition on a cache miss; snap instantly on a hit, where
    // the final color is already known on the first frame and a tween would just be flicker.
    val animate = cached == null
    val animatedDominant by rememberAnimatedColorAsState(
        targetValue = targetDominant,
        animate = animate,
        animationSpec = tween(durationMillis = 500),
    )
    val animatedTint by rememberAnimatedColorAsState(
        targetValue = targetTint,
        animate = animate,
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
    animate: Boolean,
    animationSpec: AnimationSpec<Color>,
): State<Color> {
    if (!animate) return remember(targetValue) { mutableStateOf(targetValue) }

    var animated by rememberSaveable(targetValue) { mutableStateOf(false) }

    return if (!animated) {
        animateColorAsState(targetValue, animationSpec) { animated = true }
    } else {
        mutableStateOf(targetValue)
    }
}
