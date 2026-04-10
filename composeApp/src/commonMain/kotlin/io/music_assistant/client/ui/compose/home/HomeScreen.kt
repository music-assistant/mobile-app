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
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
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
import io.music_assistant.client.ui.compose.home.players.PlayersPager
import io.music_assistant.client.ui.compose.home.players.collapsedPlayerHeight
import io.music_assistant.client.ui.compose.item.ItemDetailsScreen
import io.music_assistant.client.ui.compose.library.LibraryScreen
import io.music_assistant.client.ui.compose.nav.AdaptiveNavigationScaffold
import io.music_assistant.client.ui.compose.nav.BackHandler
import io.music_assistant.client.ui.compose.nav.NavigationItem
import io.music_assistant.client.ui.compose.search.SearchScreen
import io.music_assistant.client.utils.SessionState
import io.music_assistant.client.utils.WindowClass
import kotlinx.coroutines.flow.collectLatest
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen(
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

    // Nested navigation backstack - hoisted to survive player view transitions
    val homeBackStack = rememberHomeNavBackStack()

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
    // Simple slide transition between main screen and big player

    val navigationItems = listOf(
        NavigationItem(
            selected = true,
            onClick = { },
            Icons.Default.Home
        ),
        NavigationItem(
            selected = false,
            onClick = { goToSettings() },
            Icons.Default.Settings
        )
    )

    var playerExpanded by remember { mutableStateOf(false) }
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
            HomeContent(
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
                providerIconFetcher = { modifier, provider ->
                    actionsViewModel.getProviderIcon(provider)
                        ?.let { ProviderIcon(modifier, it) }
                },
                contentPadding = PaddingValues(
                    bottom = floatingBarHeight + FloatingBarDefaults.padding
                )
            )
        }

        FloatingBar(
            bottomPadding = bottomPadding,
            expanded = playerExpanded,
            onExpand = {
                playerExpanded = it
            }
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
    providerIconFetcher: (@Composable (Modifier, String) -> Unit),
    contentPadding: PaddingValues
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
                    contentPadding = contentPadding,
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
                    providerIconFetcher = providerIconFetcher
                )
            }

            entry<HomeNavScreen.Library> {
                LibraryScreen(
                    contentPadding = contentPadding,
                    initialTabType = it.type,
                    onBack = { typedBackStack.removeLastOrNull() },
                    onNavigateClick = { item ->
                        when (item) {
                            is AppMediaItem.Artist,
                            is AppMediaItem.Album,
                            is AppMediaItem.Playlist,
                            is AppMediaItem.Podcast,
                            is AppMediaItem.Audiobook,
                            is AppMediaItem.Genre -> {
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
                    contentPadding = contentPadding,
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
        PlayersPager(
            playerPagerState = playerPagerState,
            playersState = state,
            serverUrl = serverUrl,
            simplePlayerAction = { playerId, action ->
                homeScreenViewModel.playerAction(playerId, action)
            },
            playerAction = { playerData, action ->
                homeScreenViewModel.playerAction(playerData, action)
            },
            onFavoriteClick = actionsViewModel::onFavoriteClick,
            expanded = expanded,
            onClose = onClose,
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
                homeScreenViewModel.selectPlayer(currentPlayer)
                homeScreenViewModel.onPlayersSortChanged(newPlayers)
            },
            queueAction = { action -> homeScreenViewModel.queueAction(action) },
            moveToPlayer = { id: String ->
                val player =
                    state.playerData.find { it.player.id == id }
                if (player != null) {
                    homeScreenViewModel.selectPlayer(player.player)
                }
            },
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
