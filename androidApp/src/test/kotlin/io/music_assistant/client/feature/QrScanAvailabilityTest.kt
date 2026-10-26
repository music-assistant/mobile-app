package io.music_assistant.client.feature

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.music_assistant.client.support.get
import io.music_assistant.client.support.launchApp
import io.music_assistant.client.support.rules.createTestRuleChain
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.cd_scan_qr_code
import musicassistantclient.composeapp.generated.resources.settings_connection_webrtc
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/**
 * The remote-pairing QR scanner needs a camera, which Android TV / Google TV devices don't have.
 * The manual remote-ID field stays available either way.
 */
@RunWith(AndroidJUnit4::class)
class QrScanAvailabilityTest {
    @get:Rule
    val testRuleChain = createTestRuleChain()

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `shows the QR scan button when the device has a camera`() {
        setCameraAvailable(true)

        launchApp(composeTestRule)
        composeTestRule.onNodeWithText(Res.string.settings_connection_webrtc.get()).performClick()

        composeTestRule.onNodeWithContentDescription(Res.string.cd_scan_qr_code.get())
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `hides the QR scan button when the device has no camera`() {
        setCameraAvailable(false)

        launchApp(composeTestRule)
        composeTestRule.onNodeWithText(Res.string.settings_connection_webrtc.get()).performClick()

        composeTestRule.onNodeWithContentDescription(Res.string.cd_scan_qr_code.get()).assertIsNotDisplayed()
    }

    private fun setCameraAvailable(available: Boolean) {
        val packageManager = ApplicationProvider.getApplicationContext<Context>().packageManager
        shadowOf(packageManager).setSystemFeature(PackageManager.FEATURE_CAMERA_ANY, available)
    }
}
