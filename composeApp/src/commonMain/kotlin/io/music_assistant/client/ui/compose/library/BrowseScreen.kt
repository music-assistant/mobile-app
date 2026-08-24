package io.music_assistant.client.ui.compose.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.music_assistant.client.data.model.client.ClickContext
import io.music_assistant.client.data.model.client.SortField
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.RecommendationFolder
import io.music_assistant.client.settings.SettingsRepository
import io.music_assistant.client.settings.ViewMode
import io.music_assistant.client.ui.compose.common.DataState
import io.music_assistant.client.ui.compose.common.SortChip
import io.music_assistant.client.ui.compose.common.ToastHost
import io.music_assistant.client.ui.compose.common.items.ProvideClickActions
import io.music_assistant.client.ui.compose.common.rememberToastState
import io.music_assistant.client.ui.compose.common.viewmodel.ActionsViewModel
import io.music_assistant.client.ui.compose.nav.TopBarLayout
import io.music_assistant.client.ui.compose.nav.TwoRowTopAppBar
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.cd_toggle_view_mode
import musicassistantclient.composeapp.generated.resources.common_back
import musicassistantclient.composeapp.generated.resources.library_empty
import musicassistantclient.composeapp.generated.resources.library_error
import musicassistantclient.composeapp.generated.resources.nav_browse
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * Folder-style browser for one level of the server's `music/browse` tree. Folders drill down
 * (handled by [onNavigateClick] at the call site); playable/openable items reuse the shared
 * library item cards via [AdaptiveMediaGrid].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
    browseViewModel: BrowseViewModel,
    title: String?,
    contentPadding: PaddingValues,
    actionsViewModel: ActionsViewModel,
    onNavigateClick: (AppMediaItem) -> Unit,
    onBack: () -> Unit,
) {
    val toastState = rememberToastState()
    LaunchedEffect(Unit) {
        actionsViewModel.toasts.collect { toastState.showToast(it) }
    }

    val dataState by browseViewModel.state.collectAsStateWithLifecycle()

    val settingsRepository: SettingsRepository = koinInject()
    val sortOption by settingsRepository.browseSortOption.collectAsStateWithLifecycle()
    val viewMode by settingsRepository.browseViewMode.collectAsStateWithLifecycle()
    val favoriteBrowseFolders by
        settingsRepository.favoriteBrowseFolders.collectAsStateWithLifecycle()

    var folderForFavoriteMenu by remember {
        mutableStateOf<RecommendationFolder?>(null)
    }

    TopBarLayout(
        topBar = {
            TwoRowTopAppBar(
                title = { Text(title ?: stringResource(Res.string.nav_browse)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.common_back),
                        )
                    }
                },
                secondRow = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        SortChip(
                            currentSort = sortOption,
                            availableFields = listOf(
                                SortField.ORIGINAL,
                                SortField.NAME,
                            ),
                            onSortChanged = settingsRepository::setBrowseSortOption,
                        )

                        IconButton(
                            onClick = {
                                settingsRepository.setBrowseViewMode(viewMode.toggled())
                            },
                        ) {
                            Icon(
                                imageVector = when (viewMode) {
                                    ViewMode.LIST -> Icons.Default.GridView
                                    ViewMode.GRID -> Icons.AutoMirrored.Filled.ViewList
                                },
                                contentDescription =
                                    stringResource(Res.string.cd_toggle_view_mode),
                            )
                        }
                    }
                },
            )
        },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            ProvideClickActions(ClickContext.BROWSE) {
                when (val state = dataState) {
                    is DataState.Loading -> CenteredContent { CircularProgressIndicator() }

                    is DataState.Error -> CenteredMessage(
                        text = stringResource(Res.string.library_error),
                        color = MaterialTheme.colorScheme.error,
                    )

                    is DataState.NoData -> CenteredMessage(stringResource(Res.string.library_empty))

                    is DataState.Data,
                    is DataState.Stale,
                    -> {
                        val sourceItems = state.dataOrNull.orEmpty()

                        val items = when (sortOption.field) {
                            SortField.NAME -> {
                                val sorted = sourceItems.sortedWith(
                                    Comparator { a, b ->
                                        a.displayName.lowercase().compareTo(
                                            b.displayName.lowercase(),
                                        )
                                    },
                                )
                                if (sortOption.descending) sorted.reversed() else sorted
                            }

                            else -> {
                                if (sortOption.descending) {
                                    sourceItems.reversed()
                                } else {
                                    sourceItems
                                }
                            }
                        }

                        if (items.isEmpty()) {
                            CenteredMessage(stringResource(Res.string.library_empty))
                        } else {
                            AdaptiveMediaGrid(
                                modifier = Modifier.fillMaxSize(),
                                items = items,
                                isLoadingMore = false,
                                hasMore = false,
                                viewMode = viewMode,
                                onNavigateClick = onNavigateClick,
                                onFolderLongClick = { folder ->
                                    if (!folder.isParentLink && folder.path != null) {
                                        folderForFavoriteMenu = folder
                                    }
                                },
                                onPlayClick = { item, option, radio, _ ->
                                    browseViewModel.onPlayClick(item, option, radio)
                                },
                                playlistActions = actionsViewModel,
                                libraryActions = actionsViewModel,
                                progressActions = actionsViewModel,
                                contentPadding = contentPadding,
                            )
                        }
                    }
                }
            }

            folderForFavoriteMenu?.let { folder ->
                val path = folder.path

                if (path != null) {
                    val isFavorite =
                        favoriteBrowseFolders.any { it.path == path }

                    AlertDialog(
                        onDismissRequest = {
                            folderForFavoriteMenu = null
                        },
                        title = {
                            Text(folder.displayName)
                        },
                        text = {
                            Text(
                                if (isFavorite) {
                                    "Remove this folder from Favorites?"
                                } else {
                                    "Add this folder to Favorites?"
                                },
                            )
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    settingsRepository.setBrowseFolderFavorite(
                                        SettingsRepository.FavoriteBrowseFolder(
                                            path = path,
                                            itemId = folder.itemId,
                                            provider = folder.provider,
                                            name = folder.displayName,
                                            uri = folder.uri,
                                        ),
                                        favorite = !isFavorite,
                                    )
                                    folderForFavoriteMenu = null
                                },
                            ) {
                                Text(
                                    if (isFavorite) {
                                        "Unfavorite"
                                    } else {
                                        "Favorite"
                                    },
                                )
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = {
                                    folderForFavoriteMenu = null
                                },
                            ) {
                                Text("Cancel")
                            }
                        },
                    )
                }
            }

            ToastHost(
                toastState = toastState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 48.dp),
            )
        }
    }
}

@Composable
private fun CenteredContent(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun CenteredMessage(
    text: String,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    CenteredContent {
        Text(text = text, style = MaterialTheme.typography.titleMedium, color = color)
    }
}
