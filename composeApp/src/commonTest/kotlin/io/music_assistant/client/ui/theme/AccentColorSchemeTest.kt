package io.music_assistant.client.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AccentColorSchemeTest {
    private val seeded = AccentColor.entries.mapNotNull { accent -> accent.seed?.let { accent to it } }

    @Test
    fun `every seeded accent has a readable primary in light and in dark`() {
        seeded.forEach { (accent, seed) ->
            listOf(false, true).forEach { dark ->
                val scheme = seedColorScheme(seed, dark)
                val ratio = contrastRatio(scheme.primary, scheme.onPrimary)
                assertTrue(ratio >= 4.5, "$accent dark=$dark primary/onPrimary contrast $ratio")
            }
        }
    }

    @Test
    fun `a seeded accent keeps the tertiary and neutral roles of the default scheme`() {
        seeded.forEach { (accent, seed) ->
            listOf(false, true).forEach { dark ->
                val default = defaultColorScheme(dark)
                val scheme = seedColorScheme(seed, dark)
                assertEquals(default.tertiary, scheme.tertiary, "$accent dark=$dark tertiary")
                assertEquals(default.secondary, scheme.secondary, "$accent dark=$dark secondary")
                assertEquals(default.background, scheme.background, "$accent dark=$dark background")
                assertEquals(default.surface, scheme.surface, "$accent dark=$dark surface")
            }
        }
    }

    @Test
    fun `a seeded accent changes the primary and the surface tint`() {
        seeded.forEach { (accent, seed) ->
            val scheme = seedColorScheme(seed, darkTheme = false)
            assertTrue(scheme.primary != primaryLight, "$accent primary")
            assertEquals(scheme.primary, scheme.surfaceTint, "$accent surfaceTint")
        }
    }

    private fun contrastRatio(a: Color, b: Color): Float {
        val la = a.luminance() + 0.05f
        val lb = b.luminance() + 0.05f
        return maxOf(la, lb) / minOf(la, lb)
    }
}
