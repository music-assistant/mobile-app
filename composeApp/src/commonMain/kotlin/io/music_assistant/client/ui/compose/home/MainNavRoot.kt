@file:OptIn(ExperimentalMaterial3Api::class)

package io.music_assistant.client.ui.compose.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.savedstate.serialization.SavedStateConfiguration
import io.music_assistant.client.api.DeepLinkBus
import io.music_assistant.client.api.DeepLinkDestination
import io.music_assistant.client.api.ErrorMessageBus
import io.music_assistant.client.data.model.client.MediaType
import io.music_assistant.client.data.model.client.items.Album
import io.music_assistant.client.data.model.client.items.Artist
import io.music_assistant.client.data.model.client.items.Audiobook
import io.music_assistant.client.data.model.client.items.Genre
import io.music_assistant.client.data.model.client.items.Playlist
import io.music_assistant.client.data.model.client.items.Podcast
import io.music_assistant.client.data.model.client.items.RecommendationFolder
import io.music_assistant.client.ui.compose.common.ToastDuration
import io.music_assistant.client.ui.compose.common.ToastHost
import io.music_assistant.client.ui.compose.common.providers.ProviderIcon
import io.music_assistant.client.ui.compose.common.rememberToastState
import io.music_assistant.client.ui.compose.common.viewmodel.ActionsViewModel
import io.music_assistant.client.ui.compose.home.players.DspSettingsViewModel
import io.music_assistant.client.ui.compose.home.players.PlayersPager
import io.music_assistant.client.ui.compose.item.ItemDetailsScreen
import io.music_assistant.client.ui.compose.item.ItemDetailsViewModel
import io.music_assistant.client.ui.compose.library.BrowseScreen
import io.music_assistant.client.ui.compose.library.BrowseViewModel
import io.music_assistant.client.ui.compose.library.ItemListScreen
import io.music_assistant.client.ui.compose.library.ItemListViewModel
import io.music_assistant.client.ui.compose.library.LibraryCategoriesViewModel
import io.music_assistant.client.ui.compose.library.LibraryCategory
import io.music_assistant.client.ui.compose.library.LibraryScreen
import io.music_assistant.client.ui.compose.library.LibraryScreenState
import io.music_assistant.client.ui.compose.nav.AdaptiveNavigationScaffold
import io.music_assistant.client.ui.compose.nav.BackHandler
import io.music_assistant.client.ui.compose.nav.ConditionalBackNavDisplay
import io.music_assistant.client.ui.compose.nav.MultiBackStack
import io.music_assistant.client.ui.compose.nav.NavigationItem
import io.music_assistant.client.ui.compose.nav.createNavigationItem
import io.music_assistant.client.ui.compose.search.GlobalSearchRequest
import io.music_assistant.client.ui.compose.search.SearchScreen
import io.music_assistant.client.ui.compose.search.SearchScreenState
import io.music_assistant.client.ui.compose.search.SearchViewModel
import io.music_assistant.client.utils.DataConnectionState
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
    val errorBus: ErrorMessageBus = koinInject()
    val deepLinkBus: DeepLinkBus = koinInject()

    LaunchedEffect(Unit) {
        homeScreenViewModel.links.collectLatest { url -> uriHandler.openUri(url) }
    }

    // Surface server-side RPC errors as toasts on top of whichever screen is active.
    // (Per-screen ToastHosts handle ActionsViewModel toasts.)
    LaunchedEffect(Unit) {
        errorBus.messages.collect { msg ->
            val truncated = if (msg.length > 150) msg.take(150) + "…" else msg
            toastState.showToast(truncated, ToastDuration.LONG)
        }
    }

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

    var playerExpanded by remember { mutableStateOf(false) }

    val onExpandPlayer = remember { { expanded: Boolean -> playerExpanded = expanded } }

    val backStacks = listOf(
        rememberMainNavBackStack(MainNav.Landing),
        rememberMainNavBackStack(MainNav.Library),
        rememberMainNavBackStack(MainNav.Search),
    )
    val multiBackStack = remember { MultiBackStack(backStacks) }

    // Apply a pending navigation deep link (musicassistant://app/<page> or the
    // https App/Universal Link). The destination is retained upstream, and we
    // gate on Authenticated + re-key on connectionState so that the transient
    // pre-auth MainNavigationRoot instance — torn down during the cold-launch
    // Main→Settings→Main churn — never consumes it; only the authenticated
    // instance that stays on screen applies and clears it.
    val connectionState by homeScreenViewModel.connectionState.collectAsStateWithLifecycle()
    val pendingDeepLink by deepLinkBus.pending.collectAsStateWithLifecycle()
    LaunchedEffect(pendingDeepLink, connectionState) {
        val dest = pendingDeepLink ?: return@LaunchedEffect
        val authenticated = (connectionState as? SessionState.Connected)
            ?.dataConnectionState == DataConnectionState.Authenticated
        if (!authenticated) return@LaunchedEffect
        when (dest) {
            DeepLinkDestination.Home -> {
                multiBackStack.currentBackStack = 0
                multiBackStack.resetCurrentBackStack()
            }

            is DeepLinkDestination.Library -> {
                multiBackStack.currentBackStack = 1
                multiBackStack.resetCurrentBackStack()
                // /library/<category> → push the category list onto the Library tab.
                dest.mediaType?.let { multiBackStack.add(MainNav.ItemList(it)) }
            }

            DeepLinkDestination.Search -> {
                multiBackStack.currentBackStack = 2
                multiBackStack.resetCurrentBackStack()
            }

            DeepLinkDestination.Players -> {
                // Expand the now-playing layout over the current tab (the
                // FloatingBar is global, so no tab switch needed). The pager
                // renders its own empty state if no player is present.
                playerExpanded = true
            }
        }
        deepLinkBus.consume(dest)
    }

    val homeScreenState = HomeScreenState.create()
    val libraryScreenState = LibraryScreenState.create()
    val searchScreenState = SearchScreenState.create()

    val navigationItems = listOf(
        multiBackStack.createNavigationItem(
            backStack = 0,
            icon = Icons.Default.Home,
            label = stringResource(Res.string.nav_home),
            screenState = homeScreenState,
        ),
        multiBackStack.createNavigationItem(
            backStack = 1,
            icon = Icons.Default.LibraryMusic,
            label = stringResource(Res.string.nav_library),
            screenState = libraryScreenState,
        ),
        multiBackStack.createNavigationItem(
            backStack = 2,
            icon = Icons.Default.Search,
            label = stringResource(Res.string.nav_search),
            screenState = searchScreenState,
        ),
        NavigationItem(
            selected = false,
            onClick = { goToSettings() },
            Icons.Default.Settings,
            label = stringResource(Res.string.nav_settings),
        ),
    )

    Box(modifier = Modifier.fillMaxSize()) {
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
                        content = { expanded, contentPadding ->
                            PlayersPager(
                                playerPagerState = playerPagerState,
                                state = playersState,
                                homeScreenViewModel = homeScreenViewModel,
                                actionsViewModel = actionsViewModel,
                                dspSettingsViewModel = dspSettingsViewModel,
                                expanded = expanded,
                                onClose = { playerExpanded = false },
                                contentPadding = contentPadding,
                            ) { item ->
                                multiBackStack.add(
                                    MainNav.ItemDetails(
                                        itemId = item.itemId,
                                        mediaType = item.mediaType,
                                        providerId = item.provider,
                                    ),
                                )
                            }
                        },
                    )
                },
            ) { floatingBarContentPadding ->
                BackHandler(playerExpanded) {
                    playerExpanded = !playerExpanded
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                ) {
                    ConditionalBackNavDisplay(
                        entries = rememberDecoratedNavEntries(
                            entryDecorators = listOf(
                                rememberSaveableStateHolderNavEntryDecorator(),
                                rememberViewModelStoreNavEntryDecorator(),
                            ),
                            entries = multiBackStack.toEntries(
                                mainNavEntryProvider(
                                    floatingBarContentPadding,
                                    connectionState is SessionState.Connected,
                                    multiBackStack,
                                    homeScreenViewModel,
                                    actionsViewModel,
                                    homeScreenState,
                                    libraryScreenState,
                                    searchScreenState,
                                ),
                            ),
                        ),
                        onBack = {
                            multiBackStack.removeLastOrNull()
                        },
                        backEnabled = !playerExpanded,
                    )
                }
            }
        }
        ToastHost(toastState = toastState)
    }
}

