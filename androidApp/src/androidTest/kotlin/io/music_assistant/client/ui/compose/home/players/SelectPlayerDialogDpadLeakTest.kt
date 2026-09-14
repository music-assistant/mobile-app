package io.music_assistant.client.ui.compose.home.players

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import io.music_assistant.client.data.model.client.PlayerDataFixtures
import io.music_assistant.client.ui.compose.common.providers.MdiCodepoints
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.compose.KoinApplication
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.koinConfiguration
import org.koin.dsl.module

/**
 * Reproduces the D-pad-leaks-out-of-the-player-switcher bug reported for Android TV (see
 * music-assistant issue #5162 and PR #809): a remote's up/down keys adjust whatever is focused
 * *behind* the dialog (the now-playing volume slider) instead of navigating the player list,
 * because nothing inside the dialog claims keyboard/D-pad focus.
 *
 * Why this one catches it when prior attempts didn't:
 * - Robolectric (`SelectPlayerDialogFocusTest`, and stricter dismiss-wired variants) does not
 *   simulate real Android Window focus-containment behavior at all.
 * - Compose's `performKeyInput` dispatches through Compose's own internal test harness rather
 *   than the real system input pipeline, so it passes even when the real behavior is broken.
 *
 * This version injects a real KeyEvent via `UiDevice.pressDPadDown()` (same mechanism as
 * `adb shell input keyevent`), routed by the system InputDispatcher to whatever holds real
 * OS-level window focus. It also stages the same preconditions as the real app: a focusable
 * "volume slider" element behind the dialog holds focus first, and the test asserts the key both
 * (a) lands on the second player row and (b) never reaches that background element.
 */
@RunWith(AndroidJUnit4::class)
class SelectPlayerDialogDpadLeakTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun dPadDownMovesFocusToNextRowAndStaysInDialog() {
        val kitchen = PlayerDataFixtures.playerData(name = "Kitchen")
        val livingRoom = PlayerDataFixtures.playerData(name = "Living Room")

        // Snapshot state, created off the UI thread so the test can read it directly.
        val isDialogOpen = mutableStateOf(false)
        val backgroundDpadDownCount = mutableStateOf(0)
        val backgroundFocusRequester = FocusRequester()

        composeTestRule.setContent {
            KoinApplication(
                configuration = koinConfiguration(declaration = {
                    modules(module { singleOf(::MdiCodepoints) })
                }),
                content = {
                    // Stand-in for the now-playing volume slider: focusable, focused *before* the
                    // dialog opens, and it records any DPAD_DOWN that leaks its way.
                    Column(
                        modifier = Modifier
                            .testTag("Background")
                            .focusRequester(backgroundFocusRequester)
                            .focusable()
                            .onKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                                    backgroundDpadDownCount.value++
                                }
                                false
                            },
                    ) {
                        BasicText(text = "Recently played")
                    }

                    if (isDialogOpen.value) {
                        SelectPlayerDialog(
                            selectedPlayer = kitchen,
                            players = listOf(kitchen, livingRoom),
                            onDismissRequest = { isDialogOpen.value = false },
                        )
                    }
                },
            )
        }

        // Stage the real-app precondition: the volume slider behind holds focus before the
        // player switcher opens.
        composeTestRule.runOnUiThread { backgroundFocusRequester.requestFocus() }
        composeTestRule.waitForFocus("Background")
        backgroundDpadDownCount.value = 0
        composeTestRule.runOnUiThread { isDialogOpen.value = true }

        // Part 1 of the fix: the dialog claims D-pad focus when it opens.
        composeTestRule.waitForDisplayed("PlayersList")
        composeTestRule.waitForFocus("PlayerRow-${kitchen.player.id}")

        // Part 2 of the fix: DOWN moves focus to the next row (the LazyColumn routes it through
        // focusManager.moveFocus) instead of leaking behind.
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.pressDPadDown()
        composeTestRule.waitForIdle()

        composeTestRule.waitForDisplayed("PlayersList")
        composeTestRule.waitForFocus("PlayerRow-${livingRoom.player.id}")
        if (backgroundDpadDownCount.value != 0) {
            throw AssertionError(
                "DPAD_DOWN leaked to the element behind the dialog (received " +
                    "${backgroundDpadDownCount.value} DOWN events); expected the dialog to consume it",
            )
        }
    }

    private fun ComposeTestRule.waitForFocus(tag: String, timeoutMillis: Long = 2000) {
        waitUntil(timeoutMillis) {
            runCatching { onNodeWithTag(tag).assertIsFocused() }.isSuccess
        }
    }

    private fun ComposeTestRule.waitForDisplayed(tag: String, timeoutMillis: Long = 2000) {
        waitUntil(timeoutMillis) {
            runCatching { onNodeWithTag(tag).assertIsDisplayed() }.isSuccess
        }
    }
}
