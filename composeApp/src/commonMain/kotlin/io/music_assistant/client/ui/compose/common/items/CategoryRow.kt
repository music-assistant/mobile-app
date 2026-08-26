package io.music_assistant.client.ui.compose.common.items

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
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
import io.music_assistant.client.ui.compose.common.DataState
import io.music_assistant.client.ui.compose.common.DisplayString
import io.music_assistant.client.ui.compose.common.toDisplayString
import io.music_assistant.client.ui.compose.item.ItemList
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.cd_view_all
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun <T, U> CategoryRow(
    data: DataState<T>,
    itemCategoryProvider: (T) -> ItemCategory<U>,
    onNavigateClick: (AppMediaItem) -> Unit,
    onNavigateToList: (String, ItemList) -> Unit = { _, _ -> },
    onOptionSelected: (U) -> Unit = {},
    onPlayClick: PlayHandler<AppMediaItem>,
    playlistActions: PlaylistActions,
    libraryActions: LibraryActions,
    progressActions: ProgressActions? = null,
    providerIconFetcher: (@Composable (Modifier, String) -> Unit),
    bottomFocusRequester: FocusRequester? = null,
) {
    if (data is DataState.Data) {
        CategoryRow(
            itemCategory = itemCategoryProvider(data.data),
            onNavigateClick = onNavigateClick,
            onNavigateToList = onNavigateToList,
            onOptionSelected = onOptionSelected,
            onPlayClick = onPlayClick,
            playlistActions = playlistActions,
            libraryActions = libraryActions,
            progressActions = progressActions,
            providerIconFetcher = providerIconFetcher,
            bottomFocusRequester = bottomFocusRequester,
        )
    } else if (data is DataState.Loading) {
        val placeholderWidth = 140.dp
        val placeholderColor by rememberInfiniteTransition().animateColor(
            initialValue = Color.Gray.copy(alpha = 0.1f),
            targetValue = Color.Gray.copy(alpha = 0.3f),
            animationSpec = infiniteRepeatable(
                animation = tween(2000),
                repeatMode = RepeatMode.Reverse,
            ),
        )

        RowWithTitle(
            title = {
                val height = with(LocalDensity.current) {
                    LocalTextStyle.current.fontSize.toDp()
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(placeholderColor)
                        .size(width = placeholderWidth, height = height),
                )
            },
            actions = {},
            bottomFocusRequester = bottomFocusRequester,
            row = {
                repeat(PLACEHOLDER_ITEMS) {
                    item {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(placeholderColor)
                                .size(width = placeholderWidth, height = 152.dp),
                        )
                    }
                }
            },
        )
    }
}

@Composable
fun <T> CategoryRow(
    itemCategory: ItemCategory<T>,
    onNavigateClick: (AppMediaItem) -> Unit,
    onNavigateToList: (String, ItemList) -> Unit = { _, _ -> },
    onOptionSelected: (T) -> Unit = {},
    onPlayClick: PlayHandler<AppMediaItem>,
    playlistActions: PlaylistActions,
    libraryActions: LibraryActions,
    progressActions: ProgressActions? = null,
    providerIconFetcher: (@Composable (Modifier, String) -> Unit),
    bottomFocusRequester: FocusRequester? = null,
) {
    if (itemCategory.items.isEmpty() && itemCategory.filter == null) {
        return
    }

    val title = itemCategory.title.string()
    CategoryRow(
        title = title,
        actions = {
            if (itemCategory.filter != null) {
                FilterSelector(
                    label = itemCategory.filter.label.string(),
                    rowTitle = title,
                    onOptionSelected = onOptionSelected,
                    options = itemCategory.filter.options,
                    optionLabels = { itemCategory.filter.labelTransform(it).string() },
                    contentDescriptionResource = itemCategory.filter.contentDescription,
                )
            }

            if (itemCategory.list != null) {
                ViewAllButton(
                    rowTitle = title,
                    onNavigateToList = onNavigateToList,
                    itemList = itemCategory.list,
                )
            }
        },
        onNavigateClick = onNavigateClick,
        onPlayClick = onPlayClick,
        mediaItems = itemCategory.items,
        playlistActions = playlistActions,
        libraryActions = libraryActions,
        progressActions = progressActions,
        providerIconFetcher = providerIconFetcher,
        rowTag = itemCategory.tag,
        bottomFocusRequester = bottomFocusRequester,
    )
}

