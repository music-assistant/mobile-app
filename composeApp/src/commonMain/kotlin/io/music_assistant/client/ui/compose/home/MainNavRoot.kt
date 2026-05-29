@file:OptIn(ExperimentalMaterial3Api::class)

package io.music_assistant.client.ui.compose.home

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
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
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import io.music_assistant.client.api.ErrorMessageBus
import io.music_assistant.client.data.model.client.MediaType
import io.music_assistant.client.data.model.client.items.Album
import io.music_assistant.client.data.model.client.items.Artist
import io.music_assistant.client.data.model.client.items.Audiobook
import io.music_assistant.client.data.model.client.items.Genre
import io.music_assistant.client.data.model.client.items.Playlist
import io.music_assistant.client.data.model.client.items.Podcast
import io.music_assistant.client.data.model.client.items.RecommendationFolder
import io.music_assistant.client.ui.compose.common.DataState
import io.music_assistant.client.ui.compose.common.ToastDuration
import io.music_assistant.client.ui.compose.common.ToastHost
import io.music_assistant.client.ui.compose.common.providers.ProviderIcon
import io.music_assistant.client.ui.compose.common.rememberToastState
import io.music_assistant.client.ui.compose.common.viewmodel.ActionsViewModel
import io.music_assistant.client.ui.compose.home.players.DspSettingsViewModel
import io.music_assistant.client.ui.compose.home.players.PlayersPager
import io.music_assistant.client.ui.compose.item.ItemDetailsScreen
import io.music_assistant.client.ui.compose.item.ItemDetailsViewModel
import io.music_assistant.client.ui.compose.library.ItemListScreen
import io.music_assistant.client.ui.compose.library.ItemListViewModel
import io.music_assistant.client.ui.compose.library.LibraryCategoriesViewModel
import io.music_assistant.client.ui.compose.library.LibraryScreen
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
    val errorBus: ErrorMessageBus = koinInject()

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

    val onExpandPlayer = remember { { expanded: Boolean -> playerExpanded = expanded } }

    val backStacks = listOf(
        rememberMainNavBackStack(MainNav.Landing),
        rememberMainNavBackStack(MainNav.Library),
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
                    { expanded, contentPadding ->
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
                                actionsViewModel,
                            ),
                        ),
                    ),
                    onBack = {
                        multiBackStack.removeLastOrNull()
                    },
                    // Workaround for CMP 1.10.3 iOS crash: LazyLayout measured inside
                    // AnimatedContent + CupertinoOverscroll trips a SubcomposeLayout
                    // precondition on first frame. Disabling transitions removes the
                    // animating measure path.
                    transitionSpec = { EnterTransition.None togetherWith ExitTransition.None },
                    popTransitionSpec = { EnterTransition.None togetherWith ExitTransition.None },
                    predictivePopTransitionSpec = { EnterTransition.None togetherWith ExitTransition.None },
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
    connectionState: SessionState,
    dataState: DataState<List<RecommendationFolder>>,
    hiddenFolderIds: Set<String>,
    multiBackStack: MultiBackStack<NavKey>,
    homeScreenViewModel: HomeScreenViewModel,
    actionsViewModel: ActionsViewModel,
): (NavKey) -> NavEntry<NavKey> {
    return entryProvider {
        entry<MainNav.Landing> {
            HomeScreen(
                homeScreenViewModel,
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
                onLibraryItemClick = { type ->
                    multiBackStack.add(MainNav.ItemList(type))
                },
                providerIconFetcher = { modifier, provider ->
                    actionsViewModel.getProviderIcon(provider)
                        ?.let { ProviderIcon(modifier, it) }
                },
                hiddenFolderIds = hiddenFolderIds,
                actionsViewModel = actionsViewModel,
            )
        }

        entry<MainNav.Library> {
            val libraryCategoriesViewModel = koinViewModel<LibraryCategoriesViewModel>()

            LibraryScreen(
                libraryCategoriesViewModel,
                contentPadding = contentPadding,
                onTypeClick = {
                    multiBackStack.add(MainNav.ItemList(it))
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

private sealed interface MainNav : NavKey {
    @Serializable
    data object Landing : MainNav

    @Serializable
    data object Library : MainNav

    @Serializable
    data class ItemList(val mediaType: MediaType) : MainNav

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
