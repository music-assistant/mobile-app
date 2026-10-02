package io.music_assistant.client.ui.compose.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class ClearFocusOnScrollTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private var clearFocusCalls = 0

    // Counts clearFocus calls. Asserting on focus itself is not possible here: Robolectric runs in
    // non-touch mode, where the platform re-focuses the first focusable right after a clear.
    private inner class RecordingFocusManager(private val real: FocusManager) : FocusManager by real {
        override fun clearFocus(force: Boolean) {
            clearFocusCalls++
            real.clearFocus(force)
        }
    }

    private fun setContent() = composeTestRule.setContent {
        val realFocusManager = LocalFocusManager.current
        val focusManager = remember(realFocusManager) { RecordingFocusManager(realFocusManager) }
        CompositionLocalProvider(LocalFocusManager provides focusManager) {
            Column(
                Modifier
                    .testTag(COLUMN)
                    .fillMaxSize()
                    .clearFocusOnScroll()
                    .verticalScroll(rememberScrollState()),
            ) {
                BasicTextField(value = "", onValueChange = {}, modifier = Modifier.testTag(FIELD))
                Spacer(Modifier.height(3000.dp))
                BasicText(BOTTOM)
            }
        }
    }

    // A semantics scroll reaches nested scroll as UserInput. Clearing focus on it re-focuses the
    // first focusable child, whose bring-into-view scrolls again: the UI never goes idle.
    @Test
    fun `programmatic scroll keeps focus and settles`() {
        setContent()
        composeTestRule.onNodeWithTag(FIELD).performClick().assertIsFocused()

        composeTestRule.onNodeWithText(BOTTOM).performScrollTo().assertIsDisplayed()

        composeTestRule.onNodeWithTag(FIELD).assertIsFocused()
        assertEquals(0, clearFocusCalls)
    }

    @Test
    fun `vertical drag clears focus`() {
        setContent()
        composeTestRule.onNodeWithTag(FIELD).performClick().assertIsFocused()

        composeTestRule.onNodeWithTag(COLUMN).performTouchInput { swipeUp() }

        assertTrue(clearFocusCalls > 0)
    }

    private companion object {
        const val COLUMN = "column"
        const val FIELD = "field"
        const val BOTTOM = "bottom"
    }
}
