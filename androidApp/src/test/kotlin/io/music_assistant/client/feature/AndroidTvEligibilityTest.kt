package io.music_assistant.client.feature

import android.content.Context
import android.content.Intent
import android.content.pm.FeatureInfo
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Guards the manifest-level requirements Android TV / Google TV check before showing an app on
 * the TV home screen or accepting it on the Play Store for TV form factors.
 */
@RunWith(AndroidJUnit4::class)
class AndroidTvEligibilityTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val packageManager: PackageManager = context.packageManager

    @Test
    fun `app resolves as a Google TV leanback launcher entry`() {
        val leanbackLauncherIntent = Intent(Intent.ACTION_MAIN)
            .addCategory("android.intent.category.LEANBACK_LAUNCHER")
            .setPackage(context.packageName)

        val resolved = packageManager.queryIntentActivities(leanbackLauncherIntent, 0)

        assertTrue(
            resolved.any { it.activityInfo.name == "io.music_assistant.client.MainActivity" },
            "Expected MainActivity to resolve for the LEANBACK_LAUNCHER intent",
        )
    }

    @Test
    fun `declares a TV banner`() {
        val applicationInfo = packageManager.getApplicationInfo(context.packageName, 0)

        assertTrue(applicationInfo.banner != 0, "Expected android:banner to be set on <application>")
    }

    @Test
    fun `does not hard-require a camera`() {
        assertFalse(isFeatureRequired("android.hardware.camera"))
    }

    @Test
    fun `declares touchscreen and leanback as optional`() {
        assertFalse(isFeatureRequired("android.hardware.touchscreen"))
        assertFalse(isFeatureRequired("android.software.leanback"))
    }

    private fun isFeatureRequired(featureName: String): Boolean {
        val packageInfo = packageManager.getPackageInfo(context.packageName, PackageManager.GET_CONFIGURATIONS)
        val feature = packageInfo.reqFeatures.orEmpty().first { it.name == featureName }
        return feature.flags and FeatureInfo.FLAG_REQUIRED != 0
    }
}
