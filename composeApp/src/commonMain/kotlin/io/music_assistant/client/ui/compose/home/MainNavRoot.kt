@file:OptIn(ExperimentalMaterial3Api::class)

package io.music_assistant.client.ui.compose.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import io.music_assistant.client.data.model.client.AppMediaItem
import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.ui.compose.common.DataState
import io.music_assistant.client.ui.compose.common.action.PlayerAction
import io.music_assistant.client.ui.compose.common.action.QueueAction
import io.music_assistant.client.ui.compose.common.providers.ProviderIcon
import io.music_assistant.client.ui.compose.common.rememberToastState
import io.music_assistant.client.ui.compose.common.viewmodel.ActionsViewModel
import io.music_assistant.client.ui.compose.home.nav.MainNav
import io.music_assistant.client.ui.compose.home.nav.rememberMainNavBackStack
import io.music_assistant.client.ui.compose.home.players.PlayersPager
import io.music_assistant.client.ui.compose.home.players.collapsedPlayerHeight
import io.music_assistant.client.ui.compose.item.ItemDetailsScreen
import io.music_assistant.client.ui.compose.library.LibraryScreen
import io.music_assistant.client.ui.compose.nav.AdaptiveNavigationScaffold
import io.music_assistant.client.ui.compose.nav.MultiBackStack
import io.music_assistant.client.ui.compose.nav.NavigationItem
import io.music_assistant.client.ui.compose.nav.createNavigationItem
import io.music_assistant.client.ui.compose.search.SearchScreen
import io.music_assistant.client.utils.SessionState
import io.music_assistant.client.utils.WindowClass
import kotlinx.coroutines.flow.collectLatest
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MainNavigationRoot(
    viewModel: HomeScreenViewModel = koinViewModel(),
    actionsViewModel: ActionsViewModel = koinViewModel(),
    goToSettings: () -> Unit,
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

    val recommendationsState = viewModel.recommendationsState.collectAsStateWithLifecycle()
    val serverUrl by viewModel.serverUrl.collectAsStateWithLifecycle()
    val playersState by viewModel.playersState.collectAsStateWithLifecycle()
    // Single pager state used across all views
    val data = playersState as? HomeScreenViewModel.PlayersState.Data
    val playerPagerState = rememberPagerState(
        initialPage = data?.selectedPlayerIndex ?: 0,
        pageCount = { data?.playerData?.size ?: 0 }
    )

    // Bidirectional pager <-> selection sync
    // Selection→pager runs first (data layer priority), then pager→selection watches user swipes
    LaunchedEffect(playerPagerState, playersState) {
        val currentData = playersState as? HomeScreenViewModel.PlayersState.Data
            ?: return@LaunchedEffect
        val target = currentData.selectedPlayerIndex ?: return@LaunchedEffect
        if (!playerPagerState.isScrollInProgress) {
            playerPagerState.animateScrollToPage(target)
        }

        snapshotFlow { playerPagerState.settledPage }.collect { currentPage ->
            currentData.playerData.getOrNull(currentPage)?.let { playerData ->
                viewModel.selectPlayer(playerData.player)
            }
        }
    }


    val connectionState = recommendationsState.value.connectionState
    val dataState = recommendationsState.value.recommendations

    var playerExpanded by remember { mutableStateOf(false) }

    val playlistActions = remember(actionsViewModel) {
        ActionsViewModel.PlaylistActions(
            onLoadPlaylists = actionsViewModel::getEditablePlaylists,
            onAddToPlaylist = actionsViewModel::addToPlaylist
        )
    }
    val libraryActions = remember(actionsViewModel) {
        ActionsViewModel.LibraryActions(
            onLibraryClick = actionsViewModel::onLibraryClick,
            onFavoriteClick = actionsViewModel::onFavoriteClick
        )
    }
    val progressActions = remember(actionsViewModel) {
        ActionsViewModel.ProgressActions(
            onMarkPlayed = actionsViewModel::onMarkPlayed,
            onMarkUnplayed = actionsViewModel::onMarkUnplayed
        )
    }

    val onExpandPlayer = remember { { expanded: Boolean -> playerExpanded = expanded } }

    val backStacks = listOf(
        rememberMainNavBackStack(MainNav.Landing),
        rememberMainNavBackStack(MainNav.Search)
    )
    val multiBackStack = remember { MultiBackStack(backStacks) }

    val navigationItems = listOf(
        multiBackStack.createNavigationItem(
            backStack = 0,
            icon = Icons.Default.Home,
            label = "Home"
        ),
        multiBackStack.createNavigationItem(
            backStack = 1,
            icon = Icons.Default.Search,
            label = "Search"
        ),
        NavigationItem(
            selected = false,
            onClick = { goToSettings() },
            Icons.Default.Settings,
            label = "Settings"
        )
    )

    AdaptiveNavigationScaffold(
        showNavBar = !playerExpanded,
        navigationItems = navigationItems
    ) { contentPadding ->
        val isExpandedScreen = WindowClass.isAtLeastExpanded()
        val bottomPadding = contentPadding.calculateBottomPadding()
        val floatingBarHeight = collapsedPlayerHeight(isExpandedScreen)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = bottomPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            NavDisplay(
                entries = multiBackStack.toEntries(
                    mainNavEntryProvider(
                        floatingBarHeight,
                        connectionState,
                        dataState,
                        serverUrl,
                        multiBackStack,
                        viewModel,
                        playlistActions,
                        libraryActions,
                        progressActions,
                        actionsViewModel
                    )
                ),
                onBack = {
                    multiBackStack.removeLastOrNull()
                }
            )
        }

        FloatingBar(
            bottomPadding = bottomPadding,
            expanded = playerExpanded,
            onExpand = onExpandPlayer
        ) { expanded, contentPadding ->
            Players(
                playerPagerState = playerPagerState,
                state = playersState,
                serverUrl = serverUrl,
                homeScreenViewModel = viewModel,
                actionsViewModel = actionsViewModel,
                expanded = expanded,
                onClose = { playerExpanded = false },
                isExpandedScreen = isExpandedScreen,
                contentPadding = contentPadding
            )
        }
    }
}

