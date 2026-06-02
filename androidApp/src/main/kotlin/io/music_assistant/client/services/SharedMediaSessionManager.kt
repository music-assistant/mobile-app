// debounce() is FlowPreview; the debounce window is documented at use site.
@file:Suppress("MagicNumber")

package io.music_assistant.client.services

import android.app.PendingIntent
import android.content.Context
import android.graphics.Bitmap
import android.media.AudioManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.media.utils.MediaConstants
import co.touchlab.kermit.Logger
import coil3.ImageLoader
import coil3.SingletonImageLoader
import io.music_assistant.client.R
import io.music_assistant.client.auto.toMediaDescription
import io.music_assistant.client.auto.toUri
import io.music_assistant.client.data.MainDataSource
import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.data.model.client.RepeatMode
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.ui.compose.common.action.PlayerAction
import io.music_assistant.client.ui.compose.common.action.QueueAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Single source of truth for the app's MediaSession **and** its sole writer.
 *
 * Both AndroidAutoPlaybackService and MainMediaPlaybackService share this instance
 * via Koin singleton, ensuring Android sees exactly one active session. Neither service
 * writes playback state itself — this manager owns one writer coroutine fed by
 * [MainDataSource.nowPlayingPlayer] (the canonical "what's playing across all players"),
 * so the session can no longer be clobbered by whichever service the OS happens to bind.
 *
 * Reference-counted: first [acquire] creates the session + writer, last [release] tears
 * them down.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SharedMediaSessionManager(
    private val applicationContext: Context,
    private val dataSource: MainDataSource,
) {
    private var mediaSession: MediaSessionCompat? = null
    private var writerScope: CoroutineScope? = null
    private var refCount = 0

    private val imageLoader: ImageLoader by lazy { SingletonImageLoader.get(applicationContext) }
    private val defaultIconUri: Uri by lazy {
        R.drawable.baseline_library_music_24.toUri(applicationContext)
    }

    // Current artwork bitmap for the canonical now-playing track. The notification
    // service observes this to refresh its largeIcon; title/artist/position are read
    // live from the session via MediaStyle, so they need no notification repost.
    private val _artwork = MutableStateFlow<Bitmap?>(null)
    val artwork: StateFlow<Bitmap?> = _artwork.asStateFlow()

    // Browse/voice "play" requests are AA-specific (need AutoLibrary). A real AA host
    // registers a handler; transient SystemUI binds never do.
    interface AutoPlayHandler {
        fun onPlayFromMediaId(mediaId: String?, extras: Bundle?)
        fun onPlayFromSearch(query: String?, extras: Bundle?)
    }

    private var autoPlayHandler: AutoPlayHandler? = null

    // True while a real Android Auto / media host is bound. AA is deliberately isolated to
    // the LOCAL player: when a host is connected the session presents/controls only the
    // local player; otherwise it presents the canonical all-players now-playing (the phone
    // notification, with its switch-player action). SystemUI binds never flip this.
    private val autoHostActive = MutableStateFlow(false)

    // Cached last playback data — used to restore state after clearing errors.
    private var lastData: MediaNotificationData? = null
    private var lastBitmap: Bitmap? = null
    private var lastMultiPlayer: Boolean = false

    // Current error state (non-null = error takes precedence over playback). Only a real
    // AA host sets this (see AndroidAutoPlaybackService); cleared when that host goes away.
    private var currentError: ErrorState? = null

    private val logger = Logger.withTag("SharedSession")

    data class ErrorState(
        val code: Int,
        val message: String,
        val resolution: PendingIntent? = null,
    )

    @Synchronized
    fun acquire(): MediaSessionCompat.Token {
        val session = ensureSession()
        refCount++
        logger.i { "acquire — refCount=$refCount" }
        return session.sessionToken
    }

    @Synchronized
    fun release() {
        refCount--
        logger.i { "release — refCount=$refCount" }
        if (refCount <= 0) {
            writerScope?.cancel()
            writerScope = null
            mediaSession?.release()
            mediaSession = null
            _artwork.value = null
            refCount = 0
            currentError = null
            lastData = null
            lastBitmap = null
            autoPlayHandler = null
        }
    }

    /** A real AA host connected: isolate the session to the local player + accept browse/voice play. */
    fun bindAutoHost(handler: AutoPlayHandler) {
        autoPlayHandler = handler
        autoHostActive.value = true
    }

    /** The AA host went away: return to the all-players notification view, drop any host error. */
    fun unbindAutoHost() {
        autoPlayHandler = null
        autoHostActive.value = false
        clearErrorState()
    }

    /** The player the session currently targets: local-only under an AA host, else canonical. */
    private fun currentPlayer() =
        if (autoHostActive.value) dataSource.localPlayer.value else dataSource.nowPlayingPlayer.value

    private fun ensureSession(): MediaSessionCompat {
        mediaSession?.let { return it }
        val session = MediaSessionCompat(applicationContext, "MusicAssistantSession").apply {
            setFlags(MediaSessionCompat.FLAG_HANDLES_QUEUE_COMMANDS)
            setPlaybackToLocal(AudioManager.STREAM_MUSIC)
            setCallback(createCallback())
            isActive = true
        }
        mediaSession = session
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO).also { writerScope = it }
        startWriter(scope)
        return session
    }

    private fun startWriter(scope: CoroutineScope) {
        // Playback state + metadata. 200ms debounce coalesces rapid updates; bitmap
        // loading runs async via [withAsyncBitmap] so slow artwork never blocks writes.
        scope.launch {
            nowPlayingDataFlow()
                .withAsyncBitmap(scope) { loadCoilBitmap(applicationContext, imageLoader, it) }
                .debounce(timeoutMillis = 200)
                .collect { (data, bitmap) ->
                    _artwork.value = bitmap
                    // multiPlayer only gates the "(on <player>)" artist suffix, and
                    // playerName is already null for the local player — so always pass
                    // true to keep the remote-player suffix even for a single player.
                    updatePlaybackState(data, bitmap, multiPlayer = true)
                }
        }
        // Queue (separate session property). Dedup on the stable id list: setQueue is an
        // expensive IPC write AA hosts react to, so unrelated emissions (volume, etc.)
        // must not churn it.
        scope.launch {
            sourcePlayerData()
                .map { (player, _) -> player.queueItems.orEmpty() }
                .distinctUntilChanged { old, new ->
                    old.size == new.size &&
                        old.zip(new).all { (a, b) -> a.track.longId == b.track.longId }
                }
                .collect { items ->
                    updateQueue(
                        items.map { queueTrack ->
                            MediaSessionCompat.QueueItem(
                                (queueTrack.track as AppMediaItem).toMediaDescription(defaultIconUri),
                                queueTrack.track.longId,
                            )
                        },
                    )
                }
        }
    }

    // Session source, resolved atomically per mode so a toggle can never pair a stale
    // player with the new mode: under an AA host it's the local player only (deliberate
    // isolation, no switch action), otherwise the canonical all-players now-playing with
    // its switch-player flag. flatMapLatest tears down the old source on toggle, and the
    // new source emits one fully-consistent (player, multiplePlayers) frame.
    private fun sourcePlayerData(): Flow<Pair<PlayerData, Boolean>> =
        autoHostActive.flatMapLatest { hostActive ->
            if (hostActive) {
                dataSource.localPlayer.filterNotNull().map { it to false }
            } else {
                combine(
                    dataSource.nowPlayingPlayer.filterNotNull(),
                    dataSource.sessionMultiplePlayers,
                ) { player, multiplePlayers -> player to multiplePlayers }
            }
        }

    private fun nowPlayingDataFlow(): Flow<MediaNotificationData> =
        sourcePlayerData()
            .map { (player, multiplePlayers) ->
                MediaNotificationData.from(
                    playerData = player,
                    multiplePlayers = multiplePlayers,
                    effectiveElapsedSec = player.queueInfo?.id?.let {
                        dataSource.positionTracker.effectiveSec(it)
                    },
                )
            }
            .distinctUntilChanged { old, new -> MediaNotificationData.areTooSimilarToUpdate(old, new) }

    private fun createCallback(): MediaSessionCompat.Callback =
        object : MediaSessionCompat.Callback() {
            override fun onPlay() = act(PlayerAction.Play)
            override fun onPause() = act(PlayerAction.Pause)
            override fun onSkipToNext() = act(PlayerAction.Next)
            override fun onSkipToPrevious() = act(PlayerAction.Previous)
            override fun onSeekTo(pos: Long) = act(PlayerAction.SeekTo(pos / 1000))

            override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
                autoPlayHandler?.onPlayFromMediaId(mediaId, extras)
            }

            override fun onPlayFromSearch(query: String?, extras: Bundle?) {
                autoPlayHandler?.onPlayFromSearch(query, extras)
            }

            override fun onSkipToQueueItem(id: Long) {
                val playerData = currentPlayer() ?: return
                val queueItemId = playerData.queueItems
                    ?.find { it.track.longId == id }?.id ?: return
                dataSource.queueAction(
                    QueueAction.PlayQueueItem(
                        playerData.queueInfo?.id ?: playerData.player.id,
                        queueItemId,
                    ),
                )
            }

            override fun onCustomAction(action: String, extras: Bundle?) {
                when (action) {
                    "ACTION_SWITCH_PLAYER" -> dataSource.switchSessionPlayer()
                    "ACTION_TOGGLE_SHUFFLE" -> currentPlayer()?.let { pd ->
                        pd.queueInfo?.let {
                            dataSource.playerAction(
                                pd,
                                PlayerAction.ToggleShuffle(current = it.shuffleEnabled),
                            )
                        }
                    }

                    "ACTION_TOGGLE_REPEAT" -> currentPlayer()?.let { pd ->
                        pd.queueInfo?.repeatMode?.let { repeatMode ->
                            dataSource.playerAction(
                                pd,
                                PlayerAction.ToggleRepeatMode(current = repeatMode),
                            )
                        }
                    }
                }
            }
        }

    private fun act(action: PlayerAction) {
        currentPlayer()?.let { dataSource.playerAction(it, action) }
    }

    /**
     * Set an error state. The error takes precedence: the writer will show the error
     * until [clearErrorState] is called. Only a real AA host uses this.
     */
    @Synchronized
    fun setErrorState(code: Int, message: String, resolution: PendingIntent? = null) {
        currentError = ErrorState(code, message, resolution).also {
            writeErrorToSession(it)
        }
    }

    /**
     * Clear the error state and immediately restore the last known playback data, so the
     * session isn't stuck at STATE_ERROR after a transient AA-host error/reconnect.
     */
    @Synchronized
    fun clearErrorState() {
        currentError = null
        lastData?.let { writePlaybackToSession(it, lastBitmap, lastMultiPlayer) }
    }

    @Synchronized
    private fun updatePlaybackState(
        data: MediaNotificationData,
        bitmap: Bitmap?,
        multiPlayer: Boolean,
    ) {
        lastData = data
        lastBitmap = bitmap
        lastMultiPlayer = multiPlayer
        currentError?.let {
            writeErrorToSession(it)
        } ?: run {
            writePlaybackToSession(data, bitmap, multiPlayer)
        }
    }

    @Synchronized
    private fun updateQueue(queue: List<MediaSessionCompat.QueueItem>) {
        mediaSession?.setQueue(queue)
        mediaSession?.setQueueTitle("Now playing")
    }

    // --- Private writers ---

    private fun writePlaybackToSession(
        data: MediaNotificationData,
        bitmap: Bitmap?,
        multiPlayer: Boolean = false,
    ) {
        val session = mediaSession ?: return
        val state = if (data.isPlaying) {
            PlaybackStateCompat.STATE_PLAYING
        } else {
            PlaybackStateCompat.STATE_PAUSED
        }
        val playbackState = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_SEEK_TO or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS,
            )
            .setState(
                state,
                data.elapsedTime ?: PlaybackState.PLAYBACK_POSITION_UNKNOWN,
                if (data.isPlaying) 1f else 0f,
                data.elapsedUpdateTimeMs ?: SystemClock.elapsedRealtime(),
            )
            .setActiveQueueItemId(
                data.longItemId ?: MediaSessionCompat.QueueItem.UNKNOWN_ID.toLong(),
            )
            .also { builder ->
                data.shuffleEnabled?.let { shuffle ->
                    builder.addCustomAction(
                        PlaybackStateCompat.CustomAction.Builder(
                            "ACTION_TOGGLE_SHUFFLE",
                            "Shuffle",
                            getShuffleModeIcon(shuffle),
                        ).build(),
                    )
                }
                if (data.multiplePlayers) {
                    builder.addCustomAction(
                        PlaybackStateCompat.CustomAction.Builder(
                            "ACTION_SWITCH_PLAYER",
                            "Next player",
                            R.drawable.ic_speaker,
                        ).build(),
                    )
                } else {
                    data.repeatMode?.let { repeatMode ->
                        builder.addCustomAction(
                            PlaybackStateCompat.CustomAction.Builder(
                                "ACTION_TOGGLE_REPEAT",
                                "Repeat",
                                getRepeatModeIcon(repeatMode),
                            ).build(),
                        )
                    }
                }
            }
            .build()
        session.setPlaybackState(playbackState)

        val metadata = MediaMetadataCompat.Builder()
            .putString(
                MediaMetadataCompat.METADATA_KEY_TITLE,
                data.name ?: "Unknown Track",
            )
            .putString(
                MediaMetadataCompat.METADATA_KEY_ARTIST,
                (data.artist ?: "Unknown Artist") +
                        (if (multiPlayer) data.playerName?.let { " (on $it)" } ?: "" else ""),
            )
            .putString(
                MediaMetadataCompat.METADATA_KEY_ALBUM,
                data.album,
            )
            .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, bitmap)
            .also { builder ->
                data.duration?.let {
                    builder.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, it)
                }
            }
            .build()
        session.setMetadata(metadata)
    }

    private fun writeErrorToSession(error: ErrorState) {
        val session = mediaSession ?: return
        val extras = error.resolution?.let { intent ->
            Bundle().apply {
                putParcelable(
                    MediaConstants.PLAYBACK_STATE_EXTRAS_KEY_ERROR_RESOLUTION_ACTION_INTENT,
                    intent,
                )
                putString(
                    MediaConstants.PLAYBACK_STATE_EXTRAS_KEY_ERROR_RESOLUTION_ACTION_LABEL,
                    "Open app",
                )
            }
        }
        val playbackState = PlaybackStateCompat.Builder()
            .setState(PlaybackStateCompat.STATE_ERROR, 0, 0f)
            .setErrorMessage(error.code, error.message)
            .also { builder -> extras?.let { builder.setExtras(it) } }
            .build()
        session.setPlaybackState(playbackState)
    }

    private fun getRepeatModeIcon(repeatMode: RepeatMode): Int = when (repeatMode) {
        RepeatMode.ALL -> R.drawable.baseline_repeat_24
        RepeatMode.ONE -> R.drawable.baseline_repeat_one_24
        RepeatMode.OFF -> R.drawable.baseline_no_repeat_24
    }

    private fun getShuffleModeIcon(shuffleMode: Boolean): Int =
        if (shuffleMode) {
            R.drawable.baseline_shuffle_24
        } else {
            R.drawable.baseline_arrow_right_alt_24
        }
}
