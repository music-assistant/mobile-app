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
import androidx.compose.material.icons.automirrored.filled.FeaturedPlayList
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.music_assistant.client.data.model.client.AppMediaItem
import io.music_assistant.client.data.model.server.MediaType
import io.music_assistant.client.data.model.server.QueueOption
import io.music_assistant.client.ui.compose.common.DataState
import io.music_assistant.client.ui.compose.common.items.AlbumWithMenu
import io.music_assistant.client.ui.compose.common.items.ArtistWithMenu
import io.music_assistant.client.ui.compose.common.items.AudiobookWithMenu
import io.music_assistant.client.ui.compose.common.items.PlaylistWithMenu
import io.music_assistant.client.ui.compose.common.items.PodcastEpisodeWithMenu
import io.music_assistant.client.ui.compose.common.items.PodcastWithMenu
import io.music_assistant.client.ui.compose.common.items.RadioWithMenu
import io.music_assistant.client.ui.compose.common.items.TrackWithMenu
import io.music_assistant.client.ui.compose.common.painters.rememberPlaceholderPainter
import io.music_assistant.client.ui.compose.common.viewmodel.ActionsViewModel
import io.music_assistant.client.ui.compose.nav.NavScreen
import io.music_assistant.client.utils.SessionState
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.mass
import org.jetbrains.compose.resources.painterResource

@Composable
fun LandingPage(
    modifier: Modifier = Modifier,
    connectionState: SessionState,
    dataState: DataState<List<AppMediaItem.RecommendationFolder>>,
    serverUrl: String?,
    onNavigateClick: (AppMediaItem) -> Unit,
    onPlayClick: ((AppMediaItem, QueueOption, Boolean) -> Unit),
    onLibraryItemClick: (MediaType?) -> Unit,
    playlistActions: ActionsViewModel.PlaylistActions,
    libraryActions: ActionsViewModel.LibraryActions,
    progressActions: ActionsViewModel.ProgressActions? = null,
    navigateTo: (NavScreen) -> Unit,
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
                } == true
            }
        } else {
            emptyList()
        }
    }

    val listState = rememberLazyListState()

    Column(modifier = modifier.fillMaxSize()) {

        LandingPageTopBar(navigateTo)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(bottom = 16.dp)
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
private fun LandingPageTopBar(
    navigateTo: (NavScreen) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.height(64.dp).fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.Start)
        ) {
            Image(
                painter = painterResource(Res.drawable.mass),
                contentDescription = "Music Assistant Logo",
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = "MASS",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = { navigateTo(NavScreen.Settings) }) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                )
            }
        }
    }
}

// --- Common UI Components ---

@Composable
fun LibraryRow(
    onLibraryItemClick: (MediaType?) -> Unit
) {
    val libraryItems = remember {
        listOf(
            LibraryItem("Artists", Icons.Default.Mic, MediaType.ARTIST),
            LibraryItem("Albums", Icons.Default.Album, MediaType.ALBUM),
            LibraryItem("Tracks", Icons.Default.MusicNote, MediaType.TRACK),
            LibraryItem(
                "Playlists",
                Icons.AutoMirrored.Filled.FeaturedPlayList,
                MediaType.PLAYLIST
            ),
            LibraryItem("Audiobooks", Icons.AutoMirrored.Filled.MenuBook, MediaType.AUDIOBOOK),
            LibraryItem("Podcasts", Icons.Default.Podcasts, MediaType.PODCAST),
            LibraryItem("Radio", Icons.Default.Radio, MediaType.RADIO),
            LibraryItem("Global search", Icons.Default.Search, null),
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            libraryItems.chunked(3).forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    rowItems.forEach { item ->
                        LibraryItemCard(
                            modifier = Modifier.weight(1f),
                            name = item.name,
                            icon = item.icon,
                            onClick = { onLibraryItemClick(item.type) }
                        )
                    }
                    // Fill remaining columns with spacers to keep grid alignment
                    repeat(3 - rowItems.size) {
                        Spacer(modifier = Modifier.weight(1f).padding(8.dp))
                    }
                }
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
                .height(64.dp)
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
    val isHomogenous = remember(mediaItems) {
        mediaItems.all { it::class == mediaItems.firstOrNull()?.let { first -> first::class } }
    }
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
                        is AppMediaItem.RadioStation -> "${item::class.simpleName}_${item.itemId}"

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
                        else -> "Unknown"
                    }
                }
            ) { item ->
                when (item) {
                    is AppMediaItem.Artist -> ArtistWithMenu(
                        item = item,
                        showSubtitle = !isHomogenous,
                        serverUrl = serverUrl,
                        onNavigateClick = onNavigateClick,
                        onPlayOption = onPlayClick,
                        libraryActions = libraryActions,
                        providerIconFetcher = providerIconFetcher
                    )

                    is AppMediaItem.Album -> AlbumWithMenu(
                        item = item,
                        showSubtitle = !isHomogenous,
                        serverUrl = serverUrl,
                        onNavigateClick = onNavigateClick,
                        onPlayOption = onPlayClick,
                        libraryActions = libraryActions,
                        providerIconFetcher = providerIconFetcher
                    )

                    is AppMediaItem.Playlist -> PlaylistWithMenu(
                        item = item,
                        showSubtitle = !isHomogenous,
                        serverUrl = serverUrl,
                        onNavigateClick = onNavigateClick,
                        onPlayOption = onPlayClick,
                        libraryActions = libraryActions,
                        providerIconFetcher = providerIconFetcher
                    )

                    is AppMediaItem.Podcast -> PodcastWithMenu(
                        item = item,
                        showSubtitle = !isHomogenous,
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
                        showSubtitle = !isHomogenous,
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
    else -> null
}


