package io.music_assistant.client.ui.compose.home.players

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class VolumeSliderBoxTest {
    // A test dispatcher as the effect context puts the hint timer on the main clock.
    @get:Rule
    val composeTestRule = createComposeRule(StandardTestDispatcher())

    private var stepsDown = 0
    private var stepsUp = 0

    @Before
    fun setUp() {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            VolumeSliderBox(
                modifier = Modifier.fillMaxWidth().testTag(BOX),
                volume = { VOLUME },
                onStepDown = { stepsDown++ },
                onStepUp = { stepsUp++ },
            ) {
                // The gesture lives on the box, so a plain slot stands in for the slider.
                Box(modifier = Modifier.fillMaxWidth().height(40.dp))
            }
        }
        composeTestRule.mainClock.advanceTimeByFrame()
    }

    private fun hint() = composeTestRule.onNodeWithTag(VolumeSliderSemantics.STEP_HINT_TAG)

    /** Delivers the gesture's state write to the recomposer, then runs the frames of the overlay. */
    private fun settle() {
        composeTestRule.waitForIdle()
        composeTestRule.mainClock.advanceTimeBy(SETTLE_MILLIS)
    }

    @Test
    fun `tap shows the step hint and hides it after 3 seconds`() {
        composeTestRule.onNodeWithTag(BOX).performTouchInput { click(Offset(width * 0.1f, centerY)) }
        assertEquals(1, stepsDown)
        assertEquals(0, stepsUp)

        settle()
        hint().assertExists("hint after a tap")

        composeTestRule.mainClock.advanceTimeBy(HINT_MILLIS - 2 * SETTLE_MILLIS)
        hint().assertExists("hint before the 3 seconds end")

        composeTestRule.mainClock.advanceTimeBy(2 * SETTLE_MILLIS + FADE_MILLIS)
        hint().assertDoesNotExist()
    }

    @Test
    fun `drag does not show the step hint`() {
        composeTestRule.onNodeWithTag(BOX).performTouchInput {
            swipe(start = Offset(width * 0.1f, centerY), end = Offset(width * 0.3f, centerY))
        }
        settle()

        hint().assertDoesNotExist()
        assertEquals(0, stepsDown)
        assertEquals(0, stepsUp)
    }

    private companion object {
        const val BOX = "box"
        const val VOLUME = 50f
        const val SETTLE_MILLIS = 300L
        const val HINT_MILLIS = 3_000L
        const val FADE_MILLIS = 2_000L
    }
}
