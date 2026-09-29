package io.music_assistant.client

import android.app.ForegroundServiceStartNotAllowedException
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.asLiveData
import co.touchlab.kermit.Logger
import io.music_assistant.client.api.DeepLinkBus
import io.music_assistant.client.auth.AuthenticationManager
import io.music_assistant.client.auth.CustomTabsOAuthHandler
import io.music_assistant.client.auth.OAuthHandler
import io.music_assistant.client.data.MainDataSource
import io.music_assistant.client.input.VolumeButtonService
import io.music_assistant.client.services.MainMediaPlaybackService
import io.music_assistant.client.settings.SettingsRepository
import io.music_assistant.client.ui.compose.App
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val dataSource: MainDataSource by inject()
    private val authManager: AuthenticationManager by inject()
    private val deepLinkBus: DeepLinkBus by inject()
    private val volumeButtonService: VolumeButtonService by inject()
    private val settings: SettingsRepository by inject()
    private val oauthHandler: OAuthHandler by lazy {
        CustomTabsOAuthHandler(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        resolveOrientationLock(resources.configuration)?.let { requestedOrientation = it }

        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Lock orientation on compact devices, unless the user opted out. The
        // manifest declares no screenOrientation, so re-applying this live only
        // triggers a configuration change the activity already handles itself.
        settings.allowLandscapeOnAllDevices.asLiveData()
            .observe(this) { allowLandscape ->
                requestedOrientation = if (!allowLandscape && isCompactDevice()) {
                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                } else {
                    ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
            }

        // Provide OAuthHandler to AuthenticationManager
        authManager.oauthHandler = oauthHandler

        // Handle OAuth callback / page deep link if launched from one
        handleIncomingUri(intent)

        dataSource.isAnythingPlaying.asLiveData()
            .observe(this) {
                if (it) {
                    val serviceIntent = Intent(this, MainMediaPlaybackService::class.java)
                    serviceIntent.action = "ACTION_PLAY"
                    try {
                        startForegroundService(serviceIntent)
                    } catch (e: Exception) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                            e is ForegroundServiceStartNotAllowedException
                        ) {
                            Logger.withTag("MainActivity")
                                .w("Cannot start foreground service from background, will retry when foregrounded")
                        } else {
                            throw e
                        }
                    }
                }
            }
        setContent {
            App()
        }
    }

    // TVs are landscape hardware regardless of smallestScreenWidthDp (this Google TV emulator's
    // 1920x1080 @ 320dpi display is 540dp smallest-width, under COMPACT_DEVICE_WIDTH) -- without
    // this guard the portrait lock below fights resolveOrientationLock's TV landscape lock and
    // wins (it re-applies on every allowLandscapeOnAllDevices emission, after onCreate's initial
    // lock), letterboxing the whole app into a portrait sliver on the TV screen. Verified live.
    private fun isCompactDevice() =
        !resources.configuration.isTelevision() &&
            resources.configuration.smallestScreenWidthDp <= COMPACT_DEVICE_WIDTH

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // singleTask reuses this instance, so this path is now live (it was dead
        // under the default standard launchMode). Keep getIntent() in sync.
        setIntent(intent)
        handleIncomingUri(intent)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if ((keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) &&
            event?.repeatCount == 0
        ) {
            volumeButtonService.onPlatformVolumeButtonPressed()
        }
        return super.onKeyDown(keyCode, event)
    }

    /**
     * Single dispatch point for incoming deep-link URIs. The OAuth callback is
     * peeled off explicitly; everything else (musicassistant://app/<page> and
     * the https App Link https://music-assistant.io/app/<page>) is forwarded to
     * [DeepLinkBus], which self-filters and ignores anything it doesn't
     * recognize. Only URIs matching a manifest intent-filter ever reach here.
     */
    private fun handleIncomingUri(intent: Intent?) {
        val data = intent?.data ?: return
        Logger.withTag("MainActivity").d("Deep link received: $data")

        // musicassistant://auth/callback?code=...
        if (authManager.handleOAuthCallbackUrl(data.toString())) return
        deepLinkBus.handle(data.toString())
    }

    companion object {
        internal const val COMPACT_DEVICE_WIDTH = 600
    }
}

/**
 * TVs are always landscape and never rotate, so that takes priority over the phone-width
 * check. Compact (phone-width) devices are locked to portrait. Larger/tablet configurations
 * are left as the platform default (free rotation) — hence the nullable return.
 */
internal fun resolveOrientationLock(configuration: Configuration): Int? = when {
    configuration.isTelevision() -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    configuration.smallestScreenWidthDp <= MainActivity.COMPACT_DEVICE_WIDTH ->
        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

    else -> null
}

internal fun Configuration.isTelevision(): Boolean =
    (uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION
