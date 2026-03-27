package io.music_assistant.client

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.asLiveData
import androidx.lifecycle.lifecycleScope
import co.touchlab.kermit.Logger
import io.music_assistant.client.auth.AuthenticationManager
import io.music_assistant.client.auth.OAuthHandler
import io.music_assistant.client.data.MainDataSource
import io.music_assistant.client.services.MainMediaPlaybackService
import io.music_assistant.client.ui.compose.App
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {

    private val dataSource: MainDataSource by inject()
    private val authManager: AuthenticationManager by inject()
    private val oauthHandler: OAuthHandler by lazy {
        OAuthHandler(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Provide OAuthHandler to AuthenticationManager
        authManager.oauthHandler = oauthHandler

        // Handle OAuth callback if launched from deep link
        handleOAuthCallback(intent)

        dataSource.isAnythingPlaying.asLiveData()
            .observe(this) {
                if (it) {
                    val serviceIntent = Intent(this, MainMediaPlaybackService::class.java)
                    serviceIntent.action = "ACTION_PLAY"
                    lifecycleScope.launch {
                        // This allows app to start before showing notification -
                        // otherwise if something is playing on app start, service doesn't start...
                        delay(1000)
                        startForegroundService(serviceIntent)
                    }
                }
            }
        setContent {
            App()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleOAuthCallback(intent)
    }

    private fun handleOAuthCallback(intent: Intent?) {
        val data = intent?.data ?: return
        Logger.withTag("MainActivity").d("Deep link received: $data")

        // Handle musicassistant://auth/callback?code=...
        if (data.scheme == "musicassistant" && data.host == "auth" && data.path == "/callback") {
            val token = data.getQueryParameter("code")
            Logger.withTag("MainActivity").d("Custom scheme callback - token: ${token != null}")

            if (token != null) {
                // Deliver token directly to AuthenticationManager
                authManager.handleOAuthCallback(token)
            } else {
                Logger.withTag("MainActivity").e("No token in OAuth callback")
            }
        }
    }
}