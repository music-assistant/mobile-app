package io.music_assistant.client.data.model.client

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Podcasts
import io.music_assistant.client.data.model.server.MediaType
import io.music_assistant.client.data.model.server.ServerPlayerMedia
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
) {
    val subtitle = listOfNotNull(artist, album)
        .takeIf { it.isNotEmpty() }?.joinToString(" • ")

    val defaultIcon = when (mediaType) {
        MediaType.AUDIOBOOK -> BookAudioIcon
        MediaType.PODCAST_EPISODE -> Icons.Default.Podcasts
        MediaType.RADIO -> RadioIcon
        else -> TrackIcon
    }

    companion object {
        fun ServerPlayerMedia.toPlayerMedia() = PlayerMedia(
            title = title,
            artist = artist,
            album = album,
            imageUrl = imageUrl,
            duration = duration.takeIf { mediaType != MediaType.RADIO },
            queueId = queueId,
            queueItemId = queueItemId,
            mediaType = mediaType,
            uri = uri,
        )
    }
}
