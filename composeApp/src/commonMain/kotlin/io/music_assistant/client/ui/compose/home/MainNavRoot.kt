@file:OptIn(ExperimentalMaterial3Api::class)

package io.music_assistant.client.ui.compose.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import io.music_assistant.client.data.model.client.MediaType
import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.data.model.client.items.Album
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.Artist
import io.music_assistant.client.data.model.client.items.Audiobook
import io.music_assistant.client.data.model.client.items.Genre
import io.music_assistant.client.data.model.client.items.Playlist
import io.music_assistant.client.data.model.client.items.Podcast
import io.music_assistant.client.data.model.client.items.RecommendationFolder
import io.music_assistant.client.ui.compose.common.DataState
import io.music_assistant.client.ui.compose.common.action.PlayerAction
import io.music_assistant.client.ui.compose.common.action.QueueAction
import io.music_assistant.client.ui.compose.common.providers.ProviderIcon
import io.music_assistant.client.ui.compose.common.rememberExtractedColorsFetcher
import io.music_assistant.client.ui.compose.common.rememberToastState
import io.music_assistant.client.ui.compose.common.viewmodel.ActionsViewModel
import io.music_assistant.client.ui.compose.home.players.DspSettingsViewModel
import io.music_assistant.client.ui.compose.home.players.PlayersPager
import io.music_assistant.client.ui.compose.item.ItemDetailsScreen
import io.music_assistant.client.ui.compose.item.ItemDetailsViewModel
import io.music_assistant.client.ui.compose.library.LibraryNavCoordinator
import io.music_assistant.client.ui.compose.library.LibraryScreen
import io.music_assistant.client.ui.compose.library.LibraryViewModel
import io.music_assistant.client.ui.compose.nav.AdaptiveNavigationScaffold
import io.music_assistant.client.ui.compose.nav.MultiBackStack
import io.music_assistant.client.ui.compose.nav.NavigationItem
import io.music_assistant.client.ui.compose.nav.createNavigationItem
import io.music_assistant.client.ui.compose.search.SearchScreen
import io.music_assistant.client.ui.compose.search.SearchViewModel
import io.music_assistant.client.utils.SessionState
import kotlinx.coroutines.flow.collectLatest
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.nav_home
import musicassistantclient.composeapp.generated.resources.nav_library
import musicassistantclient.composeapp.generated.resources.nav_search
import musicassistantclient.composeapp.generated.resources.nav_settings
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MainNavigationRoot(
    homeScreenViewModel: HomeScreenViewModel = koinViewModel(),
    actionsViewModel: ActionsViewModel = koinViewModel(),
    dspSettingsViewModel: DspSettingsViewModel = koinViewModel(),
    goToSettings: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val toastState = rememberToastState()

    LaunchedEffect(Unit) {
        homeScreenViewModel.links.collectLatest { url -> uriHandler.openUri(url) }
    }

    // Collect toasts
    LaunchedEffect(Unit) {
        actionsViewModel.toasts.collect { toast ->
            toastState.showToast(toast)
        }
    }

    val recommendationsState = homeScreenViewModel.recommendationsState.collectAsStateWithLifecycle()
    val playersState by homeScreenViewModel.playersState.collectAsStateWithLifecycle()
    // Single pager state used across all views
    val data = playersState as? HomeScreenViewModel.PlayersState.Data
    val playerPagerState = rememberPagerState(
        initialPage = data?.selectedPlayerIndex ?: 0,
        pageCount = { data?.playerData?.size ?: 0 },
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
                homeScreenViewModel.selectPlayer(playerData.player)
            }
        }
    }

    val connectionState = recommendationsState.value.connectionState
    val dataState = recommendationsState.value.recommendations
    val hiddenFolderIds = recommendationsState.value.hiddenFolderIds

    var playerExpanded by remember { mutableStateOf(false) }

    val playlistActions = remember(actionsViewModel) {
        ActionsViewModel.PlaylistActions(
            onLoadPlaylists = actionsViewModel::getEditablePlaylists,
            onAddToPlaylist = actionsViewModel::addToPlaylist,
        )
    }
    val libraryActions = remember(actionsViewModel) {
        ActionsViewModel.LibraryActions(
            onLibraryClick = actionsViewModel::onLibraryClick,
            onFavoriteClick = actionsViewModel::onFavoriteClick,
        )
    }
    val progressActions = remember(actionsViewModel) {
        ActionsViewModel.ProgressActions(
            onMarkPlayed = actionsViewModel::onMarkPlayed,
            onMarkUnplayed = actionsViewModel::onMarkUnplayed,
        )
    }

    val onExpandPlayer = remember { { expanded: Boolean -> playerExpanded = expanded } }

    val backStacks = listOf(
        rememberMainNavBackStack(MainNav.Landing),
        rememberMainNavBackStack(MainNav.Library(null)),
        rememberMainNavBackStack(MainNav.Search),
    )
    val multiBackStack = remember { MultiBackStack(backStacks) }

    val navigationItems = listOf(
        multiBackStack.createNavigationItem(
            backStack = 0,
            icon = Icons.Default.Home,
            label = stringResource(Res.string.nav_home),
        ),
        multiBackStack.createNavigationItem(
            backStack = 1,
            icon = Icons.Default.LibraryMusic,
            label = stringResource(Res.string.nav_library),
        ),
        multiBackStack.createNavigationItem(
            backStack = 2,
            icon = Icons.Default.Search,
            label = stringResource(Res.string.nav_search),
        ),
        NavigationItem(
            selected = false,
            onClick = { goToSettings() },
            Icons.Default.Settings,
            label = stringResource(Res.string.nav_settings),
        ),
    )

    AdaptiveNavigationScaffold(
        showNavBar = !playerExpanded,
        navigationItems = navigationItems,
    ) { scaffoldContentPadding ->
        val bottomPadding = scaffoldContentPadding.calculateBottomPadding()

        FloatingBarLayout(
            floatingBar = {
                FloatingBar(
                    collapsedBottomPadding = bottomPadding,
                    expanded = playerExpanded,
                    onExpand = onExpandPlayer,
                    { expanded, contentPadding ->
                        Players(
                            playerPagerState = playerPagerState,
                            state = playersState,
                            homeScreenViewModel = homeScreenViewModel,
                            actionsViewModel = actionsViewModel,
                            dspSettingsViewModel = dspSettingsViewModel,
                            expanded = expanded,
                            onClose = { playerExpanded = false },
                            contentPadding = contentPadding,
                            backStack = multiBackStack,
                        )
                    },
                )
            },
        ) { floatingBarContentPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
            ) {
                NavDisplay(
                    entries = rememberDecoratedNavEntries(
                        entryDecorators = listOf(
                            rememberSaveableStateHolderNavEntryDecorator(),
                            rememberViewModelStoreNavEntryDecorator(),
                        ),
                        entries = multiBackStack.toEntries(
                            mainNavEntryProvider(
                                floatingBarContentPadding,
                                connectionState,
                                dataState,
                                hiddenFolderIds,

                                multiBackStack,
                                homeScreenViewModel,
                                playlistActions,
                                libraryActions,
                                progressActions,
                                actionsViewModel,
                            ),
                        ),
                    ),
                    onBack = {
                        multiBackStack.removeLastOrNull()
                    },
                )
            }
        }
    }
}

