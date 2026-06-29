package io.music_assistant.client.ui.compose.common.items

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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

data class ItemCategory(
    val id: String,
    val title: DisplayString,
    val items: List<AppMediaItem>,
    val lazyListKey: String,
    val tag: String? = null,
)

@Composable
fun CategoryRow(
    itemCategory: ItemCategory,
    onNavigateClick: (AppMediaItem) -> Unit,
    onPlayClick: PlayHandler<AppMediaItem>,
    playlistActions: PlaylistActions,
    libraryActions: LibraryActions,
    progressActions: ProgressActions? = null,
    providerIconFetcher: (@Composable (Modifier, String) -> Unit),
) {
    CategoryRow(
        title = itemCategory.title.string(),
        onNavigateClick = onNavigateClick,
        onPlayClick = onPlayClick,
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
    onNavigateClick: (AppMediaItem) -> Unit,
    onPlayClick: PlayHandler<AppMediaItem>,
    mediaItems: List<AppMediaItem>,
    playlistActions: PlaylistActions,
    libraryActions: LibraryActions,
    progressActions: ProgressActions? = null,
    providerIconFetcher: (@Composable (Modifier, String) -> Unit),
    rowTag: String? = null,
) {
    val rowListState = rememberLazyListState()

    Column {
        Text(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            text = title,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        val modifier = if (rowTag != null) {
            Modifier.testTag(rowTag)
        } else {
            Modifier
        }

        // Recommendation rows are server-curated and can repeat canonical item
        // Key by occurrence to avoid Compose's duplicate-key crash
        val itemKeys = remember(mediaItems) { mediaItems.lazyListOccurrenceKeys() }

        LazyRow(
            modifier = modifier,
            state = rowListState,
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            itemsIndexed(
                items = mediaItems,
                key = { index, _ -> itemKeys[index] },
                contentType = { _, item ->
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
            ) { _, item ->
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
