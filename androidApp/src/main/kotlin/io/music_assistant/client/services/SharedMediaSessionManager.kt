package io.music_assistant.client.services

import android.app.PendingIntent
import android.content.Context
import android.graphics.Bitmap
import android.media.AudioManager
import android.media.session.PlaybackState
import android.os.Bundle
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.media.utils.MediaConstants
import io.music_assistant.client.R
import io.music_assistant.client.data.model.server.RepeatMode

/**
 * Single source of truth for the app's MediaSession.
 * Both AndroidAutoPlaybackService and MainMediaPlaybackService share this instance
 * via Koin singleton, ensuring Android sees exactly one active session.
 *
 * Reference-counted: first [acquire] creates the session, last [release] destroys it.
 */
class SharedMediaSessionManager(private val applicationContext: Context) {
    private var mediaSession: MediaSessionCompat? = null
    private var refCount = 0
    private var autoServiceActive = false

    // Store callbacks so we can swap when AA connects/disconnects
    private var autoCallback: MediaSessionCompat.Callback? = null
    private var notificationCallback: MediaSessionCompat.Callback? = null

    // Cached last playback data — used to restore state after clearing errors
    private var lastData: MediaNotificationData? = null
    private var lastBitmap: Bitmap? = null
    private var lastMultiPlayer: Boolean = false

    // Current error state (non-null = error takes precedence over playback)
    private var currentError: ErrorState? = null

    data class ErrorState(
        val code: Int,
        val message: String,
        val resolution: PendingIntent? = null,
    )

    @Synchronized
    fun acquire(
        callback: MediaSessionCompat.Callback,
        isAutoService: Boolean,
    ): MediaSessionCompat.Token {
        if (mediaSession == null) {
            mediaSession = MediaSessionCompat(applicationContext, "MusicAssistantSession").apply {
                setFlags(MediaSessionCompat.FLAG_HANDLES_QUEUE_COMMANDS)
                setPlaybackToLocal(AudioManager.STREAM_MUSIC)
                isActive = true
            }
        }
        if (isAutoService) {
            autoServiceActive = true
            autoCallback = callback
            mediaSession?.setCallback(callback)
        } else {
            notificationCallback = callback
            if (!autoServiceActive) {
                mediaSession?.setCallback(callback)
            }
        }
        refCount++
        return mediaSession!!.sessionToken
    }

    @Synchronized
    fun release(isAutoService: Boolean) {
        if (isAutoService) {
            autoServiceActive = false
            autoCallback = null
            // Restore notification callback if it's still alive
            notificationCallback?.let { mediaSession?.setCallback(it) }
        } else {
            notificationCallback = null
        }
        refCount--
        if (refCount <= 0) {
            mediaSession?.release()
            mediaSession = null
            refCount = 0
            currentError = null
            lastData = null
            lastBitmap = null
            autoCallback = null
            notificationCallback = null
        }
    }

    fun isAutoServiceActive(): Boolean = autoServiceActive

    /**
     * Set an error state. The error takes precedence: [updatePlaybackState] will show
     * the error until [clearErrorState] is called.
     */
    @Synchronized
    fun setErrorState(code: Int, message: String, resolution: PendingIntent? = null) {
        currentError = ErrorState(code, message, resolution)
        writeErrorToSession(currentError!!)
    }

    /**
     * Clear the error state and immediately restore the last known playback data.
     * This is the key fix for Bug 1: no more "set STATE_NONE and hope the data flow re-emits."
     */
    @Synchronized
    fun clearErrorState() {
        currentError = null
        // Restore cached playback state so the session isn't stuck at STATE_NONE
        lastData?.let { writePlaybackToSession(it, lastBitmap, lastMultiPlayer) }
    }

    /**
     * Update the MediaSession with playback data + metadata.
     * If an error is active, the error is shown instead (data is still cached).
     */
    @Synchronized
    fun updatePlaybackState(
        data: MediaNotificationData,
        bitmap: Bitmap?,
        multiPlayer: Boolean,
    ) {
        lastData = data
        lastBitmap = bitmap
        lastMultiPlayer = multiPlayer
        if (currentError != null) {
            writeErrorToSession(currentError!!)
        } else {
            writePlaybackToSession(data, bitmap, multiPlayer)
        }
    }

    @Synchronized
    fun updateQueue(queue: List<MediaSessionCompat.QueueItem>) {
        mediaSession?.setQueue(queue)
        mediaSession?.setQueueTitle("Now playing")
    }

    fun getSessionToken(): MediaSessionCompat.Token? = mediaSession?.sessionToken

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
                1f,
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
