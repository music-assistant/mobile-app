package io.music_assistant.client.ui.compose.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import io.music_assistant.client.ui.compose.common.TvFocusFlow
import io.music_assistant.client.ui.compose.common.rememberTvFocusFlow
import io.music_assistant.client.utils.SessionState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real-hardware regression coverage for the initial-configuration screen D-pad bug found on a
 * Chromecast with Google TV: focus lands on the centered "Exit App" button and Compose's
 * geometric focus search cannot move a remote's DOWN to the server address field below.
 *
 * Mounts the real production content composables ([DirectConnectionContent],
 * [WebRTCConnectionContent]) and the connection tabs with the same explicit `TvFocusFlow` links
 * that `SettingsScreen` uses, and drives real DPAD_DOWN/UP/LEFT key events via `UiDevice` (the
 * same mechanism as `adb shell input keyevent`, which previous Robolectric/`performKeyInput`
 * attempts could not reproduce). The production fix routes navigation through
 * `FocusProperties` links rather than trusting geometry, so this test asserts the framework's own
 * key dispatch honours those links.
 *
 * Text-field targets are verified by *typing* rather than `assertIsFocused`: requestFocus() on a
 * TextField lands on the field's internal text node, which is editable but does not reflect its
 * focus in the outer node's semantics, so `assertIsFocused` cannot see it. Button/checkbox/tab
 * targets focus on their own node and are verified with `assertIsFocused`.
 *
 * Key finding on this hardware: a focused TextField swallows D-pad in its own key handling before
 * the framework can honour the `FocusProperties` links on the field's node, so D-pad navigation
 * out of a text field never moves. `TvFocusFlow.modifierFor` therefore also intercepts directional
 * keys in the preview phase and routes them through the same explicit links. The tests still
 * dismiss the system keyboard with BACK before pressing D-pad to leave a text field (the Leanback
 * keyboard on real TVs captures D-pad until dismissed) — mirroring the remote flow of
 * "type, then press Done, then navigate on".
 */
@RunWith(AndroidJUnit4::class)
class SettingsConfigDpadNavigationTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun dPadDownMovesThroughDirectFlowAndUpReturns() {
        val host = mutableStateOf("")
        val port = mutableStateOf("")
        val isTls = mutableStateOf(false)
        val flowHolder = arrayOfNulls<TvFocusFlow>(1)

