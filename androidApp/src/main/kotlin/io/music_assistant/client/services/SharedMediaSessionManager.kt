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
import io.music_assistant.client.data.CarConnectionMonitor
import io.music_assistant.client.data.MainDataSource
import io.music_assistant.client.data.model.client.MediaType
import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.data.model.client.RepeatMode
import io.music_assistant.client.data.model.client.ResolvedChapter
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.LongFormSeekDefaults
import io.music_assistant.client.data.model.client.items.canBeFavorited
import io.music_assistant.client.data.model.client.presentationChapter
import io.music_assistant.client.data.model.client.toAbsoluteSeekSeconds
import io.music_assistant.client.data.withPresentationChapter
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Transport actions advertised on EVERY PlaybackState this manager publishes.
 *
 * Voice hosts (Android Auto search, Assistant/Gemini, Wear, AVRCP) read this bitmask to decide
 * whether the app can be driven at all, so a state written without it makes the app look
 * incapable at exactly the moment the user speaks. That includes the idle and error states —
 * "play X" and a voice retry both arrive when nothing is playing.
 *
 * Every bit here MUST have a matching override in [SharedMediaSessionManager.createCallback].
 * Deliberately absent: ACTION_STOP, ACTION_SET_SHUFFLE_MODE and ACTION_SET_REPEAT_MODE have no
 * override (shuffle and repeat are custom actions here), and advertising them gives hosts dead
 * buttons. PREPARE_FROM_* are absent because there is no prepare pipeline — see the "no
 * onPrepareFromSearch" note in docs/ANDROID-AUTO.md.
 */
