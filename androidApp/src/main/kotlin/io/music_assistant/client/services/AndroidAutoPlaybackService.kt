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
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.MediaSessionCompat.QueueItem
import android.support.v4.media.session.PlaybackStateCompat
import androidx.media.MediaBrowserServiceCompat
import androidx.media.utils.MediaConstants
import co.touchlab.kermit.Logger
import coil3.BitmapImage
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import io.music_assistant.client.R
import io.music_assistant.client.auto.AutoLibrary
import io.music_assistant.client.auto.MediaIds
import io.music_assistant.client.auto.toMediaDescription
import io.music_assistant.client.auto.toUri
import io.music_assistant.client.data.MainDataSource
import io.music_assistant.client.data.model.client.AppMediaItem
import io.music_assistant.client.ui.compose.common.DataState
import io.music_assistant.client.ui.compose.common.action.PlayerAction
import io.music_assistant.client.ui.compose.common.action.QueueAction
import io.music_assistant.client.utils.DataConnectionState
import io.music_assistant.client.utils.SessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

@OptIn(FlowPreview::class)
class AndroidAutoPlaybackService : MediaBrowserServiceCompat() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val imageLoader: ImageLoader by lazy { SingletonImageLoader.get(this) }
    private lateinit var defaultIconUri: Uri

    private val dataSource: MainDataSource by inject()
    private val library: AutoLibrary by inject()
    private val sharedSession: SharedMediaSessionManager by inject()
    private val currentPlayerData = dataSource.localPlayer
    private val mediaNotificationData = currentPlayerData.filterNotNull()
        .map {
            MediaNotificationData.from(
                playerData = it,
                multiplePlayers = false,
                effectiveElapsedSec = it.queueInfo?.id?.let { id ->
                    dataSource.positionTracker.effectiveSec(id)
                },
            )
        }
        .distinctUntilChanged { old, new -> MediaNotificationData.areTooSimilarToUpdate(old, new) }
        .stateIn(scope, SharingStarted.WhileSubscribed(), null)
        .filterNotNull()

    override fun onCreate() {
        super.onCreate()
        val token = sharedSession.acquire(
            callback = createCallback(),
            isAutoService = true,
        )
        sessionToken = token
        defaultIconUri = R.drawable.baseline_library_music_24.toUri(this)

        // Playback data collector — the primary writer for playback state.
        // SharedMediaSessionManager coordinates with error state: if an error is set,
        // updatePlaybackState will still cache the data but show the error to the user.
        // When clearErrorState is called, it restores the cached data automatically.
        scope.launch {
            mediaNotificationData.debounce(200).collect { data ->
                val bitmap = loadBitmap(data)
                sharedSession.updatePlaybackState(data, bitmap, multiPlayer = false)
            }
        }

        // Queue updates (separate MediaSession property — no conflict with playback state).
        // Project to a stable id list and dedup: setQueue is an expensive IPC write that AA
        // hosts react to (re-rendering, which can re-subscribe browse parents). Without
        // dedup, every `_localPlayerData` emission — volume changes, optimistic bumps,
        // anything — churns the AA UI even when the queue itself is unchanged.
        scope.launch {
            currentPlayerData
                .map { playerData ->
                    val items = (playerData?.queue as? DataState.Data)
                        ?.data?.items?.let { it as? DataState.Data }?.data
                        .orEmpty()
                    items
                }
                .distinctUntilChanged { old, new ->
                    old.size == new.size &&
                        old.zip(new).all { (a, b) -> a.track.longId == b.track.longId }
                }
                .collect { items ->
                    val baseUrl = dataSource.apiClient.serverBaseUrl.value
                    sharedSession.updateQueue(
                        items.map { queueTrack ->
                            QueueItem(
                                (queueTrack.track as AppMediaItem).toMediaDescription(
                                    baseUrl,
                                    defaultIconUri,
                                ),
                                queueTrack.track.longId,
                            )
                        },
                    )
                }
        }

        dataSource.apiClient.onExternalConsumerActive()
        observeSessionState()
        observeLocalPlayer()
        ensureNotificationService()
    }

    private fun createCallback(): MediaSessionCompat.Callback =
        object : MediaSessionCompat.Callback() {
            override fun onPlay() {
                currentPlayerData.value?.let {
                    dataSource.playerAction(it, PlayerAction.Play)
                }
            }

            override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
                currentPlayerData.value?.let { playerData ->
                    mediaId?.let {
                        library.play(
                            it,
                            extras,
                            playerData.queueInfo?.id ?: playerData.player.id,
                        )
                    }
                }
            }

            override fun onPlayFromSearch(query: String?, extras: Bundle?) {
                currentPlayerData.value?.let { playerData ->
                    query?.takeIf { it.isNotBlank() }?.let { q ->
                        library.searchAndPlay(q, playerData.queueInfo?.id ?: playerData.player.id)
                    }
                }
            }

            override fun onPause() {
                currentPlayerData.value?.let {
                    dataSource.playerAction(it, PlayerAction.Pause)
                }
            }

            override fun onSkipToNext() {
                currentPlayerData.value?.let { dataSource.playerAction(it, PlayerAction.Next) }
            }

            override fun onSkipToPrevious() {
                currentPlayerData.value?.let {
                    dataSource.playerAction(it, PlayerAction.Previous)
                }
            }

            override fun onSkipToQueueItem(id: Long) {
                currentPlayerData.value?.let { playerData ->
                    when (val queueData = playerData.queue) {
                        is DataState.Data -> {
                            when (val queueItems = queueData.data.items) {
                                is DataState.Data -> {
                                    queueItems.data.find { it.track.longId == id }?.id?.let { queueItemId ->
                                        dataSource.queueAction(
                                            QueueAction.PlayQueueItem(
                                                playerData.queueInfo?.id
                                                    ?: playerData.player.id,
                                                queueItemId,
                                            ),
                                        )
                                    }
                                }

                                else -> {}
                            }
                        }

                        else -> {}
                    }
                }
            }

            override fun onSeekTo(pos: Long) {
                currentPlayerData.value?.let {
                    dataSource.playerAction(it, PlayerAction.SeekTo(pos / 1000))
                }
            }

            override fun onCustomAction(action: String, extras: Bundle?) {
                when (action) {
                    "ACTION_TOGGLE_SHUFFLE" -> currentPlayerData.value?.let { playerData ->
                        playerData.queueInfo?.let {
                            dataSource.playerAction(
                                playerData,
                                PlayerAction.ToggleShuffle(current = it.shuffleEnabled),
                            )
                        }
                    }

                    "ACTION_TOGGLE_REPEAT" -> currentPlayerData.value?.let { playerData ->
                        playerData.queueInfo?.repeatMode?.let { repeatMode ->
                            dataSource.playerAction(
                                playerData,
                                PlayerAction.ToggleRepeatMode(current = repeatMode),
                            )
                        }
                    }
                }
            }
        }

    override fun onGetRoot(packageName: String, uID: Int, hints: Bundle?): BrowserRoot {
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
     * Observe session state for errors (reconnecting, disconnected).
     * Uses SharedMediaSessionManager's error/clear mechanism which properly
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
                            "Reconnecting...",
                        )
                    }

                    is SessionState.Disconnected.Error -> {
                        wasAuthenticated = false
                        sharedSession.setErrorState(
                            PlaybackStateCompat.ERROR_CODE_APP_ERROR,
                            "Connection lost",
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
                currentPlayerData,
            ) { sessionState, playerData ->
                val isAuthenticated = (sessionState as? SessionState.Connected)
                    ?.dataConnectionState is DataConnectionState.Authenticated
                isAuthenticated to playerData
            }.collect { (isAuthenticated, playerData) ->
                if (isAuthenticated && playerData == null) {
                    delay(2000)
                    // Re-check after debounce
                    if (currentPlayerData.value == null) {
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
                            "Local player is not enabled",
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
                            Logger.withTag("AndroidAutoPlaybackService")
                                .w("Cannot start MainMediaPlaybackService from background")
                        } else {
                            throw e
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        dataSource.apiClient.onExternalConsumerInactive()
        sharedSession.release(isAutoService = true)
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun loadBitmap(data: MediaNotificationData): android.graphics.Bitmap? =
        data.imageUrl?.let {
            (
                (
                    imageLoader.execute(
                ImageRequest.Builder(this@AndroidAutoPlaybackService)
                    .data(it)
                    .allowHardware(false)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .memoryCacheKey(it)
                    .build(),
            ) as? SuccessResult
                )?.image as? BitmapImage
            )?.bitmap
        }
}
