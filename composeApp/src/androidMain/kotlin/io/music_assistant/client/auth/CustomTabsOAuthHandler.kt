package io.music_assistant.client.auth

import android.app.Activity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri

/**
 * Opens the OAuth page in a Chrome Custom Tab.
 *
 * The tab is a separate task, so the app backgrounds and gets no callback when the user
 * backs out of it — hence [reportsCancellation] is false and abandonment stays inferred
 * from the next foreground event.
 */
class CustomTabsOAuthHandler(private val activity: Activity) : OAuthHandler {
    override val reportsCancellation = false

    override fun openOAuthUrl(url: String) {
        val builder = CustomTabsIntent.Builder()
        val customTabsIntent = builder.build()
        customTabsIntent.launchUrl(activity, url.toUri())
    }
}