        composeTestRule.setContent {
            MaterialTheme {
                // Same wiring as SettingsScreen: each focusable declares its directional links
                // via FocusProperties and the framework's key dispatch honours them.
                val flow = rememberTvFocusFlow()
                flowHolder[0] = flow
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .testTag("SettingsColumn"),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Box(
                            modifier = flow.modifierFor("exitApp", directLinks.getValue("exitApp"))
                                .testTag("Config-ExitApp")
                                .focusable()
                                .clickable { }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        ) { Text("Exit App") }
                    }
                    PrimaryTabRow(selectedTabIndex = 0) {
                        Tab(
                            selected = true,
                            onClick = {},
                            modifier = flow.modifierFor("tabDirect", directLinks.getValue("tabDirect"))
                                .testTag("Config-TabDirect"),
                        ) { Text("Direct") }
                        Tab(
                            selected = false,
                            onClick = {},
                            modifier = flow.modifierFor("tabWebRTC", directLinks.getValue("tabWebRTC"))
                                .testTag("Config-TabWebRTC"),
                        ) { Text("WebRTC") }
                    }
                    DirectConnectionContent(
                        configFlow = flow,
                        configLinks = directLinks,
                        ipAddress = host.value,
                        port = port.value,
                        isTls = isTls.value,
                        hasToken = false,
                        onIpAddressChange = { host.value = it },
                        onPortChange = { port.value = it },
                        onTlsChange = { isTls.value = it },
                        onConnect = {},
                        enabled = true,
                        onShowHistory = {},
                    )
                }
            }
        }

        // Stage the pre-fix trap: focus on the Exit App button.
        composeTestRule.runOnUiThread { flowHolder[0]!!.requestFocus("exitApp") }
        composeTestRule.waitForFocus("Config-ExitApp")

        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

        // DOWN leaves the button via its explicit link and lands on the host field; typing proves
        // the field is focused.
        device.pressDPadDown()
        composeTestRule.waitForIdle()
        typeInto(device, android.view.KeyEvent.KEYCODE_H)
        check(host.value == "h") {
            "DOWN did not focus the host field (value='${host.value}')"
        }

        // Focusing the host field auto-opens the system keyboard, which may swallow D-pad until it
        // is dismissed. Close it, then DOWN to the port field.
        device.pressBack()
        composeTestRule.waitForIdle()
        Thread.sleep(500)
        device.pressDPadDown()
        composeTestRule.waitForIdle()
        typeInto(device, android.view.KeyEvent.KEYCODE_1)
        check(port.value == "1") {
            "DOWN did not focus the port field. host='${host.value}', port='${port.value}'"
        }

        // Port focus re-opened the Leanback keyboard; dismiss it before the next D-pad press.
        device.pressBack()
        composeTestRule.waitForIdle()
        Thread.sleep(500)

        // DOWN through the non-text targets (no keyboard involved).
        device.pressDPadDown()
        composeTestRule.waitForFocus("Config-Tls")
        device.pressDPadDown()
        composeTestRule.waitForFocus("Config-Connect")
        device.pressDPadDown()
        composeTestRule.waitForFocus("Config-History")

        // UP walks back up through the non-text targets.
        device.pressDPadUp()
        composeTestRule.waitForFocus("Config-Connect")
        device.pressDPadUp()
        composeTestRule.waitForFocus("Config-Tls")

        // UP back onto the port field (typing proves focus), then on to host.
        device.pressDPadUp()
        typeInto(device, android.view.KeyEvent.KEYCODE_2)
        check(port.value.contains("2")) {
            "UP did not focus the port field (value='${port.value}')"
        }
        device.pressBack()
        composeTestRule.waitForIdle()
        Thread.sleep(500)
        device.pressDPadUp()
        typeInto(device, android.view.KeyEvent.KEYCODE_X)
        check(host.value.contains("x")) {
            "UP did not focus the host field (value='${host.value}')"
        }

        // UP from the first field lands on the right-hand tab (explicit link), and LEFT crosses
        // to the left-hand tab. The final hop from the tab row back up to Exit App is geometric
        // and deliberately not asserted here — that crossing is not what this test pins down.
        device.pressBack()
        composeTestRule.waitForIdle()
        Thread.sleep(500)
        device.pressDPadUp()
        composeTestRule.waitForFocus("Config-TabWebRTC")
        device.pressDPadLeft()
        composeTestRule.waitForFocus("Config-TabDirect")
    }

    @Test
    fun dPadDownMovesThroughWebRtcFlow() {
        val remoteId = mutableStateOf(VALID_REMOTE_ID)
        val flowHolder = arrayOfNulls<TvFocusFlow>(1)

        composeTestRule.setContent {
            MaterialTheme {
                val flow = rememberTvFocusFlow()
                flowHolder[0] = flow
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .testTag("SettingsColumn"),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Box(
                            modifier = flow.modifierFor("exitApp", webrtcLinks.getValue("exitApp"))
                                .testTag("Config-ExitApp")
                                .focusable()
                                .clickable { }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        ) { Text("Exit App") }
                    }
                    PrimaryTabRow(selectedTabIndex = 1) {
                        Tab(
                            selected = false,
                            onClick = {},
                            modifier = flow.modifierFor("tabDirect", webrtcLinks.getValue("tabDirect"))
                                .testTag("Config-TabDirect"),
                        ) { Text("Direct") }
                        Tab(
                            selected = true,
                            onClick = {},
                            modifier = flow.modifierFor("tabWebRTC", webrtcLinks.getValue("tabWebRTC"))
                                .testTag("Config-TabWebRTC"),
                        ) { Text("WebRTC") }
                    }
                    WebRTCConnectionContent(
                        configFlow = flow,
                        configLinks = webrtcLinks,
                        remoteId = remoteId.value,
                        onRemoteIdChange = { remoteId.value = it },
                        onConnect = {},
                        sessionState = SessionState.Disconnected.Initial,
                        hasToken = false,
                        onShowHistory = {},
                    )
                }
            }
        }

        composeTestRule.runOnUiThread { flowHolder[0]!!.requestFocus("exitApp") }
        composeTestRule.waitForFocus("Config-ExitApp")

        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

        // DOWN to the remote-ID field; typing proves it is focused.
        device.pressDPadDown()
        composeTestRule.waitForIdle()
        typeInto(device, android.view.KeyEvent.KEYCODE_A)
        check(remoteId.value != VALID_REMOTE_ID) {
            "DOWN did not focus the remote-ID field (value='${remoteId.value}')"
        }

        // Restore a valid ID so the Connect button is enabled for the rest of the flow.
        composeTestRule.runOnUiThread { remoteId.value = VALID_REMOTE_ID }
        composeTestRule.waitForIdle()

        // Dismiss the auto-opened Leanback keyboard before navigating on with D-pad.
        device.pressBack()
        composeTestRule.waitForIdle()
        Thread.sleep(500)
        device.pressDPadDown()
        composeTestRule.waitForFocus("Config-Connect")
        device.pressDPadDown()
        composeTestRule.waitForFocus("Config-History")
        device.pressDPadUp()
        composeTestRule.waitForFocus("Config-Connect")
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
        private val directLinks = mapOf(
            "exitApp" to TvFocusFlow.Links(down = "host"),
            "tabDirect" to TvFocusFlow.Links(right = "tabWebRTC", down = "host"),
            "tabWebRTC" to TvFocusFlow.Links(left = "tabDirect", down = "host"),
            "host" to TvFocusFlow.Links(up = "tabWebRTC", down = "port"),
            "port" to TvFocusFlow.Links(up = "host", down = "tls"),
            "tls" to TvFocusFlow.Links(up = "port", down = "connect"),
            "connect" to TvFocusFlow.Links(up = "tls", down = "history", right = "history"),
            "history" to TvFocusFlow.Links(up = "connect", left = "connect"),
        )

        private val webrtcLinks = mapOf(
            "exitApp" to TvFocusFlow.Links(down = "remoteId"),
            "tabDirect" to TvFocusFlow.Links(right = "tabWebRTC", down = "remoteId"),
            "tabWebRTC" to TvFocusFlow.Links(left = "tabDirect", down = "remoteId"),
            "remoteId" to TvFocusFlow.Links(up = "tabWebRTC", down = "connect"),
            "connect" to TvFocusFlow.Links(up = "remoteId", down = "history", right = "history"),
            "history" to TvFocusFlow.Links(up = "connect", left = "connect"),
        )

        private const val VALID_REMOTE_ID = "PGSVXKGZJCFA6MOH4UPBH5Q9HY"
    }
}
