@file:OptIn(ExperimentalMaterial3Api::class)

package io.music_assistant.client.ui.compose.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Podcasts
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.music_assistant.client.data.model.client.AppMediaItem
import io.music_assistant.client.data.model.server.MediaType
import io.music_assistant.client.data.model.server.QueueOption
import io.music_assistant.client.ui.compose.common.DataState
import io.music_assistant.client.ui.compose.common.icons.AlbumIcon
import io.music_assistant.client.ui.compose.common.icons.ArtistIcon
import io.music_assistant.client.ui.compose.common.icons.BookAudioIcon
import io.music_assistant.client.ui.compose.common.icons.GenreIcon
import io.music_assistant.client.ui.compose.common.icons.PlaylistIcon
import io.music_assistant.client.ui.compose.common.icons.RadioIcon
import io.music_assistant.client.ui.compose.common.icons.TrackIcon
import io.music_assistant.client.ui.compose.common.items.AlbumWithMenu
import io.music_assistant.client.ui.compose.common.items.ArtistWithMenu
import io.music_assistant.client.ui.compose.common.items.AudiobookWithMenu
import io.music_assistant.client.ui.compose.common.items.GenreWithMenu
import io.music_assistant.client.ui.compose.common.items.PlaylistWithMenu
import io.music_assistant.client.ui.compose.common.items.PodcastEpisodeWithMenu
import io.music_assistant.client.ui.compose.common.items.PodcastWithMenu
import io.music_assistant.client.ui.compose.common.items.RadioWithMenu
import io.music_assistant.client.ui.compose.common.items.TrackWithMenu
import io.music_assistant.client.ui.compose.common.painters.rememberPlaceholderPainter
import io.music_assistant.client.ui.compose.common.viewmodel.ActionsViewModel
import io.music_assistant.client.ui.compose.nav.Screen
import io.music_assistant.client.utils.SessionState

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
            // Your library row
            item {
                LibraryRow(onLibraryItemClick = onLibraryItemClick)
            }
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
                        row = row,
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
        title = { Text("Home") },
        scrollBehavior = scrollBehavior
    )
}

// --- Common UI Components ---

@Composable
fun LibraryRow(
    onLibraryItemClick: (MediaType?) -> Unit
) {
    val libraryItems = remember {
        listOf(
            LibraryItem("Artists", ArtistIcon, MediaType.ARTIST),
            LibraryItem("Albums", AlbumIcon, MediaType.ALBUM),
            LibraryItem("Tracks", TrackIcon, MediaType.TRACK),
            LibraryItem("Playlists", PlaylistIcon, MediaType.PLAYLIST),
            LibraryItem("Audiobooks", BookAudioIcon, MediaType.AUDIOBOOK),
            LibraryItem("Podcasts", Icons.Default.Podcasts, MediaType.PODCAST),
            LibraryItem("Radio", RadioIcon, MediaType.RADIO),
            LibraryItem("Genres", GenreIcon, MediaType.GENRE),
        )
    }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Your library",
                style = MaterialTheme.typography.titleLarge
            )
        }
        LazyRow(
            modifier = Modifier.testTag("LibraryRow"),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(libraryItems) { item ->
                LibraryItemCard(
                    modifier = Modifier,
                    name = item.name,
                    icon = item.icon,
                    onClick = { onLibraryItemClick(item.type) }
                )
            }
        }
    }
}

@Composable
fun LibraryItemCard(
    modifier: Modifier,
    name: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val primary = MaterialTheme.colorScheme.primary

    Column(
        modifier = modifier
            .wrapContentSize()
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(width = 96.dp, height = 40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            val placeholder = rememberPlaceholderPainter(
                backgroundColor = primaryContainer,
                iconColor = primary,
                icon = icon
            )
            Image(
                painter = placeholder,
                contentDescription = name,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

private data class LibraryItem(
    val name: String,
    val icon: ImageVector,
    val type: MediaType?,
)

@Composable
fun CategoryRow(
    serverUrl: String?,
    row: AppMediaItem.RecommendationFolder,
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
                text = row.name,
                style = MaterialTheme.typography.titleLarge
            )
            row.rowItemType?.let { type ->
                val title = allItemsTitle(type)
                title?.let {
                    TextButton(
                        onClick = onAllClick,
                        contentPadding = PaddingValues(start = 4.dp, end = 4.dp)
                    ) {
                        Text(title, style = MaterialTheme.typography.labelLarge)
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

fun allItemsTitle(type: MediaType) = when (type) {
    MediaType.TRACK -> "All tracks"
    MediaType.ALBUM -> "All albums"
    MediaType.ARTIST -> "All artists"
    MediaType.PLAYLIST -> "All playlists"
    MediaType.AUDIOBOOK -> "All audiobooks"
    MediaType.PODCAST -> "All podcasts"
    MediaType.RADIO -> "All radio stations"
    MediaType.GENRE -> "All genres"
    else -> null
}


