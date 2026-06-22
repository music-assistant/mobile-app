// Service-level timing values (debounce intervals, retry delays) are inline-documented at use site.
@file:Suppress("MagicNumber")

package io.music_assistant.client.services

import android.app.ForegroundServiceStartNotAllowedException
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.media.MediaBrowserServiceCompat
import androidx.media.utils.MediaConstants
import io.music_assistant.client.R
import io.music_assistant.client.auto.AutoLibrary
import io.music_assistant.client.auto.MediaIds
import io.music_assistant.client.auto.androidAutoLog
import io.music_assistant.client.auto.toUri
import io.music_assistant.client.data.MainDataSource
import io.music_assistant.client.utils.DataConnectionState
import io.music_assistant.client.utils.SessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.media_error_connection_lost
import musicassistantclient.composeapp.generated.resources.media_error_local_player_off
import musicassistantclient.composeapp.generated.resources.media_error_reconnecting
import org.jetbrains.compose.resources.getString
import org.koin.android.ext.android.inject

class AndroidAutoPlaybackService : MediaBrowserServiceCompat() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private lateinit var defaultIconUri: Uri

    private val dataSource: MainDataSource by inject()
    private val library: AutoLibrary by inject()
    private val sharedSession: SharedMediaSessionManager by inject()
    private val settingsRepository: io.music_assistant.client.settings.SettingsRepository by inject()
    private val localPlayer = dataSource.localPlayer

    // Promoted only once a real media host (not SystemUI's notification rendering) binds.
    // Until then this service is a passive browse/lifetime holder of the shared session.
    private var promotedToHost = false

    override fun onCreate() {
        super.onCreate()
        androidAutoLog.i { "onCreate — acquiring shared session" }
        sessionToken = sharedSession.acquire()
        defaultIconUri = R.drawable.baseline_library_music_24.toUri(this)
        observeLibraryTabsConfig()
        ensureNotificationService()
    }

    // Phone-side Customize Tabs changes must propagate to AA without requiring
    // a reconnect. Drop the initial value so we don't notify on cold start.
    private fun observeLibraryTabsConfig() {
        scope.launch {
            settingsRepository.libraryCategoryConfig
                .drop(1)
                .collect { notifyChildrenChanged(MediaIds.ROOT) }
        }
    }

    /**
     * SystemUI binds this MediaBrowserService just to render the notification's media
     * controls — it is NOT a real media host. Treating it as one (seizing the session,
     * probing/reconnecting the transport) is what zombified the notification on remote
     * players (issue #519). Promote to a real AA host — register the browse/voice play
     * handler and signal an external consumer — only for non-SystemUI binders.
     */
    private fun promoteIfRealHost(packageName: String) {
        if (promotedToHost || packageName == SYSTEMUI_PACKAGE) return
        promotedToHost = true
        androidAutoLog.i { "Real media host '$packageName' — promoting to AA owner" }
        sharedSession.bindAutoHost(autoPlayHandler)
        dataSource.apiClient.onExternalConsumerActive()
        observeSessionState()
        observeLocalPlayer()
    }

    private val autoPlayHandler = object : SharedMediaSessionManager.AutoPlayHandler {
        override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
            // No readiness wait (unlike onPlayFromSearch): browse tap can only fire
            // after the user navigated to a media item, so the local player is up.
            mediaId?.let { library.play(it, extras) }
        }

        override fun onPlayFromSearch(query: String?, extras: Bundle?) {
            @Suppress("DEPRECATION") // Bundle.get(key) is the only untyped log-dump accessor.
            val extrasDump = extras?.keySet()?.joinToString { k -> "$k=${extras.get(k)}" }
            androidAutoLog.i { "onPlayFromSearch query=\"$query\" extras={$extrasDump}" }
            if (query.isNullOrBlank() && extras == null) {
                androidAutoLog.w { "Blank query AND null extras — nothing to act on, no-op." }
                return
            }
            // Cold-start case (phone-side voice dispatch via MediaBrowser bind):
            // the service was just created, so the local player may still be null
            // while auth + local-player initialization complete. Wait up to 10s.
            // For the in-car AA path the player is already up, so the await is immediate.
            scope.launch {
                val playerData = withTimeoutOrNull(LOCAL_PLAYER_READY_TIMEOUT_MS) {
                    localPlayer.filterNotNull().first()
                }
                if (playerData == null) {
                    androidAutoLog.w {
                        "Local player did not initialize within 10s — dropping " +
                            "(query=\"$query\"). Open the app once to bootstrap it."
                    }
                    return@launch
                }
                androidAutoLog.i { "Dispatching to AutoLibrary (local player ready)" }
                library.searchAndPlay(query = query.orEmpty(), extras = extras)
            }
        }
    }

    override fun onGetRoot(packageName: String, uID: Int, hints: Bundle?): BrowserRoot {
        androidAutoLog.i { "onGetRoot from package=$packageName uid=$uID" }
        promoteIfRealHost(packageName)
        val extras = Bundle().apply {
            putBoolean(MediaConstants.BROWSER_SERVICE_EXTRAS_KEY_SEARCH_SUPPORTED, true)
            putInt(
                MediaConstants.DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_BROWSABLE,
                MediaConstants.DESCRIPTION_EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM,
            )
            putInt(
                MediaConstants.DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_PLAYABLE,
                MediaConstants.DESCRIPTION_EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM,
            )
        }
        return BrowserRoot(MediaIds.ROOT, extras)
    }

    override fun onLoadChildren(
        parentId: String,
        result: Result<List<MediaBrowserCompat.MediaItem>>,
    ) = library.getItems(parentId, result)

    override fun onSearch(
        query: String,
        extras: Bundle?,
        result: Result<List<MediaBrowserCompat.MediaItem>>,
    ) = library.search(query, result)

    /**
     * Observe session state for errors (reconnecting, disconnected). Only active while a
     * real AA host is bound. Uses SharedMediaSessionManager's error/clear mechanism, which
     * coordinates with playback data (clearErrorState restores cached data).
     */
    private fun observeSessionState() {
        scope.launch {
            var wasAuthenticated = false
            dataSource.apiClient.sessionState.collect { state ->
                when (state) {
                    is SessionState.Connected -> {
                        if (state.dataConnectionState is DataConnectionState.Authenticated) {
                            sharedSession.clearErrorState()
                            if (!wasAuthenticated) {
                                wasAuthenticated = true
                                // Drop stale cached lists from a prior server session before
                                // AA re-pulls. One-shot, not cyclic.
                                library.invalidateCache()
                                notifyChildrenChanged(MediaIds.ROOT)
                                notifyChildrenChanged(MediaIds.TAB_ARTISTS)
                                notifyChildrenChanged(MediaIds.TAB_ALBUMS)
                                notifyChildrenChanged(MediaIds.TAB_PLAYLISTS)
                                notifyChildrenChanged(MediaIds.TAB_PODCASTS)
                                notifyChildrenChanged(MediaIds.TAB_RADIO)
                                notifyChildrenChanged(MediaIds.TAB_AUDIOBOOKS)
                            }
                        }
                    }

                    is SessionState.Reconnecting -> {
                        wasAuthenticated = false
                        sharedSession.setErrorState(
                            PlaybackStateCompat.ERROR_CODE_APP_ERROR,
                            getString(Res.string.media_error_reconnecting),
                        )
                    }

                    is SessionState.Disconnected.Error -> {
                        wasAuthenticated = false
                        sharedSession.setErrorState(
                            PlaybackStateCompat.ERROR_CODE_APP_ERROR,
                            getString(Res.string.media_error_connection_lost),
                        )
                    }

                    is SessionState.Disconnected -> {
                        wasAuthenticated = false
                    }

                    is SessionState.Connecting -> {}
                }
            }
        }
    }

    private fun observeLocalPlayer() {
        scope.launch {
            combine(
                dataSource.apiClient.sessionState,
                localPlayer,
            ) { sessionState, playerData ->
                val isAuthenticated = (sessionState as? SessionState.Connected)
                    ?.dataConnectionState is DataConnectionState.Authenticated
                isAuthenticated to playerData
            }.collect { (isAuthenticated, playerData) ->
                if (isAuthenticated && playerData == null) {
                    delay(2000)
                    // Re-check after debounce
                    if (localPlayer.value == null) {
                        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
                            ?.apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                        val pendingIntent = launchIntent?.let {
                            PendingIntent.getActivity(
                                this@AndroidAutoPlaybackService,
                                0,
                                it,
                                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                            )
                        }
                        sharedSession.setErrorState(
                            PlaybackStateCompat.ERROR_CODE_APP_ERROR,
                            getString(Res.string.media_error_local_player_off),
                            pendingIntent,
                        )
                    }
                } else if (playerData != null) {
                    // Local player appeared (e.g. Sendspin initialized after AA started) —
                    // clear the "not enabled" error so cached playback data is restored.
                    sharedSession.clearErrorState()
                }
            }
        }
    }

    /**
     * Start MainMediaPlaybackService when playback begins.
     * MainActivity's lifecycle-aware observer won't fire while backgrounded,
     * so Android Auto must ensure the notification service is running.
     */
    private fun ensureNotificationService() {
        scope.launch {
            dataSource.isAnythingPlaying.collect { isPlaying ->
                if (isPlaying) {
                    val intent =
                        Intent(this@AndroidAutoPlaybackService, MainMediaPlaybackService::class.java)
                    intent.action = "ACTION_PLAY"
                    try {
                        startForegroundService(intent)
                    } catch (e: Exception) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                            e is ForegroundServiceStartNotAllowedException
                        ) {
                            androidAutoLog.w("Cannot start MainMediaPlaybackService from background")
                        } else {
                            throw e
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        if (promotedToHost) {
            dataSource.apiClient.onExternalConsumerInactive()
            // Returns the session to the all-players view and clears any host-set error,
            // so a transient "Reconnecting..." doesn't outlive the host.
            sharedSession.unbindAutoHost()
        }
        sharedSession.release()
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        // SystemUI renders the media-control notification by binding this browser service;
        // it must not be mistaken for a real Android Auto / media host.
        const val SYSTEMUI_PACKAGE = "com.android.systemui"

        // Cold-start window: voice intent may arrive before auth + local player
        // bootstrap finish. After this many ms we give up and log a warning.
        const val LOCAL_PLAYER_READY_TIMEOUT_MS = 10_000L
    }
}
