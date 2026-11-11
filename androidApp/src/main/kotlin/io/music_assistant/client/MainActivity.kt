package io.music_assistant.client

import android.app.ForegroundServiceStartNotAllowedException
import android.content.Intent
import android.content.pm.ActivityInfo
import android.media.AudioManager
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
import io.music_assistant.client.services.isAnyQueueItemPlaying
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
    private val audioManager: AudioManager by lazy { getSystemService(AudioManager::class.java) }

    // Volume key codes whose ACTION_DOWN went to the selected remote player.
    private val routedVolumeKeys = mutableSetOf<Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
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

        dataSource.isAnyQueueItemPlaying.asLiveData()
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

    private fun isCompactDevice() =
        resources.configuration.smallestScreenWidthDp <= COMPACT_DEVICE_WIDTH

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // singleTask reuses this instance, so this path is now live (it was dead
        // under the default standard launchMode). Keep getIntent() in sync.
        setIntent(intent)
        handleIncomingUri(intent)
    }

    /**
     * Volume keys reach the activity only while it has window focus, so the selected remote
     * player gets them only while the app is on screen. A key that went to the player on
     * ACTION_DOWN keeps its repeats and its ACTION_UP, so the system never sees half a press.
     * During a call or VoIP (mode not normal) the keys stay with the system.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val up = volumeKeyDirection(keyCode) ?: return super.onKeyDown(keyCode, event)
        val repeat = (event?.repeatCount ?: 0) > 0
        return when {
            repeat && keyCode in routedVolumeKeys -> {
                volumeButtonService.onVolumeKey(up, isRepeat = true)
                true
            }

            audioManager.mode == AudioManager.MODE_NORMAL && volumeButtonService.onVolumeKey(up, repeat) -> {
                routedVolumeKeys += keyCode
                true
            }

            else -> {
                routedVolumeKeys -= keyCode
                if (!repeat) volumeButtonService.onPlatformVolumeButtonPressed()
                super.onKeyDown(keyCode, event)
            }
        }
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean =
        routedVolumeKeys.remove(keyCode) || super.onKeyUp(keyCode, event)

    private fun volumeKeyDirection(keyCode: Int): Boolean? = when (keyCode) {
        KeyEvent.KEYCODE_VOLUME_UP -> true
        KeyEvent.KEYCODE_VOLUME_DOWN -> false
        else -> null
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // The ACTION_UP of a held key goes to the new focus owner.
        if (!hasFocus) routedVolumeKeys.clear()
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
        private const val COMPACT_DEVICE_WIDTH = 600
    }
}
