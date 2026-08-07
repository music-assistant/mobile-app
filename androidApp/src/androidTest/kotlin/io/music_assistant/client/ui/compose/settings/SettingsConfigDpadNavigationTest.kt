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
 * On TV the config form uses the settings row pattern: host/port/remote-ID are [TvPreferenceRow]s
 * that open the full-window [TvTextEditorDialog] when selected, and buttons/checkbox/tabs are
 * ordinary focusable targets. Rows and buttons focus on their own node and are verified with
 * `assertIsFocused`; the row editors are verified by *committing* — selecting the row, typing into
 * the dialog's field, and pressing the keyboard's Done key, then checking the row's value state.
 *
 * Key finding on this hardware: a focused TextField swallows D-pad in the system IME layer (the
 * Leanback keyboard reopens and consumes the key), so D-pad navigation out of a text field never
 * moves — in the main window *and* inside the editor dialog. Editors are therefore committed via
 * the IME action (Done), and the dialog's own Done/Cancel buttons are secondary targets that the
 * remote reaches by walking the focus chain, not the primary flow.
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

        // DOWN leaves the button via its explicit link and lands on the host row. On TV the host is
        // a preference row: selecting it opens the full-window editor dialog, typing lands in its
        // field, and the keyboard's Done key (KEYCODE_ENTER) commits back to the row.
        device.pressDPadDown()
        composeTestRule.waitForFocus("Config-Host")
        editRowValue(device, android.view.KeyEvent.KEYCODE_H)
        check(host.value == "h") {
            "Host row editor did not commit (value='${host.value}')"
        }
        composeTestRule.waitForFocus("Config-Host")

        // DOWN to the port row; same editor flow.
        device.pressDPadDown()
        composeTestRule.waitForFocus("Config-Port")
        editRowValue(device, android.view.KeyEvent.KEYCODE_1)
        check(port.value == "1") {
            "Port row editor did not commit. host='${host.value}', port='${port.value}'"
        }
        composeTestRule.waitForFocus("Config-Port")

        // DOWN through the non-text targets.
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

        // UP back onto the port row and then the host row (the editor commits prove both are
        // reachable and editable from their rows).
        device.pressDPadUp()
        composeTestRule.waitForFocus("Config-Port")
        device.pressDPadUp()
        composeTestRule.waitForFocus("Config-Host")

        // UP from the first row lands on the right-hand tab (explicit link), and LEFT crosses to
        // the left-hand tab. The final hop from the tab row back up to Exit App is geometric and
        // deliberately not asserted here — that crossing is not what this test pins down.
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

        // DOWN to the remote-ID row; selecting it opens the editor dialog and typing proves the
        // field is focused once the edit is committed with the keyboard's Done key.
        device.pressDPadDown()
        composeTestRule.waitForFocus("Config-RemoteId")
        editRowValue(device, android.view.KeyEvent.KEYCODE_A)
        check(remoteId.value != VALID_REMOTE_ID) {
            "Remote-ID row editor did not commit (value='${remoteId.value}')"
        }

        // Restore a valid ID so the Connect button is enabled for the rest of the flow, and wait
        // for focus to return to the row that opened the editor.
        composeTestRule.runOnUiThread { remoteId.value = VALID_REMOTE_ID }
        composeTestRule.waitForIdle()
        composeTestRule.waitForFocus("Config-RemoteId")

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

    /**
     * The TV editor flow for a preference row: CENTER opens the full-window dialog, the typed key
     * lands in its field, and the keyboard's Done key (KEYCODE_ENTER) commits back to the row.
     * The dialog's field cannot receive D-pad on this hardware (the Leanback keyboard reopens and
     * swallows it), so commit goes through the IME action, mirroring the real remote flow.
     */
    private fun editRowValue(device: UiDevice, keyCode: Int) {
        device.pressDPadCenter()
        composeTestRule.waitForIdle()
        Thread.sleep(800)
        device.pressKeyCode(keyCode)
        composeTestRule.waitForIdle()
        Thread.sleep(300)
        device.pressKeyCode(android.view.KeyEvent.KEYCODE_ENTER)
        composeTestRule.waitForIdle()
        Thread.sleep(500)
        // The settings screen re-lands focus on the row that opened the editor via a
        // `LaunchedEffect { delay(150); requestFocus(returnTo) }`. Under the test's virtual clock
        // that delay only completes when the clock advances, so flush it here — otherwise it fires
        // mid-way through the later D-pad navigation and yanks focus back to this row.
        composeTestRule.mainClock.advanceTimeBy(200)
        composeTestRule.waitForIdle()
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