@Composable
private fun mainNavEntryProvider(
    contentPadding: PaddingValues,
    connectionState: SessionState,
    dataState: DataState<List<RecommendationFolder>>,
    hiddenFolderIds: Set<String>,
    multiBackStack: MultiBackStack<NavKey>,
    viewModel: HomeScreenViewModel,
    playlistActions: ActionsViewModel.PlaylistActions,
    libraryActions: ActionsViewModel.LibraryActions,
    progressActions: ActionsViewModel.ProgressActions,
    actionsViewModel: ActionsViewModel,
): (NavKey) -> NavEntry<NavKey> {
    val libraryNavCoordinator: LibraryNavCoordinator = koinInject()
    return entryProvider {
        entry<MainNav.Landing> {
            HomeScreen(
                contentPadding = contentPadding,
                connectionState = connectionState,
                dataState = dataState,
                onNavigateClick = { item ->
                    when (item) {
                        is Artist,
                        is Album,
                        is Playlist,
                        is Podcast,
                        is Audiobook,
                        is Genre,
                        -> {
                            multiBackStack.add(
                                MainNav.ItemDetails(
                                    itemId = item.itemId,
                                    mediaType = item.mediaType,
                                    providerId = item.provider,
                                ),
                            )
                        }

                        else -> Unit
                    }
                },
                onPlayClick = viewModel::onPlayClick,
                onLibraryItemClick = { type ->
                    type?.let { libraryNavCoordinator.requestTab(it) }
                    multiBackStack.switchTo(1, MainNav.Library(type))
                },
                playlistActions = playlistActions,
                libraryActions = libraryActions,
                progressActions = progressActions,
                providerIconFetcher = { modifier, provider ->
                    actionsViewModel.getProviderIcon(provider)
                        ?.let { ProviderIcon(modifier, it) }
                },
                onRefresh = { viewModel.loadRecommendations() },
                hiddenFolderIds = hiddenFolderIds,
                onSaveHiddenFolders = viewModel::saveHiddenRecommendationFolders,
            )
        }

        entry<MainNav.Library> {
            val libraryViewModel = koinViewModel<LibraryViewModel>()

            LibraryScreen(
                libraryViewModel = libraryViewModel,
                contentPadding = contentPadding,
                initialTabType = it.type,
                actionsViewModel = actionsViewModel,
            ) { item ->
                when (item) {
                    is Artist,
                    is Album,
                    is Playlist,
                    is Podcast,
                    is Audiobook,
                    is Genre,
                        -> {
                        multiBackStack.add(
                            MainNav.ItemDetails(
                                itemId = item.itemId,
                                mediaType = item.mediaType,
                                providerId = item.provider,
                            ),
                        )
                    }

                    else -> Unit
                }
            }
        }

        entry<MainNav.ItemDetails> {
            val itemDetailsViewModel = koinViewModel<ItemDetailsViewModel> {
                parametersOf(it.itemId, it.mediaType, it.providerId)
            }

            ItemDetailsScreen(
                itemDetailsViewModel = itemDetailsViewModel,
                actionsViewModel = actionsViewModel,
                onBack = { multiBackStack.removeLastOrNull() },
                onNavigateToItem = { itemId, mediaType, providerId ->
                    multiBackStack.add(
                        MainNav.ItemDetails(
                            itemId = itemId,
                            mediaType = mediaType,
                            providerId = providerId,
                        ),
                    )
                },
                contentPadding = contentPadding,
            )
        }

        entry<MainNav.Search> {
            val searchViewModel = koinViewModel<SearchViewModel>()

            SearchScreen(
                searchViewModel = searchViewModel,
                onNavigateToItem = { itemId, mediaType, providerId ->
                    multiBackStack.add(
                        MainNav.ItemDetails(
                            itemId = itemId,
                            mediaType = mediaType,
                            providerId = providerId,
                        ),
                    )
                },
                contentPadding = contentPadding,
                actionsViewModel = actionsViewModel,
            )
        }
    }
}

