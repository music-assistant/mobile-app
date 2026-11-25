package io.music_assistant.client.wear.ui

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class ControlSizesTest {
    private val sizes = ControlSizes(
        playButton = 48.dp,
        ring = 68.dp,
        sideButton = 40.dp,
        sideIcon = 20.dp,
        gap = 8.dp,
        actionButton = 32.dp,
        topInset = 32.dp,
        spacing = 12.dp,
    )

    @Test
    fun `the transport row measures the ring, not the play button`() {
        // 68 + (8 + 40) * 2: the ring is what the middle of the row actually occupies, and
        // mistaking it for the 48 dp button is what let the row overflow in the first place.
        assertEquals(164f, sizes.transportWidth.value)
    }

    @Test
    fun `a row that already fits is left alone`() {
        assertSame(sizes, sizes.fitTo(sizes.transportWidth))
        assertSame(sizes, sizes.fitTo(sizes.transportWidth + 40.dp))
    }

    @Test
    fun `a row that does not fit is shrunk to exactly the width it is given`() {
        // The gap scales with the buttons; a gap left at full size would overshoot by twice
        // what it did not give up.
        val available = sizes.transportWidth - 24.dp
        assertEquals(available.value, sizes.fitTo(available).transportWidth.value, TOLERANCE)
    }

    @Test
    fun `every transport size shrinks by the same factor`() {
        val fitted = sizes.fitTo(sizes.transportWidth * HALF)
        listOf(
            sizes.playButton to fitted.playButton,
            sizes.ring to fitted.ring,
            sizes.sideButton to fitted.sideButton,
            sizes.sideIcon to fitted.sideIcon,
            sizes.gap to fitted.gap,
        ).forEach { (before, after) ->
            assertEquals(before.value * HALF, after.value, TOLERANCE)
        }
    }

    @Test
    fun `the rim button and the insets do not scale`() {
        val fitted = sizes.fitTo(sizes.transportWidth * HALF)
        assertEquals(sizes.actionButton, fitted.actionButton)
        assertEquals(sizes.topInset, fitted.topInset)
        assertEquals(sizes.spacing, fitted.spacing)
    }

    @Test
    fun `no watch in circulation has to scale`() {
        // Scaling is the fallback, not the usual path: the narrowest round Wear screens are
        // about 180 dp, and the large size set only applies from 225 dp up.
        assertSame(SMALL_SCREEN_SIZES, SMALL_SCREEN_SIZES.fitTo(available(NARROWEST_SCREEN_DP)))
        assertSame(
            LARGE_SCREEN_SIZES,
            LARGE_SCREEN_SIZES.fitTo(available(LARGE_SCREEN_WIDTH_DP)),
        )
    }

    /** What [controlSizes] hands [ControlSizes.fitTo] for a screen this wide. */
    private fun available(screenWidthDp: Int) = screenWidthDp.dp - RIM_INSET * 2

    private companion object {
        const val TOLERANCE = 0.01f
        const val HALF = 0.5f

        /** Galaxy Watch 40 mm: 360 px at 2.0, the narrowest this app is expected on. */
        const val NARROWEST_SCREEN_DP = 180
    }
}
