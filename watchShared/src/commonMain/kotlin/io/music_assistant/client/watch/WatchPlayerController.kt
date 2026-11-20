package io.music_assistant.client.watch

import io.music_assistant.client.watch.data.MaRepository
import io.music_assistant.client.watch.data.MaSession
import io.music_assistant.client.watch.data.SessionState
import io.music_assistant.client.watch.data.WatchPreferences
import io.music_assistant.client.watch.data.WatchServerConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the preferences and the device name look like on this platform. */
internal expect fun defaultWatchPreferences(): WatchPreferences

/** Shown in the server's session list, so the user can tell the watch from the phone. */
internal expect fun watchDeviceName(): String

/** A running [WatchPlayerController.observe]. Cancelling it stops the callbacks. */
class WatchSubscription internal constructor(private val job: Job) {
    fun cancel() {
        job.cancel()
    }
}

/**
 * The whole watch client behind one object, built for Swift to hold.
 *
 * SwiftUI cannot collect a Kotlin `Flow`, so the state the screens render is published through
 * [observe], which delivers on the main thread and hands back a handle to cancel. Commands are
 * forwarded unchanged; the repository owns the optimism.
 *
 * The object graph is built by hand, as it is on Wear OS: four long-lived objects and no
 * platform bindings to resolve, where a container would be more machinery than the app has
 * moving parts.
 */
class WatchPlayerController internal constructor(
    config: WatchServerConfig,
    preferences: WatchPreferences,
    deviceName: String,
) {
    /** What the app uses: the server from `local.properties`, this watch's own storage. */
    constructor() : this(
        config = WatchServerConfig.fromBuildConfig(),
        preferences = defaultWatchPreferences(),
        deviceName = watchDeviceName(),
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Callbacks land on the main thread, because SwiftUI reads them straight into its state.
    private val callbackScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val session = MaSession(config = config, scope = scope, deviceName = deviceName)

    private val repository = MaRepository(
        session = session,
        config = config,
        preferences = preferences,
        scope = scope,
    )

    private val states: StateFlow<WatchUiState> = combine(
        repository.sessionState,
        repository.players,
        repository.selectedPlayer,
        repository.nowPlaying,
        combine(repository.queueItems, repository.activeQueueItemId, ::Pair),
    ) { sessionState, players, selected, nowPlaying, queueState ->
        val (queue, activeItemId) = queueState
        WatchUiState(
            status = sessionState.status(),
            statusMessage = sessionState.message(),
            players = players,
            selected = selected,
            nowPlaying = nowPlaying,
            queue = queue,
            activeQueueItemId = activeItemId,
        )
    }.stateIn(scope, SharingStarted.Eagerly, WatchUiState())

    /** The snapshot as it stands, for the first frame before [observe] delivers anything. */
    val currentState: WatchUiState get() = states.value

    /** Opens the session. Safe to call again: a live session is left alone. */
    fun start() {
        session.start()
    }

    /** Closes the session. The watch holds no socket while the app is off screen. */
    fun stop() {
        session.stop()
    }

    /** Delivers every snapshot, starting with the current one, until the handle is cancelled. */
    fun observe(onState: (WatchUiState) -> Unit): WatchSubscription =
        WatchSubscription(callbackScope.launch { states.collect(onState) })

    fun togglePlayPause() = repository.togglePlayPause()

    fun next() = repository.next()

    fun previous() = repository.previous()

    fun setVolume(level: Int) = repository.setVolume(level)

    fun selectPlayer(playerId: String) = repository.selectPlayer(playerId)

    fun transferPlayback(playerId: String) = repository.transferPlayback(playerId)

    fun playQueueItem(itemId: String) = repository.playQueueItem(itemId)

    fun removeQueueItem(itemId: String) = repository.removeQueueItem(itemId)

    private fun SessionState.status(): WatchSessionStatus = when (this) {
        SessionState.Idle -> WatchSessionStatus.IDLE
        SessionState.Connecting -> WatchSessionStatus.CONNECTING
        is SessionState.Connected -> WatchSessionStatus.CONNECTED
        is SessionState.Reconnecting -> WatchSessionStatus.RECONNECTING
        is SessionState.Unauthorized -> WatchSessionStatus.UNAUTHORIZED
        SessionState.NotConfigured -> WatchSessionStatus.NOT_CONFIGURED
    }

    private fun SessionState.message(): String? =
        (this as? SessionState.Unauthorized)?.message
}
