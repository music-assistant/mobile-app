package io.music_assistant.client.ui.compose.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import kotlinx.coroutines.delay
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real-hardware coverage for the TV settings row/editor pattern ([TvPreferenceRow],
 * [TvTextEditorDialog]): rows are D-pad traversable via explicit `TvFocusFlow` links, selecting a
 * row opens the full-window editor dialog, Done commits the typed value back to the row while
 * Cancel discards it, and focus returns to the row that opened the editor.
 *
 * Mounts the real production composables and drives real DPAD key events via `UiDevice`, the same
 * mechanism as the config-screen tests and as `adb shell input keyevent`.
 */
@RunWith(AndroidJUnit4::class)
class TvPreferenceRowDialogTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun rowsNavigateAndEditorCommitReturnsFocus() {
        val valueA = mutableStateOf("original")
        val editingA = mutableStateOf(false)
        val flowHolder = arrayOfNulls<TvFocusFlow>(1)

        composeTestRule.setContent {
            MaterialTheme {
                val flow = rememberTvFocusFlow()
                flowHolder[0] = flow
                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    TvPreferenceRow(
                        label = "Label A",
                        value = valueA.value,
                        onClick = { editingA.value = true },
                        focusModifier = flow.modifierFor("rowA", rowLinks.getValue("rowA"))
                            .testTag("RowA"),
                    )
                    TvPreferenceRow(
                        label = "Label B",
                        value = null,
                        onClick = {},
                        focusModifier = flow.modifierFor("rowB", rowLinks.getValue("rowB"))
                            .testTag("RowB"),
                    )
                }
                if (editingA.value) {
                    TvTextEditorDialog(
                        title = "Edit A",
                        initialValue = valueA.value,
                        onConfirm = { valueA.value = it; editingA.value = false },
                        onDismiss = { editingA.value = false },
                    )
                }
                LaunchedEffect(editingA.value) {
                    if (!editingA.value) {
                        delay(300)
                        flow.requestFocus("rowA")
                    }
                }
            }
        }

        composeTestRule.runOnUiThread { flowHolder[0]!!.requestFocus("rowA") }
        composeTestRule.waitForFocus("RowA")

        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

        // D-pad traverses the rows through the explicit links.
        device.pressDPadDown()
        composeTestRule.waitForFocus("RowB")
        device.pressDPadUp()
        composeTestRule.waitForFocus("RowA")

        // Selecting the row opens the editor; the dialog field gets focus (typing proves it).
        device.pressDPadCenter()
        composeTestRule.waitForIdle()
        Thread.sleep(800)
        typeInto(device, android.view.KeyEvent.KEYCODE_X)
        check(editingA.value) { "CENTER on the row did not open the editor" }

        // A focused TextField on this hardware captures D-pad in the system IME layer (the Leanback
        // keyboard reopens and consumes the key), so the real TV flow is to press the keyboard's
        // Done key. KEYCODE_ENTER triggers the field's ImeAction.Done -> commit.
        device.pressKeyCode(android.view.KeyEvent.KEYCODE_ENTER)
        composeTestRule.waitForIdle()
        Thread.sleep(500)
        check(!editingA.value) { "IME Done did not commit" }
        check(valueA.value.contains("x")) {
            "Done committed a value without the typed char (value='${valueA.value}')"
        }

        // The settings screen re-lands focus on the row that opened the editor.
        composeTestRule.waitForFocus("RowA")
    }

    @Test
    fun cancelDiscardsAndReturnsToRow() {
        val valueA = mutableStateOf("original")
        val editingA = mutableStateOf(false)
        val flowHolder = arrayOfNulls<TvFocusFlow>(1)

        composeTestRule.setContent {
            MaterialTheme {
                val flow = rememberTvFocusFlow()
                flowHolder[0] = flow
                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    TvPreferenceRow(
                        label = "Label A",
                        value = valueA.value,
                        onClick = { editingA.value = true },
                        focusModifier = flow.modifierFor("rowA", rowLinks.getValue("rowA"))
                            .testTag("RowA"),
                    )
                    TvPreferenceRow(
                        label = "Label B",
                        value = null,
                        onClick = {},
                        focusModifier = flow.modifierFor("rowB", rowLinks.getValue("rowB"))
                            .testTag("RowB"),
                    )
                }
                if (editingA.value) {
                    TvTextEditorDialog(
                        title = "Edit A",
                        initialValue = valueA.value,
                        onConfirm = { valueA.value = it; editingA.value = false },
                        onDismiss = { editingA.value = false },
                    )
                }
                LaunchedEffect(editingA.value) {
                    if (!editingA.value) {
                        delay(300)
                        flow.requestFocus("rowA")
                    }
                }
            }
        }

        composeTestRule.runOnUiThread { flowHolder[0]!!.requestFocus("rowA") }
        composeTestRule.waitForFocus("RowA")

        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

        device.pressDPadCenter()
        composeTestRule.waitForIdle()
        Thread.sleep(800)
        typeInto(device, android.view.KeyEvent.KEYCODE_Y)
        check(editingA.value) { "CENTER on the row did not open the editor" }

        // Real TV cancel flow: the system BACK key closes the open keyboard first, then a second
        // BACK dismisses the dialog without committing.
        device.pressBack()
        composeTestRule.waitForIdle()
        Thread.sleep(500)
        device.pressBack()
        composeTestRule.waitForIdle()
        Thread.sleep(500)
        check(!editingA.value) { "BACK did not dismiss the editor" }
        check(valueA.value == "original") {
            "BACK must discard the edit (value='${valueA.value}')"
        }

        composeTestRule.waitForFocus("RowA")
    }

    private fun ComposeTestRule.waitForFocus(tag: String, timeoutMillis: Long = 3000) {
        waitUntil(timeoutMillis) {
            runCatching { onNodeWithTag(tag).assertIsFocused() }.isSuccess
        }
    }

    private fun typeInto(device: UiDevice, keyCode: Int) {
        device.pressKeyCode(keyCode)
        composeTestRule.waitForIdle()
        Thread.sleep(300)
    }

    companion object {
        private val rowLinks = mapOf(
            "rowA" to TvFocusFlow.Links(down = "rowB"),
            "rowB" to TvFocusFlow.Links(up = "rowA"),
        )
    }
}
