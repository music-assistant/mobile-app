package io.music_assistant.client.ui.compose.common.items

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.music_assistant.client.data.model.client.MediaType
import io.music_assistant.client.data.model.client.items.Album
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.Artist
import io.music_assistant.client.data.model.client.items.Audiobook
import io.music_assistant.client.data.model.client.items.Genre
import io.music_assistant.client.data.model.client.items.Playlist
import io.music_assistant.client.data.model.client.items.Podcast
import io.music_assistant.client.data.model.client.items.PodcastEpisode
import io.music_assistant.client.data.model.client.items.RadioStation
import io.music_assistant.client.data.model.client.items.Track
import io.music_assistant.client.ui.compose.common.DisplayString
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.all_albums
import musicassistantclient.composeapp.generated.resources.all_artists
import musicassistantclient.composeapp.generated.resources.all_audiobooks
import musicassistantclient.composeapp.generated.resources.all_genres
import musicassistantclient.composeapp.generated.resources.all_playlists
import musicassistantclient.composeapp.generated.resources.all_podcasts
import musicassistantclient.composeapp.generated.resources.all_radio
import musicassistantclient.composeapp.generated.resources.all_tracks
import org.jetbrains.compose.resources.stringResource

data class ItemCategory(
    val id: String,
    val title: DisplayString,
    val items: List<AppMediaItem>,
    val lazyListKey: Any,
    val itemType: MediaType? = null,
    val tag: String? = null,
)

@Composable
fun CategoryRow(
    itemCategory: ItemCategory,
    onNavigateClick: (AppMediaItem) -> Unit,
    onPlayClick: PlayHandler<AppMediaItem>,
    onAllClick: () -> Unit = { },
    playlistActions: PlaylistActions,
    libraryActions: LibraryActions,
    progressActions: ProgressActions? = null,
    providerIconFetcher: (@Composable (Modifier, String) -> Unit),
) {
    CategoryRow(
        title = itemCategory.title.string(),
        rowItemType = itemCategory.itemType,
        onNavigateClick = onNavigateClick,
        onPlayClick = onPlayClick,
        onAllClick = onAllClick,
        mediaItems = itemCategory.items,
        playlistActions = playlistActions,
        libraryActions = libraryActions,
        progressActions = progressActions,
        providerIconFetcher = providerIconFetcher,
        rowTag = itemCategory.tag,
    )
}

@Composable
fun CategoryRow(
    title: String,
    rowItemType: MediaType? = null,
    onNavigateClick: (AppMediaItem) -> Unit,
    onPlayClick: PlayHandler<AppMediaItem>,
    onAllClick: () -> Unit = { },
    mediaItems: List<AppMediaItem>,
    playlistActions: PlaylistActions,
    libraryActions: LibraryActions,
    progressActions: ProgressActions? = null,
    providerIconFetcher: (@Composable (Modifier, String) -> Unit),
    rowTag: String? = null,
) {
    val rowListState = rememberLazyListState()

    Column {
        RowTitle(
            title = title,
            link = {
                rowItemType?.let { type ->
                    val allTitle = allItemsTitle(type)
                    allTitle?.let {
                        TextButton(
                            onClick = onAllClick,
                            contentPadding = PaddingValues(start = 4.dp, end = 4.dp),
                        ) {
                            Text(allTitle, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            },
        )

        val modifier = if (rowTag != null) {
            Modifier.testTag(rowTag)
        } else {
            Modifier
        }

        LazyRow(
            modifier = modifier,
            state = rowListState,
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(
                items = mediaItems,
                key = { it.lazyListKey() },
                contentType = { item ->
                    when (item) {
                        is Track -> "Track"
                        is Artist -> "Artist"
                        is Album -> "Album"
                        is Playlist -> "Playlist"
                        is Audiobook -> "Audiobook"
                        is Podcast -> "Podcast"
                        is PodcastEpisode -> "Episode"
                        is RadioStation -> "RadioStation"
                        is Genre -> "Genre"
                        else -> "Unknown"
                    }
                },
            ) { item ->
                when (item) {
                    is Artist -> ArtistWithMenu(
                        item = item,
                        onNavigateClick = onNavigateClick,
                        onPlayOption = onPlayClick,
                        libraryActions = libraryActions,
                        providerIconFetcher = providerIconFetcher,
                    )

                    is Album -> AlbumWithMenu(
                        item = item,
                        onNavigateClick = onNavigateClick,
                        onPlayOption = onPlayClick,
                        playlistActions = playlistActions,
                        libraryActions = libraryActions,
                        providerIconFetcher = providerIconFetcher,
                    )

                    is Playlist -> PlaylistWithMenu(
                        item = item,
                        onNavigateClick = onNavigateClick,
                        onPlayOption = onPlayClick,
                        libraryActions = libraryActions,
                        providerIconFetcher = providerIconFetcher,
                    )

                    is Podcast -> PodcastWithMenu(
                        item = item,
                        onNavigateClick = onNavigateClick,
                        onPlayOption = onPlayClick,
                        libraryActions = libraryActions,
                        providerIconFetcher = providerIconFetcher,
                    )

                    is Track -> TrackWithMenu(
                        item = item,
                        onPlayOption = onPlayClick,
                        playlistActions = playlistActions,
                        libraryActions = libraryActions,
                        providerIconFetcher = providerIconFetcher,
                    )

                    is PodcastEpisode -> PodcastEpisodeWithMenu(
                        item = item,
                        onPlayOption = onPlayClick,
                        playlistActions = playlistActions,
                        libraryActions = libraryActions,
                        progressActions = progressActions,
                        providerIconFetcher = providerIconFetcher,
                    )

                    is Audiobook -> AudiobookWithMenu(
                        item = item,
                        onNavigateClick = onNavigateClick,
                        onPlayOption = onPlayClick,
                        playlistActions = playlistActions,
                        libraryActions = libraryActions,
                        progressActions = progressActions,
                        providerIconFetcher = providerIconFetcher,
                    )

                    is RadioStation -> RadioWithMenu(
                        item = item,
                        onPlayOption = onPlayClick,
                        playlistActions = playlistActions,
                        libraryActions = libraryActions,
                        providerIconFetcher = providerIconFetcher,
                    )

                    is Genre -> GenreWithMenu(
                        item = item,
                        onNavigateClick = onNavigateClick,
                        onPlayOption = onPlayClick,
                        libraryActions = libraryActions,
                        providerIconFetcher = providerIconFetcher,
                    )

                    else -> {}
                }
            }
        }
    }
}

@Composable
private fun RowTitle(
    title: String,
    link: @Composable () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
        )

        link()
    }
}

@Composable
private fun allItemsTitle(type: MediaType) = when (type) {
    MediaType.TRACK -> stringResource(Res.string.all_tracks)
    MediaType.ALBUM -> stringResource(Res.string.all_albums)
    MediaType.ARTIST -> stringResource(Res.string.all_artists)
    MediaType.PLAYLIST -> stringResource(Res.string.all_playlists)
    MediaType.AUDIOBOOK -> stringResource(Res.string.all_audiobooks)
    MediaType.PODCAST -> stringResource(Res.string.all_podcasts)
    MediaType.RADIO -> stringResource(Res.string.all_radio)
    MediaType.GENRE -> stringResource(Res.string.all_genres)
    else -> null
}
