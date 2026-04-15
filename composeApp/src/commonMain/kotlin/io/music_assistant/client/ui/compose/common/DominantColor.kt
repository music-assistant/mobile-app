package io.music_assistant.client.ui.compose.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.kmpalette.extensions.network.rememberNetworkDominantColorState
import io.ktor.http.Url

@Composable
fun rememberAnimatedDominantColor(
    imageUrl: String?,
    fallback: Color,
): State<Color> {
    val dominantColorState = rememberNetworkDominantColorState(
        defaultColor = fallback,
        defaultOnColor = Color.White,
    )

    LaunchedEffect(imageUrl) {
        if (imageUrl != null) {
            try {
                dominantColorState.updateFrom(Url(imageUrl))
            } catch (_: Exception) {
                dominantColorState.reset()
            }
        } else {
            dominantColorState.reset()
        }
    }

    val extractedColor = Color(dominantColorState.color.value as ULong)
    val targetColor = extractedColor.takeIf {
        imageUrl != null && it != Color.Unspecified && it != Color.Transparent
    } ?: fallback

    return animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(durationMillis = 500)
    )
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

/**
 * Return a contrast-adjusted copy of this color suitable for foreground controls,
 * based on the current theme's surface luminance.
 */
@Composable
fun Color.asControlTint(): Color {
    val onDarkSurface = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return ensureReadable(onDarkSurface = onDarkSurface)
}
