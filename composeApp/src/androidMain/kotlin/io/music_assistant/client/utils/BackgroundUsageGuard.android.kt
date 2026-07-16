package io.music_assistant.client.utils

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * Android implementation backed by the Application [Context] (Koin-provided, see AndroidModule).
 */
class AndroidBackgroundUsageGuard(
    private val context: Context,
) : BackgroundUsageGuard {

    override fun isBackgroundRestricted(): Boolean =
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
            .isBackgroundRestricted // API 28; minSdk = 28

    override fun openBackgroundUsageSettings() {
        // The background-usage restriction toggle (Unrestricted/Optimized/Restricted, i.e. what
        // isBackgroundRestricted() reads) lives on the app-details → Battery page. The battery-
        // optimization list (ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS) is a *different* setting
        // and doesn't even list an optimized app by default, so we target app details directly.
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) // required for an app-context start
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
        }
    }
}
