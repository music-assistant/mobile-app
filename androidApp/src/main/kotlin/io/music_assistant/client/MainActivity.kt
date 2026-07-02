package io.music_assistant.client

import android.app.ForegroundServiceStartNotAllowedException
import android.content.Intent
import android.content.pm.ActivityInfo
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
import io.music_assistant.client.auth.OAuthHandler
import io.music_assistant.client.data.MainDataSource
import io.music_assistant.client.input.VolumeButtonService
import io.music_assistant.client.services.MainMediaPlaybackService
import io.music_assistant.client.ui.compose.App
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val dataSource: MainDataSource by inject()
    private val authManager: AuthenticationManager by inject()
    private val deepLinkBus: DeepLinkBus by inject()
    private val volumeButtonService: VolumeButtonService by inject()
    private val oauthHandler: OAuthHandler by lazy {
        OAuthHandler(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Lock orientation on compact devices
        if (this.resources.configuration.smallestScreenWidthDp <= COMPACT_DEVICE_WIDTH) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

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
        if (data.scheme == "musicassistant" && data.host == "auth" && data.path == "/callback") {
            val token = data.getQueryParameter("code")
            Logger.withTag("MainActivity").d("OAuth callback - token: ${token != null}")
            if (token != null) {
                authManager.handleOAuthCallback(token)
            } else {
                Logger.withTag("MainActivity").e("No token in OAuth callback")
            }
            return
        }
        deepLinkBus.handle(data.toString())
    }

    companion object {
        private const val COMPACT_DEVICE_WIDTH = 600
    }
}
