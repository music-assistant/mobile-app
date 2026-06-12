package io.music_assistant.client.ui.compose.common

import io.music_assistant.client.data.model.server.MediaItemPalette
import io.music_assistant.client.data.model.server.RgbColor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Locks the on-device port of the server's Sendspin color@v1 derivation against drift. Golden
 * values come from running the server's pure `_derive_palette` (independent of this Kotlin port);
 * exact equality holds because half-up and banker's rounding agree on these inputs.
 */
class PaletteDerivationTest {
    private val black = RgbColor(0, 0, 0)
    private val white = RgbColor(255, 255, 255)

    @Test
    fun `contrast and luminance match WCAG references`() {
        assertEquals(21.0, contrastRatio(black, white), 1e-9)
        assertEquals(1.0, relativeLuminance(white), 1e-9)
        assertEquals(0.0, relativeLuminance(black), 1e-9)
    }

    @Test
    fun `empty candidates yield an all-null palette`() {
        assertEquals(MediaItemPalette(), derivePalette(emptyList()))
    }

    @Test
    fun `primary is the most dominant candidate`() {
        val candidates = listOf(RgbColor(10, 20, 30), RgbColor(200, 100, 50))
        assertEquals(candidates[0], derivePalette(candidates).primary)
    }

    @Test
    fun `golden case matches server reference`() {
        val candidates = listOf(
            RgbColor(38, 70, 83),
            RgbColor(244, 162, 97),
            RgbColor(231, 111, 81),
            RgbColor(42, 157, 143),
            RgbColor(233, 196, 106),
        )
        assertEquals(
            MediaItemPalette(
                backgroundDark = RgbColor(30, 56, 66),
                backgroundLight = RgbColor(212, 218, 221),
                primary = RgbColor(38, 70, 83),
                accent = RgbColor(244, 162, 97),
                onDark = RgbColor(233, 196, 106),
                onLight = RgbColor(38, 70, 83),
            ),
            derivePalette(candidates),
        )
    }

    @Test
    fun `single mid-gray synthesizes on-light and contrast-clean backgrounds`() {
        val palette = derivePalette(listOf(RgbColor(128, 128, 128)))
        assertEquals(RgbColor(128, 128, 128), palette.primary)
        assertNull(palette.accent) // no second candidate to be hue-distant
        // on_light could not be picked from the lone gray (too low contrast vs white) -> synthesized
        assertEquals(RgbColor(115, 115, 115), palette.onLight)
        assertEquals(RgbColor(19, 19, 19), palette.backgroundDark)
        assertEquals(RgbColor(249, 249, 249), palette.backgroundLight)
    }

    @Test
    fun `derived slots clear the spec-mandated contrast pairs`() {
        val candidates = listOf(
            RgbColor(38, 70, 83),
            RgbColor(244, 162, 97),
            RgbColor(42, 157, 143),
        )
        val p = derivePalette(candidates)
        val onDark = assertNotNull(p.onDark)
        val onLight = assertNotNull(p.onLight)
        val bgDark = assertNotNull(p.backgroundDark)
        val bgLight = assertNotNull(p.backgroundLight)
        assertTrue(contrastRatio(onDark, black) >= MIN_CONTRAST, "on_dark vs black")
        assertTrue(contrastRatio(onLight, white) >= MIN_CONTRAST, "on_light vs white")
        assertTrue(contrastRatio(bgDark, white) >= MIN_CONTRAST, "bg_dark vs white")
        assertTrue(contrastRatio(bgDark, onDark) >= MIN_CONTRAST, "bg_dark vs on_dark")
        assertTrue(contrastRatio(bgLight, black) >= MIN_CONTRAST, "bg_light vs black")
        assertTrue(contrastRatio(bgLight, onLight) >= MIN_CONTRAST, "bg_light vs on_light")
    }

    @Test
    fun `black-heavy art still yields a usable non-null palette`() {
        // Regression: kmpalette's DEFAULT_FILTER strips near-black pixels, which previously left
        // black covers with zero candidates → null palette → fallback. With filters cleared the
        // quantizer keeps black, so primary is present and the on-colors are synthesized.
        val palette = derivePalette(listOf(black))
        assertEquals(black, palette.primary) // primary present ⇒ toExtractedColors() is non-null
        val onDark = assertNotNull(palette.onDark)
        assertTrue(contrastRatio(onDark, black) >= MIN_CONTRAST, "synthesized on_dark vs black")
        assertNotNull(palette.toExtractedColors())
    }

    @Test
    fun `meaningfulCandidates drops black-white backgrounds but keeps saturated extremes`() {
        val red = RgbColor(220, 30, 40)
        val deepBlue = RgbColor(0, 0, 90)        // very dark yet saturated → kept
        val brightYellow = RgbColor(255, 255, 0) // very bright yet saturated → kept
        val midGrey = RgbColor(128, 128, 128)    // neither black nor white → kept
        // Black is the most populous (background); the colored figure must still win primary.
        val candidates = listOf(black, red, deepBlue, brightYellow, midGrey, white)
        val filtered = meaningfulCandidates(candidates)
        assertEquals(listOf(red, deepBlue, brightYellow, midGrey), filtered)
        assertEquals(red, derivePalette(filtered).primary)
    }

    @Test
    fun `meaningfulCandidates falls back to full list when all are achromatic`() {
        val mono = listOf(black, white, RgbColor(20, 20, 20))
        assertEquals(mono, meaningfulCandidates(mono)) // never empty ⇒ never a null palette
    }

    @Test
    fun `accent picks a hue-distant candidate and is null when all are similar`() {
        val primary = RgbColor(40, 70, 80)
        val distant = RgbColor(240, 160, 100)
        assertEquals(distant, pickAccent(primary, listOf(primary, distant)))

        val nearby = RgbColor(42, 72, 82) // within SIMILARITY_THRESHOLD of primary
        assertNull(pickAccent(primary, listOf(primary, nearby)))
    }
}
