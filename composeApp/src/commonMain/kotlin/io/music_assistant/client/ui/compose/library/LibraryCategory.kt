package io.music_assistant.client.ui.compose.library

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.ui.graphics.vector.ImageVector
import io.music_assistant.client.data.model.client.MediaType
import io.music_assistant.client.data.model.client.stringResource
import io.music_assistant.client.ui.compose.common.icons.AlbumIcon
import io.music_assistant.client.ui.compose.common.icons.ArtistIcon
import io.music_assistant.client.ui.compose.common.icons.BookAudioIcon
import io.music_assistant.client.ui.compose.common.icons.GenreIcon
import io.music_assistant.client.ui.compose.common.icons.PlaylistIcon
import io.music_assistant.client.ui.compose.common.icons.RadioIcon
import io.music_assistant.client.ui.compose.common.icons.TrackIcon
import org.jetbrains.compose.resources.StringResource

enum class LibraryCategory {
    ARTISTS, ALBUMS, TRACKS, PLAYLISTS, AUDIOBOOKS, PODCASTS, RADIOS, GENRES;

    val mediaType: MediaType
        get() = when (this) {
            ARTISTS -> MediaType.ARTIST
            ALBUMS -> MediaType.ALBUM
            TRACKS -> MediaType.TRACK
            PLAYLISTS -> MediaType.PLAYLIST
            AUDIOBOOKS -> MediaType.AUDIOBOOK
            PODCASTS -> MediaType.PODCAST
            RADIOS -> MediaType.RADIO
            GENRES -> MediaType.GENRE
        }
}

/** Tabs Android Auto / CarPlay can render at root — Tracks/Genres aren't AA tabs. */
val carTabCategories: List<LibraryCategory> = listOf(
    LibraryCategory.ARTISTS,
    LibraryCategory.ALBUMS,
    LibraryCategory.PLAYLISTS,
    LibraryCategory.PODCASTS,
    LibraryCategory.RADIOS,
    LibraryCategory.AUDIOBOOKS,
)

fun LibraryCategory.stringResource(): StringResource = mediaType.stringResource()

fun LibraryCategory.icon(): ImageVector = when (this) {
    LibraryCategory.ARTISTS -> ArtistIcon
    LibraryCategory.ALBUMS -> AlbumIcon
    LibraryCategory.TRACKS -> TrackIcon
    LibraryCategory.PLAYLISTS -> PlaylistIcon
    LibraryCategory.AUDIOBOOKS -> BookAudioIcon
    LibraryCategory.PODCASTS -> Icons.Default.Podcasts
    LibraryCategory.RADIOS -> RadioIcon
    LibraryCategory.GENRES -> GenreIcon
}
