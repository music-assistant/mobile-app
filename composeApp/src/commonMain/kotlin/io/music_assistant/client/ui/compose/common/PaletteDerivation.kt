// WCAG contrast constants and blend ratios are tuning knobs from the Sendspin color@v1 spec;
// naming each magic value adds noise without aiding readability.
@file:Suppress("MagicNumber")

package io.music_assistant.client.ui.compose.common

import io.music_assistant.client.data.model.server.MediaItemPalette
import io.music_assistant.client.data.model.server.RgbColor
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * On-device port of the server's Sendspin color@v1 palette derivation. Given a list of
 * dominant-first candidate colors (most populous first), produces the same 6-field
 * [MediaItemPalette] the server computes, so local artwork extraction matches the colors the
 * server emits for active queue items.
 *
 * This is a faithful 1:1 port of the server's `_derive_palette`; every spec-mandated contrast
 * pair clears WCAG AA 4.5:1. Pure and platform-independent — see PaletteDerivationTest.
 */

internal const val MIN_CONTRAST = 4.5
internal const val PREFERRED_CONTRAST = 7.0

// Upper cap when picking the dark on-light color. Without it, near-black image regions (e.g. text
// outlines) win and the picked color looks like pure black instead of a vibrant darker shade.
internal const val MAX_DARK_PICK_CONTRAST = 17.35
internal const val BACKGROUND_ADJUST_STEPS = 20
internal const val SIMILARITY_THRESHOLD = 60 // squared euclidean RGB distance for accent picking
internal const val QUANTIZE_COLORS = 5

private val BLACK = RgbColor(0, 0, 0)
private val WHITE = RgbColor(255, 255, 255)

// A swatch is treated as black/white only when it is BOTH near-greyscale (low chroma) AND a
// lightness extreme. The chroma guard keeps saturated colors that happen to be very dark or very
// bright (deep blue, bright yellow) from being mistaken for black/white.
internal const val ACHROMATIC_CHROMA_MAX = 20 // max-min RGB spread below which a swatch reads grey
internal const val NEAR_BLACK_MAX = 48        // max channel at/below which a grey reads as black
internal const val NEAR_WHITE_MIN = 208       // min channel at/above which a grey reads as white

/** True for near-black / near-white greys; saturated darks and brights are excluded by chroma. */
internal fun RgbColor.isBlackOrWhite(): Boolean {
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    return (max - min) <= ACHROMATIC_CHROMA_MAX && (max <= NEAR_BLACK_MAX || min >= NEAR_WHITE_MIN)
}

/**
 * Drop near-black/near-white swatches so a small colored figure on a black/white/grey field wins
 * the [derivePalette] primary slot instead of the background dominating. Falls back to the full
 * list when every candidate is achromatic, so a genuinely monochrome cover still yields a palette
 * rather than an empty one (which would degrade to a flat fallback color).
 */
fun meaningfulCandidates(candidates: List<RgbColor>): List<RgbColor> =
    candidates.filterNot { it.isBlackOrWhite() }.ifEmpty { candidates }