private val SESSION_TRANSPORT_ACTIONS: Long =
    PlaybackStateCompat.ACTION_PLAY or
        PlaybackStateCompat.ACTION_PAUSE or
        PlaybackStateCompat.ACTION_PLAY_PAUSE or
        PlaybackStateCompat.ACTION_SEEK_TO or
        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
        PlaybackStateCompat.ACTION_SKIP_TO_QUEUE_ITEM or
        PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID or
        PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH

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
    private val carConnection: CarConnectionMonitor,
) {
    private var mediaSession: MediaSessionCompat? = null
    private var writerScope: CoroutineScope? = null
    private var refCount = 0

    // Outlives the ref-counted [writerScope]: the AA-connected and blocked signals must be
    // readable before the first [acquire] and after the last [release]. This manager is a
    // Koin singleton, so the scope is never cancelled.
    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Localized labels, resolved once before the writer collectors start (see
    // [startWriter]). The synchronous writers read these; null only in the brief
    // window before load completes, which the collector ordering rules out.
    @Volatile
    private var strings: MediaSessionStrings? = null

    private val imageLoader: ImageLoader by lazy { SingletonImageLoader.get(applicationContext) }
    private val defaultIconUri: Uri by lazy {
        R.drawable.baseline_library_music_24.toUri(applicationContext)
    }

    // What the foreground notification needs to repost: the artwork bitmap plus a
    // per-track key. The service reposts on every emission, and a repost is what
    // refreshes the notification's title/artist — so the trigger must change on track
    // change, not just on artwork change. Keying by [trackKey] makes a bare same-bitmap
    // StateFlow stop conflating consecutive tracks that share artwork (which otherwise
    // froze the title/artist on the previous track).
    data class NotificationArt(val trackKey: Long?, val bitmap: Bitmap?)

    private val _notificationArt = MutableStateFlow<NotificationArt?>(null)
    val notificationArt: StateFlow<NotificationArt?> = _notificationArt.asStateFlow()

    // Browse/voice "play" requests are AA-specific (need AutoLibrary). A real AA host
    // registers a handler; transient SystemUI binds never do.
    interface AutoPlayHandler {
        fun onPlayFromMediaId(mediaId: String?, extras: Bundle?)
        fun onPlayFromSearch(query: String?, extras: Bundle?)
    }

    private var autoPlayHandler: AutoPlayHandler? = null

    // True while a real Android Auto / media host is bound. SystemUI binds never flip this.
    private val _hostBound = MutableStateFlow(false)

    /**
     * True while the session must be isolated to the LOCAL player: the car presents and
     * controls only the local player; otherwise the session presents the canonical
     * all-players now-playing (the phone notification, with its switch-player action).
     *
     * Two independent signals, OR-ed. [CarConnectionMonitor] is the dependable Android Auto
     * projection edge and works even when no host bound the browser service; [_hostBound]
     * additionally covers non-projection media hosts (Assistant, Wear).
     */
    private val _autoHostActive = MutableStateFlow(false)
    val autoHostActive: StateFlow<Boolean> = _autoHostActive.asStateFlow()

    init {
        managerScope.launch { carConnection.connected.collect { recomputeAutoHost() } }
    }

    // Recomputed rather than derived with combine(): bind/unbind must take effect before
    // the call returns, because the session callbacks read [autoHostActive] synchronously.
    private fun recomputeAutoHost() {
        _autoHostActive.value = _hostBound.value || carConnection.connected.value
    }

    /**
     * True while the car is connected but there is no local player at all (the user disabled
     * it, or it has not come up). The session must then present nothing: it is deactivated,
     * metadata and queue are cleared, and the phone notification service stops. Without this
     * the last remote-player state stays on the session and the car shows and controls a
     * remote player.
     *
     * Keyed on the absence of the local player, not on the setting, so the not-yet-connected
     * case is covered too. The debounce rides out Sendspin bootstrap at car-connect time.
     */
    val sessionBlocked: StateFlow<Boolean> =
        combine(autoHostActive, dataSource.localPlayer) { auto, player -> auto && player == null }
            .debounce(SESSION_BLOCK_DEBOUNCE_MS)
            .stateIn(managerScope, SharingStarted.Eagerly, false)

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
            _notificationArt.value = null
            refCount = 0
            currentError = null
            lastData = null
            lastBitmap = null
            autoPlayHandler = null
            strings = null
        }
    }

    /**
     * A media host connected: accept browse/voice play from it.
     *
     * [isProjectionHost] separates two facts that used to be conflated. Every host needs the
     * handler registered — that is just "where does a play request go". Only a projection host
     * (the car) may additionally isolate the session to the local player, because that isolation
     * deactivates the session when no local player exists. Passing true for a generic media
     * binder (Assistant, Gemini, Wear, or our own VoicePlayDispatchActivity) blanks the phone
     * notification for a remote player.
     */
    fun bindAutoHost(handler: AutoPlayHandler, isProjectionHost: Boolean) {
        autoPlayHandler = handler
        _hostBound.value = isProjectionHost
        recomputeAutoHost()
    }

    /** The AA host went away: return to the all-players notification view, drop any host error. */
    fun unbindAutoHost() {
        autoPlayHandler = null
        _hostBound.value = false
        recomputeAutoHost()
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
            // Publish the action mask before activating: the playback writer only runs once a
            // player emits, so without this a cold process would present an active session that
            // advertises nothing — and "play X on Music Assistant" arrives exactly then.
            setPlaybackState(idlePlaybackState())
            isActive = true
        }
        mediaSession = session
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO).also { writerScope = it }
        startWriter(scope)
        return session
    }

    private fun startWriter(scope: CoroutineScope) {
        // Resolve localized labels once before any collector runs, so the synchronous
        // writers below always see a non-null [strings].
        scope.launch {
            strings = MediaSessionStrings.load()
            launchPlaybackWriter(scope)
            launchQueueWriter(scope)
            launchBlockWriter(scope)
        }
    }

    private fun launchPlaybackWriter(scope: CoroutineScope) {
        // Playback state + metadata. 200ms debounce coalesces rapid updates; bitmap
        // loading runs async via [withAsyncBitmap] (keyed by imageUrl, so position ticks
        // never restart or starve a slow load). The notification trigger is keyed by track
        // id, not just the bitmap: a repost is what refreshes the notification's title, so
        // it must fire on track change even when consecutive tracks share artwork.
        scope.launch {
            nowPlayingDataFlow()
                .withAsyncBitmap(scope) { loadCoilBitmap(applicationContext, imageLoader, it) }
                .debounce(timeoutMillis = 200)
                .collect { (data, bitmap) ->
                    _notificationArt.value = NotificationArt(data.longItemId, bitmap)
                    // multiPlayer only gates the "(on <player>)" artist suffix, and
                    // playerName is already null for the local player — so always pass
                    // true to keep the remote-player suffix even for a single player.
                    updatePlaybackState(data, bitmap, multiPlayer = true)
                }
        }
    }

    private fun launchQueueWriter(scope: CoroutineScope) {
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

    private fun launchBlockWriter(scope: CoroutineScope) {
        // Applies / lifts the [sessionBlocked] presentation. The playback and queue writers
        // are guarded on the same flag, so a debounced emission that lands after the block
        // cannot re-publish stale remote-player data.
        scope.launch {
            sessionBlocked.collect { blocked ->
                if (blocked) writeBlockToSession() else liftBlockFromSession()
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
            .withPresentationChapter(
                preferences = dataSource.userPreferences,
                positionTracker = dataSource.positionTracker,
                playerOf = { (player, _) -> player },
            )
            .map { (source, chapter, elapsedSec) ->
                val (player, multiplePlayers) = source
                MediaNotificationData.from(
                    playerData = player,
                    multiplePlayers = multiplePlayers,
                    effectiveElapsedSec = elapsedSec,
                    currentChapter = chapter,
                )
            }
            .distinctUntilChanged { old, new -> MediaNotificationData.areTooSimilarToUpdate(old, new) }

    /** Pref-gated chapter for chapter-relative session presentation. */
    private fun sessionChapter(player: PlayerData, elapsedSec: Double?): ResolvedChapter? =
        player.presentationChapter(elapsedSec)
            .takeIf { dataSource.userPreferences.isChapterProgressEnabled }

    private fun createCallback(): MediaSessionCompat.Callback =
        object : MediaSessionCompat.Callback() {
            override fun onPlay() = act(PlayerAction.Play)
            override fun onPause() = act(PlayerAction.Pause)
            override fun onSkipToNext() = act(PlayerAction.Next)
            override fun onSkipToPrevious() = act(PlayerAction.Previous)

            override fun onSeekTo(pos: Long) {
                // Host scrubbers return chapter-relative targets; remap them to absolute seconds.
                val targetSec = pos / 1000
                val player = currentPlayer()
                val elapsedSec = player?.queueInfo?.id?.let {
                    dataSource.positionTracker.effectiveSec(it)
                }
                val chapter = player?.let { sessionChapter(it, elapsedSec) }
                act(PlayerAction.SeekTo(chapter.toAbsoluteSeekSeconds(targetSec.toDouble())))
            }

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
                    "ACTION_SEEK_BACK" -> act(PlayerAction.SeekBy(-LongFormSeekDefaults.BACK_SECONDS))
                    "ACTION_SEEK_FORWARD" -> act(PlayerAction.SeekBy(LongFormSeekDefaults.FORWARD_SECONDS))
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

                    "ACTION_TOGGLE_FAVORITE" ->
                        (currentPlayer()?.queueInfo?.currentItem?.track as? AppMediaItem)
                            ?.takeIf { it.mediaType == MediaType.TRACK && it.canBeFavorited }
                            ?.let { dataSource.toggleFavorite(it) }
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
            if (!sessionBlocked.value) writeErrorToSession(it)
        }
    }

    /**
     * Clear the error state and immediately restore the last known playback data, so the
     * session isn't stuck at STATE_ERROR after a transient AA-host error/reconnect.
     */
    @Synchronized
    fun clearErrorState() {
        currentError = null
        if (sessionBlocked.value) return
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
        // Precedence, decided in this one place: blocked > error > playback.
        if (sessionBlocked.value) return
        currentError?.let {
            writeErrorToSession(it)
        } ?: run {
            writePlaybackToSession(data, bitmap, multiPlayer)
        }
    }

    @Synchronized
    private fun updateQueue(queue: List<MediaSessionCompat.QueueItem>) {
        if (sessionBlocked.value) return
        mediaSession?.setQueue(queue)
        mediaSession?.setQueueTitle(strings?.nowPlaying ?: "")
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
            .setActions(SESSION_TRANSPORT_ACTIONS)
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
                sessionActions(data).forEach { builder.addCustomAction(customAction(it, data)) }
            }
            .build()
        session.setPlaybackState(playbackState)

        val metadata = MediaMetadataCompat.Builder()
            .putString(
                MediaMetadataCompat.METADATA_KEY_TITLE,
                data.name ?: strings?.unknownTrack ?: "",
            )
            .putString(
                MediaMetadataCompat.METADATA_KEY_ARTIST,
                artistMetadata(data, multiPlayer),
            )
            .putString(
                MediaMetadataCompat.METADATA_KEY_ALBUM,
                // Chapter mode uses the chapter name instead of the album/book grouping.
                data.chapterName ?: data.album,
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

    // Artist line, with the "(on <player>)" suffix appended for remote players when
    // multiple players are active (mirrors the pre-localization concatenation).
    private fun artistMetadata(data: MediaNotificationData, multiPlayer: Boolean): String {
        val artist = data.artist ?: strings?.unknownArtist ?: ""
        val player = data.playerName?.takeIf { multiPlayer } ?: return artist
        return strings?.artistWithPlayer(artist, player) ?: artist
    }

    /**
     * Present nothing: deactivate the session so no host draws a card for it, and drop the
     * metadata and queue left behind by the previously presented player.
     */
    /**
     * Idle but capable: no playback to report, yet still advertising what the app can do.
     * STATE_NONE rather than STATE_PAUSED — a paused baseline draws a phantom empty card in the
     * shade and the output picker, while hosts read the action mask from either.
     */
    private fun idlePlaybackState(): PlaybackStateCompat =
        PlaybackStateCompat.Builder()
            .setActions(SESSION_TRANSPORT_ACTIONS)
            .setState(PlaybackStateCompat.STATE_NONE, 0, 0f)
            .build()

    @Synchronized
    private fun writeBlockToSession() {
        val session = mediaSession ?: return
        session.setPlaybackState(idlePlaybackState())
        session.setMetadata(MediaMetadataCompat.Builder().build())
        session.setQueue(emptyList())
        session.isActive = false
    }

    /** Reactivate the session and restore the last known playback data. */
    @Synchronized
    private fun liftBlockFromSession() {
        val session = mediaSession ?: return
        session.isActive = true
        currentError?.let { writeErrorToSession(it) }
            ?: lastData?.let { writePlaybackToSession(it, lastBitmap, lastMultiPlayer) }
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
                    strings?.openApp ?: "",
                )
            }
        }
        val playbackState = PlaybackStateCompat.Builder()
            // Keep the mask on an error state too: "reconnecting" is precisely when a user
            // retries by voice, and a host that sees no actions will not route the retry.
            .setActions(SESSION_TRANSPORT_ACTIONS)
            .setState(PlaybackStateCompat.STATE_ERROR, 0, 0f)
            .setErrorMessage(error.code, error.message)
            .also { builder -> extras?.let { builder.setExtras(it) } }
            .build()
        session.setPlaybackState(playbackState)
    }

    /**
     * Maps a picked [SessionAction] to its published action. The action ids are part of
     * the contract with [createCallback]; [sessionActions] owns which ones appear and in
     * which order. A toggle is only picked when its value is present, so the fallbacks
     * below are unreachable.
     */
    private fun customAction(
        action: SessionAction,
        data: MediaNotificationData,
    ): PlaybackStateCompat.CustomAction = when (action) {
        SessionAction.SWITCH_PLAYER -> customAction(
            "ACTION_SWITCH_PLAYER",
            strings?.nextPlayer,
            R.drawable.ic_speaker,
        )

        SessionAction.FAVORITE -> customAction(
            "ACTION_TOGGLE_FAVORITE",
            strings?.favorite,
            getFavoriteIcon(data.isFavorite),
        )

        SessionAction.SHUFFLE -> customAction(
            "ACTION_TOGGLE_SHUFFLE",
            strings?.shuffle,
            getShuffleModeIcon(data.shuffleEnabled == true),
        )

        SessionAction.REPEAT -> customAction(
            "ACTION_TOGGLE_REPEAT",
            strings?.repeat,
            getRepeatModeIcon(data.repeatMode ?: RepeatMode.OFF),
        )

        SessionAction.SEEK_BACK -> customAction(
            "ACTION_SEEK_BACK",
            strings?.rewind,
            R.drawable.baseline_replay_10_24,
        )

        SessionAction.SEEK_FORWARD -> customAction(
            "ACTION_SEEK_FORWARD",
            strings?.forward,
            R.drawable.baseline_forward_30_24,
        )
    }

    private fun customAction(id: String, label: String?, icon: Int) =
        PlaybackStateCompat.CustomAction.Builder(id, label ?: "", icon).build()

    private fun getRepeatModeIcon(repeatMode: RepeatMode): Int = when (repeatMode) {
        RepeatMode.ALL -> R.drawable.baseline_repeat_24
        RepeatMode.ONE -> R.drawable.baseline_repeat_one_24
        RepeatMode.OFF -> R.drawable.baseline_no_repeat_24
    }

    private fun getFavoriteIcon(isFavorite: Boolean): Int =
        if (isFavorite) {
            R.drawable.baseline_favorite_24
        } else {
            R.drawable.baseline_favorite_border_24
        }

    private fun getShuffleModeIcon(shuffleMode: Boolean): Int =
        if (shuffleMode) {
            R.drawable.baseline_shuffle_24
        } else {
            R.drawable.baseline_arrow_right_alt_24
        }

    private companion object {
        // Sendspin needs a moment to come up after the car connects. Without this window the
        // block state flashes on every connect, tearing down and rebuilding the session.
        const val SESSION_BLOCK_DEBOUNCE_MS = 1500L
    }
}
