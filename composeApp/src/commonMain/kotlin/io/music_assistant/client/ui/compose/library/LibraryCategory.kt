package io.music_assistant.client.ui.compose.library

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.ui.graphics.vector.ImageVector
import io.music_assistant.client.data.model.client.MediaType
import io.music_assistant.client.ui.compose.common.icons.AlbumIcon
import io.music_assistant.client.ui.compose.common.icons.ArtistIcon
import io.music_assistant.client.ui.compose.common.icons.BookAudioIcon
import io.music_assistant.client.ui.compose.common.icons.GenreIcon
import io.music_assistant.client.ui.compose.common.icons.PlaylistIcon
import io.music_assistant.client.ui.compose.common.icons.RadioIcon
import io.music_assistant.client.ui.compose.common.icons.TrackIcon
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.media_type_albums
import musicassistantclient.composeapp.generated.resources.media_type_artists
import musicassistantclient.composeapp.generated.resources.media_type_audiobooks
import musicassistantclient.composeapp.generated.resources.media_type_genres
import musicassistantclient.composeapp.generated.resources.media_type_playlists
import musicassistantclient.composeapp.generated.resources.media_type_podcasts
import musicassistantclient.composeapp.generated.resources.media_type_radio
import musicassistantclient.composeapp.generated.resources.media_type_tracks
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

fun LibraryCategory.stringResource(): StringResource = when (this) {
    LibraryCategory.ARTISTS -> Res.string.media_type_artists
    LibraryCategory.ALBUMS -> Res.string.media_type_albums
    LibraryCategory.TRACKS -> Res.string.media_type_tracks
    LibraryCategory.PLAYLISTS -> Res.string.media_type_playlists
    LibraryCategory.AUDIOBOOKS -> Res.string.media_type_audiobooks
    LibraryCategory.PODCASTS -> Res.string.media_type_podcasts
    LibraryCategory.RADIOS -> Res.string.media_type_radio
    LibraryCategory.GENRES -> Res.string.media_type_genres
}

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