/** WCAG relative luminance for an sRGB color. */
internal fun relativeLuminance(rgb: RgbColor): Double {
    fun channel(c: Int): Double {
        val s = c / 255.0
        return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * channel(rgb.r) + 0.7152 * channel(rgb.g) + 0.0722 * channel(rgb.b)
}

/** WCAG contrast ratio between two RGB colors. */
internal fun contrastRatio(a: RgbColor, b: RgbColor): Double {
    val la = relativeLuminance(a)
    val lb = relativeLuminance(b)
    val lighter = maxOf(la, lb)
    val darker = minOf(la, lb)
    return (lighter + 0.05) / (darker + 0.05)
}

internal fun colorDistanceSq(a: RgbColor, b: RgbColor): Int {
    val dr = a.r - b.r
    val dg = a.g - b.g
    val db = a.b - b.b
    return dr * dr + dg * dg + db * db
}

/**
 * Blend [rgb] toward [target]. factor=0 returns rgb, factor=1 returns target.
 *
 * Note: the server uses Python `round()` (banker's rounding); Kotlin [roundToInt] rounds half-up,
 * so an exact .5 channel may differ by ±1 — visually irrelevant.
 */
internal fun mix(rgb: RgbColor, target: RgbColor, factor: Double): RgbColor = RgbColor(
    (rgb.r + (target.r - rgb.r) * factor).roundToInt(),
    (rgb.g + (target.g - rgb.g) * factor).roundToInt(),
    (rgb.b + (target.b - rgb.b) * factor).roundToInt(),
)

/** Mix [color] toward [mixToward] until contrast >= [minContrast] vs all [refs], else null. */
internal fun adjustUntilContrast(
    color: RgbColor,
    mixToward: RgbColor,
    refs: List<RgbColor>,
    minContrast: Double = MIN_CONTRAST,
): RgbColor? {
    for (step in 0..BACKGROUND_ADJUST_STEPS) {
        val factor = step.toDouble() / BACKGROUND_ADJUST_STEPS
        val candidate = mix(color, mixToward, factor)
        if (refs.all { contrastRatio(candidate, it) >= minContrast }) return candidate
    }
    return null
}

/** Try [PREFERRED_CONTRAST] first, fall back to [MIN_CONTRAST]. */
internal fun adjustWithFallback(color: RgbColor, mixToward: RgbColor, refs: List<RgbColor>): RgbColor? =
    adjustUntilContrast(color, mixToward, refs, PREFERRED_CONTRAST)
        ?: adjustUntilContrast(color, mixToward, refs, MIN_CONTRAST)

/** Pick the candidate with highest contrast vs [target] within [minContrast, maxContrast]. */
internal fun pickOnColor(
    candidates: List<RgbColor>,
    target: RgbColor,
    minContrast: Double = MIN_CONTRAST,
    maxContrast: Double = Double.POSITIVE_INFINITY,
): RgbColor? {
    var best: RgbColor? = null
    var bestRatio = 0.0
    for (rgb in candidates) {
        val ratio = contrastRatio(rgb, target)
        if (ratio in minContrast..maxContrast && ratio > bestRatio) {
            best = rgb
            bestRatio = ratio
        }
    }
    return best
}

/** Try [PREFERRED_CONTRAST] first, fall back to [MIN_CONTRAST]. */
internal fun pickOnColorWithFallback(
    candidates: List<RgbColor>,
    target: RgbColor,
    maxContrast: Double = Double.POSITIVE_INFINITY,
): RgbColor? =
    pickOnColor(candidates, target, PREFERRED_CONTRAST, maxContrast)
        ?: pickOnColor(candidates, target, MIN_CONTRAST, maxContrast)

/** First candidate that is hue-distant from [primary], or null. */
internal fun pickAccent(primary: RgbColor, candidates: List<RgbColor>): RgbColor? {
    for (rgb in candidates) {
        if (rgb == primary) continue
        if (colorDistanceSq(rgb, primary) >= SIMILARITY_THRESHOLD * SIMILARITY_THRESHOLD) return rgb
    }
    return null
}

/**
 * Derive a [MediaItemPalette] from dominant-first [candidates]. Mirrors the server's
 * `_derive_palette` exactly: empty input yields an all-null palette; primary is the most
 * dominant candidate; on-colors are picked against pure black/white (synthesized when none
 * qualify); backgrounds are adjusted to clear contrast vs both the matching text color and the
 * chosen on-color; accent is a hue-distant secondary (not contrast-adjusted).
 */
fun derivePalette(candidates: List<RgbColor>): MediaItemPalette {
    if (candidates.isEmpty()) return MediaItemPalette()
    val primary = candidates[0]

    // on_dark caps at +inf, on_light caps at MAX_DARK_PICK_CONTRAST to avoid near-black picks
    // (e.g. text outlines) winning over genuinely dark image colors.
    var onDark = pickOnColorWithFallback(candidates, BLACK)
    var onLight = pickOnColorWithFallback(candidates, WHITE, MAX_DARK_PICK_CONTRAST)

    // Synthesize a fallback when no candidate cleared the contrast bar so the field is always
    // emitted. on_dark must clear 4.5:1 vs black, on_light vs white.
    if (onDark == null) onDark = adjustUntilContrast(primary, WHITE, listOf(BLACK))
    if (onLight == null) onLight = adjustUntilContrast(primary, BLACK, listOf(WHITE))

    val bgDarkRefs = if (onDark != null) listOf(WHITE, onDark) else listOf(WHITE)
    val backgroundDark = adjustWithFallback(primary, BLACK, bgDarkRefs)

    val bgLightRefs = if (onLight != null) listOf(BLACK, onLight) else listOf(BLACK)
    val backgroundLight = adjustWithFallback(primary, WHITE, bgLightRefs)

    val accent = pickAccent(primary, candidates)

    return MediaItemPalette(
        backgroundDark = backgroundDark,
        backgroundLight = backgroundLight,
        primary = primary,
        accent = accent,
        onDark = onDark,
        onLight = onLight,
    )
}
