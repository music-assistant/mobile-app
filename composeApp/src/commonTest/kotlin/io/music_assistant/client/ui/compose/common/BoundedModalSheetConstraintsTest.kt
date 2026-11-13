package io.music_assistant.client.ui.compose.common

import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import kotlin.test.Test
import kotlin.test.assertEquals

class BoundedModalSheetConstraintsTest {
    private fun resolve(constraints: Constraints, window: IntSize) =
        boundedModalSheetConstraints(constraints, window, fallbackWidth = 1280, fallbackHeight = 1200)

    @Test
    fun unboundedAxesUsePortraitAndLandscapeWindow() {
        assertEquals(Constraints(maxWidth = 1260, maxHeight = 2736), resolve(Constraints(), IntSize(1260, 2736)))
        assertEquals(Constraints(maxWidth = 2736, maxHeight = 1260), resolve(Constraints(), IntSize(2736, 1260)))
    }

    @Test
    fun finiteAxesAndMinimumsArePreserved() {
        val finite = Constraints(minWidth = 30, maxWidth = 800, minHeight = 40, maxHeight = 900)
        assertEquals(finite, resolve(finite, IntSize(200, 300)))
        assertEquals(
            Constraints(minWidth = 500, maxWidth = 500, minHeight = 40, maxHeight = 900),
            resolve(Constraints(minWidth = 500, minHeight = 40, maxHeight = 900), IntSize(200, 300)),
        )
        assertEquals(
            Constraints(minWidth = 30, maxWidth = 800, minHeight = 500, maxHeight = 500),
            resolve(Constraints(minWidth = 30, maxWidth = 800, minHeight = 500), IntSize(200, 300)),
        )
    }

    @Test
    fun missingWindowDimensionsUseTheirFallbackAndRespectMinimums() {
        assertEquals(Constraints(maxWidth = 1280, maxHeight = 1200), resolve(Constraints(), IntSize.Zero))
        assertEquals(Constraints(maxWidth = 1260, maxHeight = 1200), resolve(Constraints(), IntSize(1260, 0)))
        assertEquals(Constraints(maxWidth = 1280, maxHeight = 2736), resolve(Constraints(), IntSize(0, 2736)))
        assertEquals(
            Constraints(minWidth = 1500, maxWidth = 1500, minHeight = 1400, maxHeight = 1400),
            resolve(Constraints(minWidth = 1500, minHeight = 1400), IntSize.Zero),
        )
    }
}
