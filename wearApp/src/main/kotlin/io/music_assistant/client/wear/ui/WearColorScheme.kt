// Tone numbers are the Wear Material 3 colour tokens, quoted from the design spec.
@file:Suppress("MagicNumber")

package io.music_assistant.client.wear.ui

import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.ColorScheme
import com.materialkolor.hct.Hct
import com.materialkolor.palettes.TonalPalette
import com.materialkolor.scheme.SchemeTonalSpot

/**
 * Builds a Wear Material 3 colour scheme from the cover art.
 *
 * The seed goes through the same tonal-spot scheme Material You uses, and each role is then
 * read at the tone the Wear Material 3 tokens prescribe (`ColorTokens` in
 * `androidx.wear.compose.material3`): primary at 90, its dim variant at 80, containers at 30,
 * and so on. That is what makes the buttons belong to the album rather than merely be tinted
 * by it.
 *
 * The surface containers do **not** come from the scheme's neutral palette, which carries so
 * little chroma that every tonal button reads as the same grey whatever is playing. They are
 * read from a palette at the album's own hue instead, at a chroma low enough that the
 * surfaces stay backgrounds and the filled buttons keep the contrast they are drawn for.
 * Those containers are what [androidx.wear.compose.material3.FilledTonalIconButton] and the
 * tonal buttons and list rows paint themselves with, so the whole app follows the cover.
 *
 * Error roles keep the Wear defaults: a red that follows the album art stops reading as an
 * error. The scheme is always dark — a watch media screen over cover art has no light mode.
 */
fun artworkColorScheme(seedArgb: Int): ColorScheme {
    val scheme = SchemeTonalSpot(
        sourceColorHct = Hct.fromInt(seedArgb),
        isDark = true,
        contrastLevel = 0.0,
    )
    val surfacePalette = TonalPalette.fromHueAndChroma(
        hue = scheme.primaryPalette.hue,
        chroma = SURFACE_CHROMA,
    )
    return ColorScheme(
        primary = scheme.primaryPalette.at(90),
        primaryDim = scheme.primaryPalette.at(80),
        primaryContainer = scheme.primaryPalette.at(30),
        onPrimary = scheme.primaryPalette.at(10),
        onPrimaryContainer = scheme.primaryPalette.at(95),
        secondary = scheme.secondaryPalette.at(90),
        secondaryDim = scheme.secondaryPalette.at(80),
        secondaryContainer = scheme.secondaryPalette.at(30),
        onSecondary = scheme.secondaryPalette.at(10),
        onSecondaryContainer = scheme.secondaryPalette.at(95),
        tertiary = scheme.tertiaryPalette.at(90),
        tertiaryDim = scheme.tertiaryPalette.at(80),
        tertiaryContainer = scheme.tertiaryPalette.at(30),
        onTertiary = scheme.tertiaryPalette.at(10),
        onTertiaryContainer = scheme.tertiaryPalette.at(95),
        surfaceContainerLow = surfacePalette.at(15),
        surfaceContainer = surfacePalette.at(20),
        surfaceContainerHigh = surfacePalette.at(30),
        onSurface = scheme.neutralPalette.at(95),
        onSurfaceVariant = scheme.neutralVariantPalette.at(80),
        outline = scheme.neutralVariantPalette.at(60),
        outlineVariant = scheme.neutralVariantPalette.at(40),
        background = scheme.neutralPalette.at(0),
        onBackground = scheme.neutralPalette.at(100),
    )
}

/**
 * The chroma of the surface palette. The scheme's own neutral palette sits at 4, which is
 * grey on any album; the primary palette sits near 36, which would make a tonal button
 * compete with the filled one next to it.
 */
private const val SURFACE_CHROMA = 16.0

/** The palette's colour at [tone] (0 = black, 100 = white), as a Compose colour. */
private fun TonalPalette.at(tone: Int): Color = Color(tone(tone))