@Composable
fun CategoryRow(
    title: String,
    actions: @Composable () -> Unit = {},
    onNavigateClick: (AppMediaItem) -> Unit,
    onPlayClick: PlayHandler<AppMediaItem>,
    mediaItems: List<AppMediaItem>,
    playlistActions: PlaylistActions,
    libraryActions: LibraryActions,
    progressActions: ProgressActions? = null,
    providerIconFetcher: (@Composable (Modifier, String) -> Unit),
    rowTag: String? = null,
    // Android TV: set by a caller only on its last visible row, to route D-pad DOWN out of this
    // row's LazyRow to a fixed element below the scrolling content (e.g. the persistent
    // mini-player), since that element isn't a normal sibling default focus search can reach.
    bottomFocusRequester: FocusRequester? = null,
    // Android TV: set by a caller that needs to land D-pad focus on this row's first item
    // programmatically (e.g. once async search results appear, when nothing upstream is a
    // reliable relative-focus anchor -- see SearchScreen.kt). Only applied to the first item.
    firstItemFocusRequester: FocusRequester? = null,
) {
    val modifier = if (rowTag != null) {
        Modifier.testTag(rowTag)
    } else {
        Modifier
    }

    // Recommendation rows are server-curated and can repeat canonical item
    // Key by occurrence to avoid Compose's duplicate-key crash
    val itemKeys = remember(mediaItems) { mediaItems.lazyListOccurrenceKeys() }

    RowWithTitle(
        modifier = modifier,
        title = {
            Text(title)
        },
        actions = actions,
        bottomFocusRequester = bottomFocusRequester,
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
        ) { index, item ->
            val itemFocusRequester = if (index == 0) firstItemFocusRequester else null
            when (item) {
                is Artist -> ArtistWithMenu(
                    item = item,
                    onNavigateClick = onNavigateClick,
                    onPlayOption = onPlayClick,
                    libraryActions = libraryActions,
                    providerIconFetcher = providerIconFetcher,
                    firstItemFocusRequester = itemFocusRequester,
                )

                is Album -> AlbumWithMenu(
                    item = item,
                    onNavigateClick = onNavigateClick,
                    onPlayOption = onPlayClick,
                    playlistActions = playlistActions,
                    libraryActions = libraryActions,
                    providerIconFetcher = providerIconFetcher,
                    firstItemFocusRequester = itemFocusRequester,
                )

                is Playlist -> PlaylistWithMenu(
                    item = item,
                    onNavigateClick = onNavigateClick,
                    onPlayOption = onPlayClick,
                    libraryActions = libraryActions,
                    providerIconFetcher = providerIconFetcher,
                    firstItemFocusRequester = itemFocusRequester,
                )

                is Podcast -> PodcastWithMenu(
                    item = item,
                    onNavigateClick = onNavigateClick,
                    onPlayOption = onPlayClick,
                    libraryActions = libraryActions,
                    providerIconFetcher = providerIconFetcher,
                    firstItemFocusRequester = itemFocusRequester,
                )

                is Track -> TrackWithMenu(
                    item = item,
                    onPlayOption = onPlayClick,
                    playlistActions = playlistActions,
                    libraryActions = libraryActions,
                    providerIconFetcher = providerIconFetcher,
                    firstItemFocusRequester = itemFocusRequester,
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
                    firstItemFocusRequester = itemFocusRequester,
                )

                is RadioStation -> RadioWithMenu(
                    item = item,
                    onPlayOption = onPlayClick,
                    playlistActions = playlistActions,
                    libraryActions = libraryActions,
                    providerIconFetcher = providerIconFetcher,
                    firstItemFocusRequester = itemFocusRequester,
                )

                is Genre -> GenreWithMenu(
                    item = item,
                    onNavigateClick = onNavigateClick,
                    onPlayOption = onPlayClick,
                    libraryActions = libraryActions,
                    providerIconFetcher = providerIconFetcher,
                    firstItemFocusRequester = itemFocusRequester,
                )

                else -> {}
            }
        }
    }
}

@Composable
private fun RowWithTitle(
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
    actions: @Composable () -> Unit,
    bottomFocusRequester: FocusRequester? = null,
    row: LazyListScope.() -> Unit,
) {
    Column(modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProvideTextStyle(
                value = MaterialTheme.typography.titleMedium,
            ) {
                title()
            }

            Row {
                actions()
            }
        }

        val rowListState = rememberLazyListState()
        LazyRow(
            modifier = if (bottomFocusRequester != null) {
                // Android TV: this is a single horizontal row, so DOWN never has an in-row
                // meaning — safe to unconditionally redirect it. onPreviewKeyEvent intercepts
                // the key regardless of which card inside currently holds focus; a plain
                // FocusProperties.down on this container would only apply if the container
                // itself were focused, not a focused descendant card.
                Modifier.onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                        bottomFocusRequester.requestFocus()
                    } else {
                        false
                    }
                }
            } else {
                Modifier
            },
            state = rowListState,
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            row()
        }
    }
}

data class ItemCategory<T>(
    val id: String,
    val title: DisplayString,
    val items: List<AppMediaItem>,
    val list: ItemList? = null,
    val filter: Filter<T>? = null,
    val lazyListKey: String = id,
    val tag: String? = null,
) {
    data class Filter<T>(
        val label: DisplayString,
        val options: List<T>,
        val labelTransform: (T) -> DisplayString,
        val contentDescription: StringResource? = null,
    )
}

