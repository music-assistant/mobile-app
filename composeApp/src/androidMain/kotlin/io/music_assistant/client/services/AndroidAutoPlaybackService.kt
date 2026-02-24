package io.music_assistant.client.services

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.MediaSessionCompat.QueueItem
import android.support.v4.media.session.PlaybackStateCompat
import androidx.media.MediaBrowserServiceCompat
import androidx.media.utils.MediaConstants
import coil3.BitmapImage
import coil3.ImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.SuccessResult
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
    private val scope = CoroutineScope(Dispatchers.IO)

    private lateinit var mediaSessionHelper: MediaSessionHelper
    private lateinit var imageLoader: ImageLoader
    private lateinit var defaultIconUri: Uri

    private val dataSource: MainDataSource by inject()
    private val library: AutoLibrary by inject()
    private val currentPlayerData = dataSource.localPlayer
    private val mediaNotificationData = currentPlayerData.filterNotNull()
        .map {
            MediaNotificationData.from(
                (dataSource.apiClient.sessionState.value as? SessionState.Connected)?.serverInfo?.baseUrl,
                it,
                false
            )
        }
        .distinctUntilChanged { old, new -> MediaNotificationData.areTooSimilarToUpdate(old, new) }
        .stateIn(scope, SharingStarted.WhileSubscribed(), null)
        .filterNotNull()

    override fun onCreate() {
        super.onCreate()
        mediaSessionHelper = MediaSessionHelper(
            tag = "AutoMediaSession",
            multiPlayer = false,
            context = this,
            callback = createCallback(),
        )
        sessionToken = mediaSessionHelper.getSessionToken()
        imageLoader = ImageLoader(this)
        defaultIconUri = R.drawable.baseline_library_music_24.toUri(this)
        scope.launch {
            mediaNotificationData.debounce(200).collect { updatePlaybackState(it) }
        }
        scope.launch {
            currentPlayerData.filterNotNull().collect { playerData ->
                // Get queue items from the player's queue data
                when (val queueData = playerData.queue) {
                    is DataState.Data -> {
                        when (val queueItems = queueData.data.items) {
                            is DataState.Data -> {
                                val baseUrl = (dataSource.apiClient.sessionState.value as? SessionState.Connected)?.serverInfo?.baseUrl
                                mediaSessionHelper.updateQueue(queueItems.data.map { queueTrack ->
                                    QueueItem(
                                        (queueTrack.track as AppMediaItem).toMediaDescription(baseUrl, defaultIconUri),
                                        queueTrack.track.longId
                                    )
                                })
                            }
                            else -> mediaSessionHelper.updateQueue(emptyList())
                        }
                    }
                    else -> mediaSessionHelper.updateQueue(emptyList())
                }
            }
        }
        observeSessionState()
        observeLocalPlayer()
    }

    private fun createCallback(): MediaSessionCompat.Callback =
        object : MediaSessionCompat.Callback() {
            override fun onPlay() {
                currentPlayerData.value?.let {
                    dataSource.playerAction(it, PlayerAction.TogglePlayPause)
                }
            }

            override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
                currentPlayerData.value?.let { playerData ->
                    mediaId?.let {
                        library.play(
                            it,
                            extras,
                            playerData.queueInfo?.id ?: playerData.player.id
                        )
                    }
                }
            }

            override fun onPause() {
                currentPlayerData.value?.let {
                    dataSource.playerAction(it, PlayerAction.TogglePlayPause)
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
                    // Get queue items from player's queue data
                    when (val queueData = playerData.queue) {
                        is DataState.Data -> {
                            when (val queueItems = queueData.data.items) {
                                is DataState.Data -> {
                                    queueItems.data.find { it.track.longId == id }?.id?.let { queueItemId ->
                                        dataSource.queueAction(
                                            QueueAction.PlayQueueItem(
                                                playerData.queueInfo?.id ?: playerData.player.id,
                                                queueItemId
                                            )
                                        )
                                    }
                                }
                                else -> {} // No queue items available
                            }
                        }
                        else -> {} // No queue data available
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
                                PlayerAction.ToggleShuffle(current = it.shuffleEnabled)
                            )
                        }
                    }

                    "ACTION_TOGGLE_REPEAT" -> currentPlayerData.value?.let { playerData ->
                        playerData.queueInfo?.repeatMode?.let { repeatMode ->
                            dataSource.playerAction(
                                playerData,
                                PlayerAction.ToggleRepeatMode(current = repeatMode)
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
                MediaConstants.DESCRIPTION_EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM
            )
            putInt(
                MediaConstants.DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_PLAYABLE,
                MediaConstants.DESCRIPTION_EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM
            )
        }
        return BrowserRoot(MediaIds.ROOT, extras)
    }

    override fun onLoadChildren(
        parentId: String,
        result: Result<List<MediaBrowserCompat.MediaItem>>
    ) = library.getItems(parentId, result)


    override fun onSearch(
        query: String,
        extras: Bundle?,
        result: Result<List<MediaBrowserCompat.MediaItem>>
    ) = library.search(query, result)

    private fun observeSessionState() {
        scope.launch {
            var wasAuthenticated = false
            dataSource.apiClient.sessionState.collect { state ->
                when (state) {
                    is SessionState.Connected -> {
                        if (state.dataConnectionState is DataConnectionState.Authenticated) {
                            mediaSessionHelper.clearErrorState()
                            if (!wasAuthenticated) {
                                wasAuthenticated = true
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
                        mediaSessionHelper.setErrorState(
                            PlaybackStateCompat.ERROR_CODE_APP_ERROR,
                            "Reconnecting..."
                        )
                    }
                    is SessionState.Disconnected.Error -> {
                        wasAuthenticated = false
                        mediaSessionHelper.setErrorState(
                            PlaybackStateCompat.ERROR_CODE_APP_ERROR,
                            "Connection lost"
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
                currentPlayerData
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
                                this@AndroidAutoPlaybackService, 0, it,
                                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                            )
                        }
                        mediaSessionHelper.setErrorState(
                            PlaybackStateCompat.ERROR_CODE_APP_ERROR,
                            "Local player is not enabled",
                            pendingIntent
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        mediaSessionHelper.release()
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun updatePlaybackState(data: MediaNotificationData) {
        val bitmap =
            data.imageUrl?.let {
                ((imageLoader.execute(
                    ImageRequest.Builder(this@AndroidAutoPlaybackService)
                        .data(it)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .memoryCacheKey(it)
                        .build()
                ) as? SuccessResult)?.image as? BitmapImage)?.bitmap
            }
        mediaSessionHelper.updatePlaybackState(data, bitmap)
    }
}
