@file:OptIn(ExperimentalMaterial3Api::class)

package io.music_assistant.client.ui.compose.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.music_assistant.client.data.model.client.AppMediaItem
import io.music_assistant.client.data.model.server.MediaType
import io.music_assistant.client.data.model.server.QueueOption
import io.music_assistant.client.ui.compose.common.DataState
import io.music_assistant.client.ui.compose.common.items.AlbumWithMenu
import io.music_assistant.client.ui.compose.common.items.ArtistWithMenu
import io.music_assistant.client.ui.compose.common.items.AudiobookWithMenu
import io.music_assistant.client.ui.compose.common.items.GenreWithMenu
import io.music_assistant.client.ui.compose.common.items.PlaylistWithMenu
import io.music_assistant.client.ui.compose.common.items.PodcastEpisodeWithMenu
import io.music_assistant.client.ui.compose.common.items.PodcastWithMenu
import io.music_assistant.client.ui.compose.common.items.RadioWithMenu
import io.music_assistant.client.ui.compose.common.items.TrackWithMenu
import io.music_assistant.client.ui.compose.common.viewmodel.ActionsViewModel
import io.music_assistant.client.ui.compose.nav.Screen
import io.music_assistant.client.utils.SessionState
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues,
    connectionState: SessionState,
    dataState: DataState<List<AppMediaItem.RecommendationFolder>>,
    serverUrl: String?,
    onNavigateClick: (AppMediaItem) -> Unit,
    onPlayClick: ((AppMediaItem, QueueOption, Boolean) -> Unit),
    onLibraryItemClick: (MediaType?) -> Unit,
    playlistActions: ActionsViewModel.PlaylistActions,
    libraryActions: ActionsViewModel.LibraryActions,
    progressActions: ActionsViewModel.ProgressActions? = null,
    providerIconFetcher: (@Composable (Modifier, String) -> Unit)
) {
    val filteredData = remember(dataState) {
        if (dataState is DataState.Data) {
            dataState.data.filter {
                it.items?.any { item ->
                    item is AppMediaItem.Track
                            || item is AppMediaItem.Artist
                            || item is AppMediaItem.Album
                            || item is AppMediaItem.Playlist
                            || item is AppMediaItem.Audiobook
                            || item is AppMediaItem.Podcast
                            || item is AppMediaItem.PodcastEpisode
                            || item is AppMediaItem.RadioStation
                            || item is AppMediaItem.Genre
                } == true
            }
        } else {
            emptyList()
        }
    }

    val listState = rememberLazyListState()

    Screen(
        topBar = { scrollBehavior ->
            LandingPageTopBar(scrollBehavior)
        }
    ) {
        LazyColumn(
            state = listState,
            contentPadding = contentPadding
        ) {
            if (connectionState !is SessionState.Connected || dataState !is DataState.Data) {
                item {
                    Box(
                        modifier = modifier.fillMaxWidth().height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else {
                items(
                    items = filteredData,
                    key = { it.itemId }
                ) { row ->
                    CategoryRow(
                        serverUrl = serverUrl,
                        title = row.name,
                        rowItemType = row.rowItemType,
                        onNavigateClick = onNavigateClick,
                        onPlayClick = onPlayClick,
                        onAllClick = { row.rowItemType?.let { onLibraryItemClick(it) } },
                        mediaItems = row.items.orEmpty(),
                        playlistActions = playlistActions,
                        libraryActions = libraryActions,
                        progressActions = progressActions,
                        providerIconFetcher = providerIconFetcher,
                    )
                }
            }
        }
    }
}

@Composable
private fun LandingPageTopBar(scrollBehavior: TopAppBarScrollBehavior) {
    TopAppBar(
        title = { Text(stringResource(Res.string.nav_home)) },
        scrollBehavior = scrollBehavior
    )
}

// --- Common UI Components ---

@Composable
fun CategoryRow(
    serverUrl: String?,
    title: String,
    rowItemType: MediaType?,
    onNavigateClick: (AppMediaItem) -> Unit,
    onPlayClick: ((AppMediaItem, QueueOption, Boolean) -> Unit),
    onAllClick: () -> Unit,
    mediaItems: List<AppMediaItem>,
    playlistActions: ActionsViewModel.PlaylistActions,
    libraryActions: ActionsViewModel.LibraryActions,
    progressActions: ActionsViewModel.ProgressActions? = null,
    providerIconFetcher: (@Composable (Modifier, String) -> Unit)
) {
    val rowListState = rememberLazyListState()

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge
            )
            rowItemType?.let { type ->
                val allTitle = allItemsTitle(type)
                allTitle?.let {
                    TextButton(
                        onClick = onAllClick,
                        contentPadding = PaddingValues(start = 4.dp, end = 4.dp)
                    ) {
                        Text(allTitle, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
        LazyRow(
            state = rowListState,
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(
                items = mediaItems,
                key = { item ->
                    when (item) {
                        is AppMediaItem.Track,
                        is AppMediaItem.Artist,
                        is AppMediaItem.Album,
                        is AppMediaItem.Playlist,
                        is AppMediaItem.Audiobook,
                        is AppMediaItem.Podcast,
                        is AppMediaItem.PodcastEpisode,
                        is AppMediaItem.RadioStation,
                        is AppMediaItem.Genre -> "${item::class.simpleName}_${item.itemId}"

                        else -> item.hashCode()
                    }
                },
                contentType = { item ->
                    when (item) {
                        is AppMediaItem.Track -> "Track"
                        is AppMediaItem.Artist -> "Artist"
                        is AppMediaItem.Album -> "Album"
                        is AppMediaItem.Playlist -> "Playlist"
                        is AppMediaItem.Audiobook -> "Audiobook"
                        is AppMediaItem.Podcast -> "Podcast"
                        is AppMediaItem.PodcastEpisode -> "Episode"
                        is AppMediaItem.RadioStation -> "RadioStation"
                        is AppMediaItem.Genre -> "Genre"
                        else -> "Unknown"
                    }
                }
            ) { item ->
                when (item) {
                    is AppMediaItem.Artist -> ArtistWithMenu(
                        item = item,

                        serverUrl = serverUrl,
                        onNavigateClick = onNavigateClick,
                        onPlayOption = onPlayClick,
                        libraryActions = libraryActions,
                        providerIconFetcher = providerIconFetcher
                    )

                    is AppMediaItem.Album -> AlbumWithMenu(
                        item = item,

                        serverUrl = serverUrl,
                        onNavigateClick = onNavigateClick,
                        onPlayOption = onPlayClick,
                        libraryActions = libraryActions,
                        providerIconFetcher = providerIconFetcher
                    )

                    is AppMediaItem.Playlist -> PlaylistWithMenu(
                        item = item,

                        serverUrl = serverUrl,
                        onNavigateClick = onNavigateClick,
                        onPlayOption = onPlayClick,
                        libraryActions = libraryActions,
                        providerIconFetcher = providerIconFetcher
                    )

                    is AppMediaItem.Podcast -> PodcastWithMenu(
                        item = item,

                        serverUrl = serverUrl,
                        onNavigateClick = onNavigateClick,
                        onPlayOption = onPlayClick,
                        libraryActions = libraryActions,
                        providerIconFetcher = providerIconFetcher
                    )

                    is AppMediaItem.Track -> TrackWithMenu(
                        item = item,
                        serverUrl = serverUrl,
                        onPlayOption = onPlayClick,
                        playlistActions = playlistActions,
                        libraryActions = libraryActions,
                        providerIconFetcher = providerIconFetcher
                    )

                    is AppMediaItem.PodcastEpisode -> PodcastEpisodeWithMenu(
                        item = item,
                        serverUrl = serverUrl,
                        onPlayOption = onPlayClick,
                        playlistActions = playlistActions,
                        libraryActions = libraryActions,
                        progressActions = progressActions,
                        providerIconFetcher = providerIconFetcher
                    )

                    is AppMediaItem.Audiobook -> AudiobookWithMenu(
                        item = item,

                        serverUrl = serverUrl,
                        onNavigateClick = onNavigateClick,
                        onPlayOption = onPlayClick,
                        libraryActions = libraryActions,
                        progressActions = progressActions,
                        providerIconFetcher = providerIconFetcher
                    )

                    is AppMediaItem.RadioStation -> RadioWithMenu(
                        item = item,
                        serverUrl = serverUrl,
                        onPlayOption = onPlayClick,
                        playlistActions = playlistActions,
                        libraryActions = libraryActions,
                        providerIconFetcher = providerIconFetcher
                    )

                    is AppMediaItem.Genre -> GenreWithMenu(
                        item = item,

                        serverUrl = serverUrl,
                        onNavigateClick = onNavigateClick,
                        onPlayOption = onPlayClick,
                        libraryActions = libraryActions,
                        providerIconFetcher = providerIconFetcher
                    )

                    else -> {}
                }
            }
        }
    }
}

@Composable
fun allItemsTitle(type: MediaType) = when (type) {
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