@Composable
private fun mainNavEntryProvider(
    contentPadding: PaddingValues,
    isConnected: Boolean,
    multiBackStack: MultiBackStack<NavKey>,
    homeScreenViewModel: HomeScreenViewModel,
    actionsViewModel: ActionsViewModel,
    homeScreenState: HomeScreenState,
    libraryScreenState: LibraryScreenState,
    searchScreenState: SearchScreenState,
): (NavKey) -> NavEntry<NavKey> {
    // Hoisted here (outlives the per-NavEntry SearchViewModel) to carry an empty-quick-search
    // escalation from the library tab to the Search tab. Set by ItemList, consumed by SearchScreen.
    var pendingSearch by remember { mutableStateOf<GlobalSearchRequest?>(null) }
    return entryProvider {
        entry<MainNav.Landing> {
            HomeScreen(
                homeScreenViewModel,
                contentPadding = contentPadding,
                isConnected = isConnected,
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
                onLibraryItemClick = { type ->
                    multiBackStack.add(MainNav.ItemList(type))
                },
                providerIconFetcher = { modifier, provider ->
                    actionsViewModel.getProviderIcon(provider)
                        ?.let { ProviderIcon(modifier, it) }
                },
                actionsViewModel = actionsViewModel,
                state = homeScreenState,
            )
        }

        entry<MainNav.Library> {
            val libraryCategoriesViewModel = koinViewModel<LibraryCategoriesViewModel>()

            LibraryScreen(
                libraryCategoriesViewModel,
                contentPadding = contentPadding,
                state = libraryScreenState,
                onCategoryClick = { category ->
                    if (category == LibraryCategory.BROWSE) {
                        multiBackStack.add(MainNav.Browse(path = null, title = null))
                    } else {
                        category.mediaType?.let { multiBackStack.add(MainNav.ItemList(it)) }
                    }
                },
            )
        }

        entry<MainNav.ItemList> {
            val itemListViewModel = koinViewModel<ItemListViewModel> {
                parametersOf(it.mediaType)
            }

            ItemListScreen(
                itemListViewModel = itemListViewModel,
                contentPadding = contentPadding,
                actionsViewModel = actionsViewModel,
                onBack = { multiBackStack.removeLastOrNull() },
                onGlobalSearch = { query ->
                    pendingSearch = GlobalSearchRequest(query, it.mediaType)
                    multiBackStack.currentBackStack = 2
                    multiBackStack.resetCurrentBackStack()
                },
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
            )
        }

        entry<MainNav.Browse> { browse ->
            val browseViewModel = koinViewModel<BrowseViewModel> {
                parametersOf(browse.path)
            }

            BrowseScreen(
                browseViewModel = browseViewModel,
                title = browse.title,
                contentPadding = contentPadding,
                actionsViewModel = actionsViewModel,
                onBack = { multiBackStack.removeLastOrNull() },
                onNavigateClick = { item ->
                    when (item) {
                        is RecommendationFolder ->
                            if (item.isParentLink) {
                                // The server's ".." entry maps to our own back navigation.
                                multiBackStack.removeLastOrNull()
                            } else {
                                // BrowseFolder carries an explicit `path`; `uri` is only a fallback.
                                multiBackStack.add(
                                    MainNav.Browse(path = item.path ?: item.uri, title = item.displayName),
                                )
                            }

                        is Artist,
                        is Album,
                        is Playlist,
                        is Podcast,
                        is Audiobook,
                        is Genre,
                        -> multiBackStack.add(
                            MainNav.ItemDetails(
                                itemId = item.itemId,
                                mediaType = item.mediaType,
                                providerId = item.provider,
                            ),
                        )

                        else -> Unit
                    }
                },
            )
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
                state = searchScreenState,
                pendingSearch = pendingSearch,
                onSearchConsumed = { pendingSearch = null },
            )
        }
    }
}

private sealed interface MainNav : NavKey {
    @Serializable
    data object Landing : MainNav

    @Serializable
    data object Library : MainNav

    @Serializable
    data class ItemList(val mediaType: MediaType) : MainNav

    /**
     * One level of the folder-style Browse tree. [path] is the server browse path (null = root);
     * [stackingId] keeps stacked levels distinct in the back stack (mirrors [ItemDetails]).
     */
    @OptIn(ExperimentalUuidApi::class)
    @Serializable
    data class Browse(
        val path: String?,
        val title: String?,
        val stackingId: String = Uuid.generateV4().toString(),
    ) : MainNav

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
                    subclass(MainNav.ItemList::class, MainNav.ItemList.serializer())
                    subclass(MainNav.Browse::class, MainNav.Browse.serializer())
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