@Composable
private fun mainNavEntryProvider(
    floatingBarHeight: Dp,
    connectionState: SessionState,
    dataState: DataState<List<AppMediaItem.RecommendationFolder>>,
    serverUrl: String?,
    multiBackStack: MultiBackStack,
    viewModel: HomeScreenViewModel,
    playlistActions: ActionsViewModel.PlaylistActions,
    libraryActions: ActionsViewModel.LibraryActions,
    progressActions: ActionsViewModel.ProgressActions,
    actionsViewModel: ActionsViewModel
): (NavKey) -> NavEntry<NavKey> {
    return entryProvider {
        entry<MainNav.Landing> {
            HomeScreen(
                contentPadding = PaddingValues(
                    bottom = floatingBarHeight + FloatingBarDefaults.padding
                ),
                connectionState = connectionState,
                dataState = dataState,
                serverUrl = serverUrl,
                onNavigateClick = { item ->
                    when (item) {
                        is AppMediaItem.Artist,
                        is AppMediaItem.Album,
                        is AppMediaItem.Playlist,
                        is AppMediaItem.Podcast,
                        is AppMediaItem.Audiobook,
                        is AppMediaItem.Genre -> {
                            multiBackStack.add(
                                MainNav.ItemDetails(
                                    itemId = item.itemId,
                                    mediaType = item.mediaType,
                                    providerId = item.provider
                                )
                            )
                        }

                        else -> Unit
                    }
                },
                onPlayClick = viewModel::onPlayClick,
                onLibraryItemClick = { type ->
                    multiBackStack.add(MainNav.Library(type))
                },
                playlistActions = playlistActions,
                libraryActions = libraryActions,
                progressActions = progressActions,
                providerIconFetcher = { modifier, provider ->
                    actionsViewModel.getProviderIcon(provider)
                        ?.let { ProviderIcon(modifier, it) }
                }
            )
        }

        entry<MainNav.Library> {
            LibraryScreen(
                contentPadding = PaddingValues(
                    bottom = floatingBarHeight + FloatingBarDefaults.padding
                ),
                initialTabType = it.type,
                onBack = { multiBackStack.removeLastOrNull() },
                onNavigateClick = { item ->
                    when (item) {
                        is AppMediaItem.Artist,
                        is AppMediaItem.Album,
                        is AppMediaItem.Playlist,
                        is AppMediaItem.Podcast,
                        is AppMediaItem.Audiobook,
                        is AppMediaItem.Genre -> {
                            multiBackStack.add(
                                MainNav.ItemDetails(
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

        entry<MainNav.ItemDetails> {
            ItemDetailsScreen(
                contentPadding = PaddingValues(
                    bottom = floatingBarHeight + FloatingBarDefaults.padding
                ),
                itemId = it.itemId,
                mediaType = it.mediaType,
                providerId = it.providerId,
                onBack = { multiBackStack.removeLastOrNull() },
                onNavigateToItem = { itemId, mediaType, providerId ->
                    multiBackStack.add(
                        MainNav.ItemDetails(
                            itemId = itemId,
                            mediaType = mediaType,
                            providerId = providerId
                        )
                    )
                }
            )
        }

        entry<MainNav.Search> {
            SearchScreen(
                onNavigateToItem = { itemId, mediaType, providerId ->
                    multiBackStack.add(
                        MainNav.ItemDetails(
                            itemId = itemId,
                            mediaType = mediaType,
                            providerId = providerId
                        )
                    )
                },
                contentPadding = PaddingValues(
                    bottom = floatingBarHeight + FloatingBarDefaults.padding
                )
            )
        }
    }
}

@Composable
private fun Players(
    playerPagerState: PagerState,
    state: HomeScreenViewModel.PlayersState,
    serverUrl: String?,
    homeScreenViewModel: HomeScreenViewModel,
    actionsViewModel: ActionsViewModel,
    expanded: Boolean,
    onClose: () -> Unit,
    isExpandedScreen: Boolean,
    contentPadding: PaddingValues
) {
    if (state is HomeScreenViewModel.PlayersState.Data && state.playerData.isNotEmpty()) {
        val simplePlayerAction = remember(homeScreenViewModel) {
            { playerId: String, action: PlayerAction ->
                homeScreenViewModel.playerAction(playerId, action)
            }
        }
        val playerAction = remember(homeScreenViewModel) {
            { playerData: PlayerData, action: PlayerAction ->
                homeScreenViewModel.playerAction(playerData, action)
            }
        }
        val onFavoriteClick = remember(actionsViewModel) {
            { item: AppMediaItem -> actionsViewModel.onFavoriteClick(item) }
        }
        val queueAction = remember(homeScreenViewModel) {
            { action: QueueAction -> homeScreenViewModel.queueAction(action) }
        }
        val moveToPlayer = remember(state, homeScreenViewModel) {
            { id: String ->
                state.playerData.find { it.player.id == id }
                    ?.let { homeScreenViewModel.selectPlayer(it.player) }
                Unit
            }
        }
        val onItemMoved = remember(state, homeScreenViewModel, playerPagerState) {
            { indexShift: Int ->
                val currentPlayer = state.playerData[playerPagerState.currentPage].player
                val newIndex = (playerPagerState.currentPage + indexShift)
                    .coerceIn(0, state.playerData.size - 1)
                val newPlayers = state.playerData.map { it.player.id }
                    .toMutableList()
                    .apply { add(newIndex, removeAt(playerPagerState.currentPage)) }
                homeScreenViewModel.selectPlayer(currentPlayer)
                homeScreenViewModel.onPlayersSortChanged(newPlayers)
            }
        }

        PlayersPager(
            playerPagerState = playerPagerState,
            playersState = state,
            serverUrl = serverUrl,
            simplePlayerAction = simplePlayerAction,
            playerAction = playerAction,
            onFavoriteClick = onFavoriteClick,
            expanded = expanded,
            onClose = onClose,
            onItemMoved = onItemMoved,
            queueAction = queueAction,
            moveToPlayer = moveToPlayer,
            isExpandedScreen = isExpandedScreen,
            contentPadding = contentPadding
        )
    } else {
        Box(Modifier.fillMaxWidth().height(collapsedPlayerHeight(isExpandedScreen))) {
            val text = when (state) {
                is HomeScreenViewModel.PlayersState.Loading -> "Loading players..."
                is HomeScreenViewModel.PlayersState.Data -> "No players available"
                else -> "No players available"
            }

            Text(
                modifier = Modifier.align(Alignment.Center),
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
