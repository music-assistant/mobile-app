package io.music_assistant.client.wear

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.music_assistant.client.wear.data.OngoingPlayback
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import androidx.wear.ambient.AmbientLifecycleObserver
import io.music_assistant.client.wear.ui.AmbientState
import io.music_assistant.client.wear.ui.MaWearApp
import io.music_assistant.client.wear.ui.WearPlayerViewModel

/**
 * The watch has one activity. The server session follows the activity's visible lifetime:
 * a watch screen is on for seconds at a time, and holding a WebSocket open behind a dark
 * screen costs battery for state nobody is reading.
 *
 * Ambient is part of that visible lifetime: a dimmed screen that is still being read needs
 * the session as much as a bright one, or the always-on screen freezes on whatever was
 * playing when the wrist dropped. Which lifecycle callbacks a watch fires on the way into
 * ambient is not something to rely on, so the session is held against ambient rather than
 * against `onStop` alone, and released on whichever of the two ends last.
 */
class MainActivity : ComponentActivity() {
    private val app: WearApplication get() = application as WearApplication

    private val viewModel: WearPlayerViewModel by lazy {
        ViewModelProvider(
            this,
            WearPlayerViewModel.Factory(app.repository, app.artworkLoader),
        )[WearPlayerViewModel::class.java]
    }

    private var ambient: AmbientState by mutableStateOf(AmbientState.Interactive)

    private val ongoingPlayback by lazy { OngoingPlayback(this) }

    /**
     * Asked for once, on the first start. A refusal costs the ambient screen past its first
     * minute and nothing else, so there is no second ask and no explanation screen.
     */
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private fun canPostNotifications(): Boolean = checkSelfPermission(
        Manifest.permission.POST_NOTIFICATIONS,
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    /**
     * Registering the observer is what opts this activity into the always-on screen: without
     * it the watch leaves the app when the screen dims. The callbacks arrive on the main
     * thread. `onUpdateAmbient`, the once-a-minute beat, is not taken: nothing in the
     * always-on layout goes stale between updates, and the shift for a panel that burns in
     * is the system's to make, not ours.
     */
    private val ambientObserver by lazy {
        AmbientLifecycleObserver(
            this,
            object : AmbientLifecycleObserver.AmbientLifecycleCallback {
                override fun onEnterAmbient(
                    ambientDetails: AmbientLifecycleObserver.AmbientDetails,
                ) {
                    ambient = AmbientState.Ambient(
                        lowBitAmbient = ambientDetails.deviceHasLowBitAmbient,
                    )
                }

                override fun onExitAmbient() {
                    ambient = AmbientState.Interactive
                    // Ambient ending after the activity stopped is the watch leaving for its
                    // own face: the stop below was skipped, so it happens here instead.
                    if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                        releaseWhileAway()
                    }
                }
            },
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycle.addObserver(ambientObserver)
        if (!canPostNotifications()) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        // The playing chip is what keeps the task on screen past Wear's one-minute ambient
        // allowance, so it follows what is actually playing rather than the screen state.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state
                    .map { it.nowPlaying }
                    .distinctUntilChanged()
                    .collect(ongoingPlayback::update)
            }
        }
        setContent { MaWearApp(viewModel, ambient) }
    }

    override fun onStart() {
        super.onStart()
        app.session.start()
    }

    override fun onStop() {
        super.onStop()
        // A watch that reports ambient as a stop would otherwise tear the session down under
        // a screen the user is still reading.
        if (!ambient.isAmbient) {
            releaseWhileAway()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ongoingPlayback.hide()
    }

    /**
     * Everything that only earns its keep while the app is on the watch's screen, ambient
     * included. The chip goes with the session: without the session the track behind it stops
     * updating, and a chip naming a track that changed ten minutes ago is worse than none.
     */
    private fun releaseWhileAway() {
        app.session.stop()
        ongoingPlayback.hide()
    }
}
