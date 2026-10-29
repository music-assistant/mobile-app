package io.music_assistant.client.data.model.client

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Podcasts
import io.music_assistant.client.ui.compose.common.icons.BookAudioIcon
import io.music_assistant.client.ui.compose.common.icons.RadioIcon
import io.music_assistant.client.ui.compose.common.icons.TrackIcon

data class PlayerMedia(
    val title: String?,
    val artist: String?,
    val album: String?,
    val imageUrl: String?,
    val duration: Double?,
    val queueId: String?,
    val queueItemId: String?,
    val mediaType: MediaType?,
    val uri: String?,
    // Where a radio stream is in the song it plays, when the stream knows.
    val songTiming: SongTiming? = null,
) {
    val subtitle = listOfNotNull(artist, album)
        .takeIf { it.isNotEmpty() }?.joinToString(" • ")

    val defaultIcon = when (mediaType) {
        MediaType.AUDIOBOOK -> BookAudioIcon
        MediaType.PODCAST_EPISODE -> Icons.Default.Podcasts
        MediaType.RADIO -> RadioIcon
        else -> TrackIcon
    }
}

/**
 * A radio stream's own report of the song on air: its [duration], and [elapsed] seconds into it
 * at [updatedAt] (epoch seconds). The queue only knows how long the station has played.
 */
data class SongTiming(
    val duration: Double,
    val elapsed: Double,
    val updatedAt: Double,
) {
    /** Seconds into the song at [nowSec] (epoch seconds), up to its end. */
    fun positionAt(nowSec: Double): Double = (elapsed + nowSec - updatedAt).coerceIn(0.0, duration)
}
