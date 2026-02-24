@file:OptIn(ExperimentalMaterial3Api::class)

package io.music_assistant.client.ui.compose.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import io.music_assistant.client.data.model.client.AppMediaItem
import io.music_assistant.client.data.model.server.QueueOption
import io.music_assistant.client.ui.compose.common.DataState
import io.music_assistant.client.ui.compose.common.providers.ProviderIcon
import io.music_assistant.client.ui.compose.common.rememberToastState
import io.music_assistant.client.ui.compose.common.viewmodel.ActionsViewModel
import io.music_assistant.client.ui.compose.home.nav.HomeNavScreen
import io.music_assistant.client.ui.compose.home.nav.rememberHomeNavBackStack
import io.music_assistant.client.ui.compose.item.ItemDetailsScreen
import io.music_assistant.client.ui.compose.library.LibraryScreen
import io.music_assistant.client.ui.compose.nav.BackHandler
import io.music_assistant.client.ui.compose.nav.NavScreen
import io.music_assistant.client.ui.compose.search.SearchScreen
import io.music_assistant.client.utils.SessionState
import kotlinx.coroutines.flow.collectLatest
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    viewModel: HomeScreenViewModel = koinViewModel(),
    actionsViewModel: ActionsViewModel = koinViewModel(),
    navigateTo: (NavScreen) -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val toastState = rememberToastState()

    LaunchedEffect(Unit) {
        viewModel.links.collectLatest { url -> uriHandler.openUri(url) }
    }

    // Collect toasts
    LaunchedEffect(Unit) {
        actionsViewModel.toasts.collect { toast ->
            toastState.showToast(toast)
        }
    }

    var showPlayersView by remember { mutableStateOf(false) }
    var isQueueExpanded by remember { mutableStateOf(false) }

    val recommendationsState = viewModel.recommendationsState.collectAsStateWithLifecycle()
    val serverUrl by viewModel.serverUrl.collectAsStateWithLifecycle()
    val playersState by viewModel.playersState.collectAsStateWithLifecycle()
    // Single pager state used across all views
    val data = playersState as? HomeScreenViewModel.PlayersState.Data
    val playerPagerState = rememberPagerState(
        initialPage = data?.selectedPlayerIndex ?: 0,
        pageCount = { data?.playerData?.size ?: 0 }
    )

    // Nested navigation backstack - hoisted to survive player view transitions
    val homeBackStack = rememberHomeNavBackStack()

    // Handle back when player view is shown
    BackHandler(enabled = showPlayersView) {
        showPlayersView = false
    }

    // Bidirectional pager <-> selection sync
    // Selection→pager runs first (data layer priority), then pager→selection watches user swipes
    LaunchedEffect(playerPagerState, playersState) {
        val currentData = playersState as? HomeScreenViewModel.PlayersState.Data
            ?: return@LaunchedEffect
        val target = currentData.selectedPlayerIndex ?: return@LaunchedEffect
        if (playerPagerState.currentPage != target) {
            playerPagerState.scrollToPage(target)
        }
        snapshotFlow { playerPagerState.currentPage }.collect { currentPage ->
            currentData.playerData.getOrNull(currentPage)?.let { playerData ->
                viewModel.selectPlayer(playerData.player)
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) { paddingValues ->
        val connectionState = recommendationsState.value.connectionState
        val dataState = recommendationsState.value.recommendations
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            // Simple slide transition between main screen and big player
            AnimatedContent(
                targetState = showPlayersView,
                transitionSpec = {
                    slideInVertically(
                        initialOffsetY = { if (targetState) it else -it },
                        animationSpec = tween(300)
                    ) togetherWith slideOutVertically(
                        targetOffsetY = { if (targetState) -it else it },
                        animationSpec = tween(300)
                    )
                },
                label = "player_transition"
            ) { isPlayersViewShown ->
                if (!isPlayersViewShown) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                    ) {
                        HomeContent(
                            modifier = Modifier.weight(1f),
                            homeBackStack = homeBackStack,
                            connectionState = connectionState,
                            dataState = dataState,
                            serverUrl = serverUrl,
                            onPlayClick = viewModel::onPlayClick,
                            playlistActions = ActionsViewModel.PlaylistActions(
                                onLoadPlaylists = actionsViewModel::getEditablePlaylists,
                                onAddToPlaylist = actionsViewModel::addToPlaylist
                            ),
                            libraryActions = ActionsViewModel.LibraryActions(
                                onLibraryClick = actionsViewModel::onLibraryClick,
                                onFavoriteClick = actionsViewModel::onFavoriteClick
                            ),
                            progressActions = ActionsViewModel.ProgressActions(
                                onMarkPlayed = actionsViewModel::onMarkPlayed,
                                onMarkUnplayed = actionsViewModel::onMarkUnplayed
                            ),
                            navigateTo = navigateTo
                        ) { modifier, provider ->
                            actionsViewModel.getProviderIcon(provider)
                                ?.let { ProviderIcon(modifier, it) }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentHeight()
                                .defaultMinSize(minHeight = 100.dp)
                                .clickable { showPlayersView = true }
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                            contentAlignment = Alignment.Center
                        ) {
                            when (val state = playersState) {
                                is HomeScreenViewModel.PlayersState.Loading -> Text(
                                    text = "Loading players...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )

                                is HomeScreenViewModel.PlayersState.Data -> {
                                    if (state.playerData.isEmpty()) {
                                        Text(
                                            text = "No players available",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    } else {
                                        PlayersPager(
                                            modifier = Modifier.fillMaxWidth().wrapContentHeight(),
                                            playerPagerState = playerPagerState,
                                            playersState = state,
                                            serverUrl = serverUrl,
                                            simplePlayerAction = { playerId, action ->
                                                viewModel.playerAction(playerId, action)
                                            },
                                            playerAction = { playerData, action ->
                                                viewModel.playerAction(playerData, action)
                                            },
                                            onPlayersRefreshClick = viewModel::refreshPlayers,
                                            onFavoriteClick = actionsViewModel::onFavoriteClick,
                                            showQueue = false,
                                            isQueueExpanded = isQueueExpanded,
                                            onQueueExpandedSwitch = {
                                                isQueueExpanded = !isQueueExpanded
                                            },
                                            onGoToLibrary = { showPlayersView = false },
                                            onItemMoved = null,
                                            queueAction = { action -> viewModel.queueAction(action) },
                                            settingsAction = viewModel::openPlayerSettings,
                                            dspSettingsAction = viewModel::openPlayerDspSettings,
                                        )
                                    }
                                }

                                else -> Text(
                                    text = "No players available",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    ) {
                        // Close button
                        IconButton(
                            onClick = { showPlayersView = false },
                            modifier = Modifier.fillMaxWidth().height(36.dp)
                                .align(Alignment.CenterHorizontally)
                        ) {
                            Icon(
                                Icons.Default.ExpandMore,
                                "Collapse",
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            when (val state = playersState) {
                                is HomeScreenViewModel.PlayersState.Loading -> Text(
                                    text = "Loading players...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )

                                is HomeScreenViewModel.PlayersState.Data -> {
                                    if (state.playerData.isEmpty()) {
                                        Text(
                                            text = "No players available",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    } else {
                                        PlayersPager(
                                            modifier = Modifier.fillMaxSize(),
                                            playerPagerState = playerPagerState,
                                            playersState = state,
                                            serverUrl = serverUrl,
                                            simplePlayerAction = { playerId, action ->
                                                viewModel.playerAction(playerId, action)
                                            },
                                            playerAction = { playerData, action ->
                                                viewModel.playerAction(playerData, action)
                                            },
                                            onPlayersRefreshClick = viewModel::refreshPlayers,
                                            onFavoriteClick = actionsViewModel::onFavoriteClick,
                                            showQueue = true,
                                            isQueueExpanded = isQueueExpanded,
                                            onQueueExpandedSwitch = {
                                                isQueueExpanded = !isQueueExpanded
                                            },
                                            onGoToLibrary = { showPlayersView = false },
                                            onItemMoved = { indexShift ->
                                                val currentPlayer =
                                                    state.playerData[playerPagerState.currentPage].player
                                                val newIndex =
                                                    (playerPagerState.currentPage + indexShift).coerceIn(
                                                        0,
                                                        state.playerData.size - 1
                                                    )
                                                val newPlayers =
                                                    state.playerData.map { it.player.id }
                                                        .toMutableList()
                                                        .apply {
                                                            add(
                                                                newIndex,
                                                                removeAt(playerPagerState.currentPage)
                                                            )
                                                        }
                                                viewModel.selectPlayer(currentPlayer)
                                                viewModel.onPlayersSortChanged(newPlayers)
                                            },
                                            queueAction = { action -> viewModel.queueAction(action) },
                                            settingsAction = viewModel::openPlayerSettings,
                                            dspSettingsAction = viewModel::openPlayerDspSettings,
                                        )
                                    }
                                }

                                else -> Text(
                                    text = "No players available",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeContent(
    modifier: Modifier = Modifier,
    homeBackStack: NavBackStack<*>,
    connectionState: SessionState,
    dataState: DataState<List<AppMediaItem.RecommendationFolder>>,
    serverUrl: String?,
    onPlayClick: (AppMediaItem, QueueOption, Boolean) -> Unit,
    playlistActions: ActionsViewModel.PlaylistActions,
    libraryActions: ActionsViewModel.LibraryActions,
    progressActions: ActionsViewModel.ProgressActions,
    navigateTo: (NavScreen) -> Unit,
    providerIconFetcher: (@Composable (Modifier, String) -> Unit)
) {
    @Suppress("UNCHECKED_CAST")
    val typedBackStack = homeBackStack as NavBackStack<HomeNavScreen>

//    val homeBottomSheetStrategy = remember { BottomSheetSceneStrategy<NavKey>() }
//    val homeDialogStrategy = remember { DialogSceneStrategy<NavKey>() }
    val saveableStateHolderForHome = rememberSaveableStateHolder()

    // Handle back when library is open
    BackHandler(enabled = typedBackStack.last() !is HomeNavScreen.Landing) {
        typedBackStack.removeLastOrNull()
    }

    NavDisplay(
        modifier = modifier,
        backStack = typedBackStack,
        onBack = { typedBackStack.removeLastOrNull() },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(saveableStateHolderForHome)
        ),
        entryProvider = entryProvider {
            entry<HomeNavScreen.Landing> {
                LandingPage(
                    modifier = Modifier.fillMaxSize(),
                    connectionState = connectionState,
                    dataState = dataState,
                    serverUrl = serverUrl,
                    onNavigateClick = { item ->
                        when (item) {
                            is AppMediaItem.Artist,
                            is AppMediaItem.Album,
                            is AppMediaItem.Playlist,
                            is AppMediaItem.Podcast,
                            is AppMediaItem.Audiobook -> {
                                typedBackStack.add(
                                    HomeNavScreen.ItemDetails(
                                        itemId = item.itemId,
                                        mediaType = item.mediaType,
                                        providerId = item.provider
                                    )
                                )
                            }

                            else -> Unit
                        }
                    },
                    onPlayClick = onPlayClick,
                    onLibraryItemClick = { type ->
                        if (type == null) {
                            typedBackStack.add(HomeNavScreen.Search)
                        } else {
                            typedBackStack.add(HomeNavScreen.Library(type))
                        }
                    },
                    playlistActions = playlistActions,
                    libraryActions = libraryActions,
                    progressActions = progressActions,
                    navigateTo = navigateTo,
                    providerIconFetcher = providerIconFetcher
                )
            }

            entry<HomeNavScreen.Library> {
                LibraryScreen(
                    initialTabType = it.type,
                    onBack = { typedBackStack.removeLastOrNull() },
                    onNavigateClick = { item ->
                        when (item) {
                            is AppMediaItem.Artist,
                            is AppMediaItem.Album,
                            is AppMediaItem.Playlist,
                            is AppMediaItem.Podcast,
                            is AppMediaItem.Audiobook -> {
                                typedBackStack.add(
                                    HomeNavScreen.ItemDetails(
                                        itemId = item.itemId,
                                        mediaType = item.mediaType,
                                        providerId = item.provider
                                    )
                                )
                            }

                            else -> Unit
                        }
                    }
                )
            }

            entry<HomeNavScreen.ItemDetails> {
                ItemDetailsScreen(
                    itemId = it.itemId,
                    mediaType = it.mediaType,
                    providerId = it.providerId,
                    onBack = { typedBackStack.removeLastOrNull() },
                    onNavigateToItem = { itemId, mediaType, providerId ->
                        typedBackStack.add(
                            HomeNavScreen.ItemDetails(
                                itemId = itemId,
                                mediaType = mediaType,
                                providerId = providerId
                            )
                        )
                    }
                )
            }

            entry<HomeNavScreen.Search> {
                SearchScreen(
                    onBack = { typedBackStack.removeLastOrNull() },
                    onNavigateToItem = { itemId, mediaType, providerId ->
                        typedBackStack.add(
                            HomeNavScreen.ItemDetails(
                                itemId = itemId,
                                mediaType = mediaType,
                                providerId = providerId
                            )
                        )
                    }
                )
            }
        }
    )
}