@Composable
private fun Players(
    playerPagerState: PagerState,
    state: HomeScreenViewModel.PlayersState,
    homeScreenViewModel: HomeScreenViewModel,
    actionsViewModel: ActionsViewModel,
    dspSettingsViewModel: DspSettingsViewModel,
    expanded: Boolean,
    onClose: () -> Unit,
    contentPadding: PaddingValues,
    backStack: MultiBackStack<NavKey>,
) {
    if (state is HomeScreenViewModel.PlayersState.Data && state.playerData.isNotEmpty()) {
        val simplePlayerAction = remember(homeScreenViewModel) {
            {
                playerId: String, action: PlayerAction ->
                homeScreenViewModel.playerAction(playerId, action)
            }
        }
        val playerAction = remember(homeScreenViewModel) {
            {
                playerData: PlayerData, action: PlayerAction ->
                homeScreenViewModel.playerAction(playerData, action)
            }
        }
        val onFavoriteClick = remember(actionsViewModel) {
            {
                item: AppMediaItem ->
                    actionsViewModel.onFavoriteClick(item)
                }
        }
        val queueAction = remember(homeScreenViewModel) {
            {
                action: QueueAction ->
                    homeScreenViewModel.queueAction(action)
                }
        }
        val moveToPlayer = remember(state, homeScreenViewModel) {
            {
                id: String ->
                state.playerData.find { it.player.id == id }
                    ?.let { homeScreenViewModel.selectPlayer(it.player) }
                Unit
            }
        }
        val onPlayersReorder = remember(homeScreenViewModel) {
            {
                newPlayerIds: List<String> ->
                homeScreenViewModel.onPlayersSortChanged(newPlayerIds)
            }
        }
        val fetchColors = rememberExtractedColorsFetcher()

        PlayersPager(
            playerPagerState = playerPagerState,
            playersState = state,
            simplePlayerAction = simplePlayerAction,
            playerAction = playerAction,
            onFavoriteClick = onFavoriteClick,
            onClose = onClose,
            navigateToItem = { item ->
                backStack.add(
                    MainNav.ItemDetails(
                        itemId = item.itemId,
                        mediaType = item.mediaType,
                        providerId = item.provider,
                    ),
                )
            },
            onPlayersReorder = onPlayersReorder,
            queueAction = queueAction,
            moveToPlayer = moveToPlayer,
            contentPadding = contentPadding,
            localPlayerId = homeScreenViewModel.localPlayerId,
            onAdjustPlaybackDelay = homeScreenViewModel::adjustSendspinStaticDelayMs,
            fetchColors = fetchColors,
            observePosition = homeScreenViewModel::observePosition,
            compact = !expanded,
            dspSettingsViewModel = dspSettingsViewModel,
        )
    } else {
        Box(Modifier.fillMaxWidth().height(84.dp)) {
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

private sealed interface MainNav : NavKey {
    @Serializable
    data object Landing : MainNav

    @Serializable
    data class Library(val type: MediaType?) : MainNav

    /**
     * Multiple instances of the same item can appear in a back stack - [stackingId] ensures they
     * are treated as different entries.
     */
    @OptIn(ExperimentalUuidApi::class)
    @Serializable
    data class ItemDetails(
        val itemId: String,
        val mediaType: MediaType,
        val providerId: String,
        val stackingId: String = Uuid.generateV4().toString(),
    ) : MainNav

    @Serializable
    data object Search : MainNav
}

@Composable
private fun rememberMainNavBackStack(bottom: MainNav) = rememberNavBackStack(
    SavedStateConfiguration(
        from = SavedStateConfiguration.DEFAULT,
        builderAction = {
            serializersModule = SerializersModule {
                polymorphic(NavKey::class) {
                    subclass(MainNav.Landing::class, MainNav.Landing.serializer())
                    subclass(MainNav.Library::class, MainNav.Library.serializer())
                    subclass(
                        MainNav.ItemDetails::class,
                        MainNav.ItemDetails.serializer(),
                    )
                    subclass(MainNav.Search::class, MainNav.Search.serializer())
                }
            }
        },
    ),
    bottom,
)
