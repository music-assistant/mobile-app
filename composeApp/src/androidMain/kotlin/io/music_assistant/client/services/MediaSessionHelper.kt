package io.music_assistant.client.services

import android.content.Context
import android.graphics.Bitmap
import android.media.AudioManager
import android.media.session.PlaybackState
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import io.music_assistant.client.R
import io.music_assistant.client.data.model.server.RepeatMode

class MediaSessionHelper(
    tag: String,
    private val multiPlayer: Boolean,
    context: Context,
    callback: MediaSessionCompat.Callback,
) {
    private val mediaSession: MediaSessionCompat = MediaSessionCompat(context, tag)

    init {
        mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_QUEUE_COMMANDS)
        mediaSession.setCallback(callback)
        mediaSession.setPlaybackToLocal(AudioManager.STREAM_MUSIC)
        mediaSession.isActive = true
    }

    fun getSessionToken(): MediaSessionCompat.Token {
        return mediaSession.sessionToken
    }

    /**
     * Release resources and unregister observers
     */
    fun release() {
        mediaSession.release()
    }

    fun updatePlaybackState(
        data: MediaNotificationData,
        bitmap: Bitmap?
    ) {
        val state = if (data.isPlaying)
            PlaybackStateCompat.STATE_PLAYING
        else
            PlaybackStateCompat.STATE_PAUSED
        val playbackState = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_SEEK_TO or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
            )
            .setState(
                state,
                data.elapsedTime ?: PlaybackState.PLAYBACK_POSITION_UNKNOWN,
                1f
            )
            .setActiveQueueItemId(
                data.longItemId ?: MediaSessionCompat.QueueItem.UNKNOWN_ID.toLong()
            )
            .also { builder ->
                data.shuffleEnabled?.let { shuffle ->
                    builder.addCustomAction(
                        PlaybackStateCompat.CustomAction.Builder(
                            "ACTION_TOGGLE_SHUFFLE",
                            "Shuffle",
                            getShuffleModeIcon(shuffle)
                        ).build()
                    )
                }
                if (data.multiplePlayers) {
                    builder.addCustomAction(
                        PlaybackStateCompat.CustomAction.Builder(
                            "ACTION_SWITCH_PLAYER",
                            "Next player",
                            R.drawable.ic_speaker
                        ).build()
                    )
                } else {
                    data.repeatMode?.let { repeatMode ->
                        builder.addCustomAction(
                            PlaybackStateCompat.CustomAction.Builder(
                                "ACTION_TOGGLE_REPEAT",
                                "Repeat",
                                getRepeatModeIcon(repeatMode)
                            ).build()
                        )
                    }
                }
            }
            .build()
        mediaSession.setPlaybackState(playbackState)

        val metadata = MediaMetadataCompat.Builder()
            .putString(
                MediaMetadataCompat.METADATA_KEY_TITLE,
                data.name ?: "Unknown Track"
            )
            .putString(
                MediaMetadataCompat.METADATA_KEY_ARTIST,
                (data.artist
                    ?: "Unknown Artist") + (if (multiPlayer) data.playerName?.let { " (on $it)" }
                    ?: "" else "")
            )
            .putString(
                MediaMetadataCompat.METADATA_KEY_ALBUM,
                data.album
            )
            .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, bitmap)
            .also { builder ->
                data.duration?.let {
                    builder.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, it)
                }
            }
            .build()

        mediaSession.setMetadata(metadata)
    }

    fun updateQueue(queue: List<MediaSessionCompat.QueueItem>) {
        mediaSession.setQueue(queue)
        mediaSession.setQueueTitle("Now playing")
    }

    private fun getRepeatModeIcon(repeatMode: RepeatMode): Int = when (repeatMode) {
        RepeatMode.ALL -> R.drawable.baseline_repeat_24
        RepeatMode.ONE -> R.drawable.baseline_repeat_one_24
        RepeatMode.OFF -> R.drawable.baseline_no_repeat_24
    }

    private fun getShuffleModeIcon(shuffleMode: Boolean): Int =
        if (shuffleMode) R.drawable.baseline_shuffle_24
        else R.drawable.baseline_arrow_right_alt_24

}
