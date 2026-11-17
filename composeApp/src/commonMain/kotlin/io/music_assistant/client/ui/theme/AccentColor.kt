package io.music_assistant.client.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.materialkolor.dynamicColorScheme

/**
 * The accent that the user picks in the settings.
 *
 * [DEFAULT] keeps the hand-made schemes of `Theme.kt`. [WALLPAPER] takes the primary group from
 * the wallpaper scheme of the platform, on Android 12 and newer only. Every other entry generates
 * the primary group from its [seed] (Material 3 tonal spot). The secondary, tertiary and neutral
 * roles never change, so the favorite heart and the surfaces keep their colors.
 */
enum class AccentColor(internal val seed: Color? = null) {
    DEFAULT,
    WALLPAPER,
    RED(Color(0xFFE53935)),
    ORANGE(Color(0xFFFB8C00)),
    YELLOW(Color(0xFFFDD835)),
    GREEN(Color(0xFF43A047)),
    TEAL(Color(0xFF00897B)),
    BLUE(Color(0xFF1E88E5)),
    INDIGO(Color(0xFF3949AB)),
    PURPLE(Color(0xFF8E24AA)),
    PINK(Color(0xFFD81B60)),
}

/** The scheme of [accent] in [darkTheme], or null if the platform cannot give it. */
@Composable
fun accentColorScheme(accent: AccentColor, darkTheme: Boolean): ColorScheme? = when (accent) {
    AccentColor.DEFAULT -> defaultColorScheme(darkTheme)
    AccentColor.WALLPAPER -> wallpaperColorScheme(darkTheme)
        ?.let { defaultColorScheme(darkTheme).withPrimaryGroupOf(it) }
    else -> accent.seed?.let { seed -> remember(seed, darkTheme) { seedColorScheme(seed, darkTheme) } }
}

/** The default scheme with the primary group that [seed] generates. */
internal fun seedColorScheme(seed: Color, darkTheme: Boolean): ColorScheme =
    defaultColorScheme(darkTheme)
        .withPrimaryGroupOf(dynamicColorScheme(seedColor = seed, isDark = darkTheme))

/** This scheme with the primary roles of [accent]. The other roles stay. */
internal fun ColorScheme.withPrimaryGroupOf(accent: ColorScheme): ColorScheme = copy(
    primary = accent.primary,
    onPrimary = accent.onPrimary,
    primaryContainer = accent.primaryContainer,
    onPrimaryContainer = accent.onPrimaryContainer,
    inversePrimary = accent.inversePrimary,
    surfaceTint = accent.surfaceTint,
    primaryFixed = accent.primaryFixed,
    primaryFixedDim = accent.primaryFixedDim,
    onPrimaryFixed = accent.onPrimaryFixed,
    onPrimaryFixedVariant = accent.onPrimaryFixedVariant,
)

/** The primary color of [accent] without the platform: the wallpaper accent gives the default. */
internal fun accentPrimary(accent: AccentColor, darkTheme: Boolean): Color =
    accent.seed?.let { seedColorScheme(it, darkTheme).primary } ?: defaultColorScheme(darkTheme).primary