@Composable
private fun ViewAllButton(
    rowTitle: String,
    onNavigateToList: (String, ItemList) -> Unit,
    itemList: ItemList,
) {
    val viewAllContentDescription = stringResource(Res.string.cd_view_all, rowTitle)
    TextButton(
        modifier = Modifier.semantics {
            contentDescription = viewAllContentDescription
        },
        onClick = {
            onNavigateToList(rowTitle, itemList)
        },
    ) {
        Text("View all")
    }
}

@Composable
private fun <T> FilterSelector(
    label: String,
    rowTitle: String,
    onOptionSelected: (T) -> Unit,
    options: List<T>,
    optionLabels: @Composable (T) -> String,
    contentDescriptionResource: StringResource? = null,
) {
    Box {
        var expanded by remember { mutableStateOf(false) }

        val chipContentDescription = if (contentDescriptionResource != null) {
            stringResource(
                contentDescriptionResource,
                rowTitle,
                label,
            )
        } else {
            null
        }

        FilterChip(
            modifier = Modifier
                .semantics {
                    if (chipContentDescription != null) {
                        contentDescription = chipContentDescription
                    }
                },
            selected = true,
            onClick = { expanded = true },
            label = {
                Text(label)
            },
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach {
                DropdownMenuItem(
                    text = { Text(optionLabels(it)) },
                    onClick = {
                        expanded = false
                        onOptionSelected(it)
                    },
                )
            }
        }
    }
}

private const val PLACEHOLDER_ITEMS = 10

@Preview
@Composable
fun PreviewCategoryRowEmpty() {
    CategoryRow(
        title = "Category",
        onNavigateClick = {},
        onPlayClick = { _, _, _, _ -> },
        mediaItems = emptyList(),
        playlistActions = object : PlaylistActions {
            override suspend fun getEditablePlaylists(): List<Playlist> {
                TODO("Not yet implemented")
            }

            override fun addToPlaylist(itemUri: String?, playlist: Playlist) {
                TODO("Not yet implemented")
            }

            override suspend fun createPlaylist(name: String): Playlist? {
                TODO("Not yet implemented")
            }
        },
        libraryActions = object : LibraryActions {
            override fun onLibraryClick(item: AppMediaItem) {
                TODO("Not yet implemented")
            }

            override fun onFavoriteClick(item: AppMediaItem) {
                TODO("Not yet implemented")
            }
        },
        providerIconFetcher = { _, _ -> },
    )
}

@Preview
@Composable
fun PreviewCategoryLoading() {
    CategoryRow(
        data = DataState.Loading<List<AppMediaItem>>(),
        itemCategoryProvider = {
            ItemCategory<Nothing>(
                id = "blah",
                title = "blah".toDisplayString(),
                items = it,
            )
        },
        onNavigateClick = {},
        onPlayClick = { _, _, _, _ -> },
        playlistActions = object : PlaylistActions {
            override suspend fun getEditablePlaylists(): List<Playlist> {
                TODO("Not yet implemented")
            }

            override fun addToPlaylist(itemUri: String?, playlist: Playlist) {
                TODO("Not yet implemented")
            }

            override suspend fun createPlaylist(name: String): Playlist? {
                TODO("Not yet implemented")
            }
        },
        libraryActions = object : LibraryActions {
            override fun onLibraryClick(item: AppMediaItem) {
                TODO("Not yet implemented")
            }

            override fun onFavoriteClick(item: AppMediaItem) {
                TODO("Not yet implemented")
            }
        },
        providerIconFetcher = { _, _ -> },
    )
}

@Preview
@Composable
fun PreviewCategoryNoData() {
    CategoryRow(
        data = DataState.NoData<List<AppMediaItem>>(),
        itemCategoryProvider = {
            ItemCategory<Nothing>(
                id = "blah",
                title = "blah".toDisplayString(),
                items = it,
            )
        },
        onNavigateClick = {},
        onPlayClick = { _, _, _, _ -> },
        playlistActions = object : PlaylistActions {
            override suspend fun getEditablePlaylists(): List<Playlist> {
                TODO("Not yet implemented")
            }

            override fun addToPlaylist(itemUri: String?, playlist: Playlist) {
                TODO("Not yet implemented")
            }

            override suspend fun createPlaylist(name: String): Playlist? {
                TODO("Not yet implemented")
            }
        },
        libraryActions = object : LibraryActions {
            override fun onLibraryClick(item: AppMediaItem) {
                TODO("Not yet implemented")
            }

            override fun onFavoriteClick(item: AppMediaItem) {
                TODO("Not yet implemented")
            }
        },
        providerIconFetcher = { _, _ -> },
    )
}
