package io.music_assistant.client.ui.compose.library

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.outlined.Folder
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
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.ai_radio_title
import musicassistantclient.composeapp.generated.resources.nav_browse
import org.jetbrains.compose.resources.StringResource

enum class LibraryCategory {
    ARTISTS, ALBUMS, TRACKS, PLAYLISTS, AUDIOBOOKS, PODCASTS, RADIOS, GENRES, BROWSE, AI_RADIO;

    /**
     * Null for the categories that are not server media types: path-based [BROWSE] and
     * [AI_RADIO], whose stations are plugin records rather than library items.
     */
    val mediaType: MediaType?
        get() = when (this) {
            ARTISTS -> MediaType.ARTIST
            ALBUMS -> MediaType.ALBUM
            TRACKS -> MediaType.TRACK
            PLAYLISTS -> MediaType.PLAYLIST
            AUDIOBOOKS -> MediaType.AUDIOBOOK
            PODCASTS -> MediaType.PODCAST
            RADIOS -> MediaType.RADIO
            GENRES -> MediaType.GENRE
            BROWSE -> null
            AI_RADIO -> null
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
    LibraryCategory.AI_RADIO,
)

fun LibraryCategory.stringResource(): StringResource = when (this) {
    LibraryCategory.BROWSE -> Res.string.nav_browse
    LibraryCategory.AI_RADIO -> Res.string.ai_radio_title
    // Every remaining category is server-media-type backed, so its label comes from there.
    else -> checkNotNull(mediaType).stringResource()
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
    LibraryCategory.BROWSE -> Icons.Outlined.Folder
    LibraryCategory.AI_RADIO -> Icons.Default.SmartToy
}
