@file:OptIn(ExperimentalMaterial3Api::class)

package io.music_assistant.client.ui.compose.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
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
import io.music_assistant.client.data.model.client.ClickContext
import io.music_assistant.client.data.model.client.MediaType
import io.music_assistant.client.data.model.client.items.Album
import io.music_assistant.client.data.model.client.items.Artist
import io.music_assistant.client.data.model.client.items.Audiobook
import io.music_assistant.client.data.model.client.items.Genre
import io.music_assistant.client.data.model.client.items.Playlist
import io.music_assistant.client.data.model.client.items.Podcast
import io.music_assistant.client.data.model.client.items.RecommendationFolder
import io.music_assistant.client.input.VolumeButtonService
import io.music_assistant.client.ui.compose.common.ToastDuration
import io.music_assistant.client.ui.compose.common.ToastHost
import io.music_assistant.client.ui.compose.common.providers.ProviderIcon
import io.music_assistant.client.ui.compose.common.rememberToastState
import io.music_assistant.client.ui.compose.common.viewmodel.ActionsViewModel
import io.music_assistant.client.ui.compose.home.players.DspSettingsViewModel
import io.music_assistant.client.ui.compose.home.players.PlayersPager
import io.music_assistant.client.ui.compose.item.ItemDetailsScreen
import io.music_assistant.client.ui.compose.item.ItemDetailsViewModel
import io.music_assistant.client.ui.compose.item.ItemListScreen
import io.music_assistant.client.ui.compose.item.ItemListViewModel
import io.music_assistant.client.ui.compose.item.ViewModeViewModel
import io.music_assistant.client.ui.compose.library.BrowseScreen
import io.music_assistant.client.ui.compose.library.BrowseViewModel
import io.music_assistant.client.ui.compose.library.LibraryCategoriesViewModel
import io.music_assistant.client.ui.compose.library.LibraryCategory
import io.music_assistant.client.ui.compose.library.LibraryListScreen
import io.music_assistant.client.ui.compose.library.LibraryListViewModel
import io.music_assistant.client.ui.compose.library.LibraryScreen
import io.music_assistant.client.ui.compose.library.LibraryScreenState
import io.music_assistant.client.ui.compose.nav.AdaptiveNavigationBarLayout
import io.music_assistant.client.ui.compose.nav.BackHandler
import io.music_assistant.client.ui.compose.nav.ConditionalBackNavDisplay
import io.music_assistant.client.ui.compose.nav.MultiBackStack
import io.music_assistant.client.ui.compose.nav.NavigationItem
import io.music_assistant.client.ui.compose.nav.ScreenState
import io.music_assistant.client.ui.compose.nav.createNavigationItem
import io.music_assistant.client.ui.compose.search.GlobalSearchRequest
import io.music_assistant.client.ui.compose.search.SearchScreen
import io.music_assistant.client.ui.compose.search.SearchScreenState
import io.music_assistant.client.ui.compose.search.SearchViewModel
import io.music_assistant.client.utils.DataConnectionState
import io.music_assistant.client.utils.SessionState
import io.music_assistant.client.utils.isTelevisionDevice
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.nav_home
import musicassistantclient.composeapp.generated.resources.nav_library
import musicassistantclient.composeapp.generated.resources.nav_search
import musicassistantclient.composeapp.generated.resources.nav_settings
import musicassistantclient.composeapp.generated.resources.players_remote_volume_hint
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

// Android TV: a fresh Nav.Main root (cold start, or returning from Settings via the back arrow)
// doesn't reliably get an initial focus grant once the window already holds focus, leaving the
// remote dead until a D-pad press. On a cold start the app window can sit unfocused for several
// seconds (auto-login splash / first-frame cold start), and every requestFocus in that window is
// a no-op, so retry until the active nav item actually reports focus. While the view is in touch
// mode (cold start, or after a mouse/touch click) requestFocus is also a no-op, so the loop asks
// the input mode manager to leave touch mode first. The loop exits on the first success, so once
// the user can actually interact the requests stop immediately (mirrors the SettingsScreen /
// PlayersPager cold-start handling).
private const val HOME_FOCUS_RETRIES = 40
private const val HOME_FOCUS_RETRY_DELAY = 250L

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MainNavigationRoot(
    homeScreenViewModel: HomeScreenViewModel = koinViewModel(),
    actionsViewModel: ActionsViewModel = koinViewModel(),
    viewModeViewModel: ViewModeViewModel = koinViewModel(),
    dspSettingsViewModel: DspSettingsViewModel = koinViewModel(),
    goToSettings: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val toastState = rememberToastState()
    val errorBus: ErrorMessageBus = koinInject()
    val deepLinkBus: DeepLinkBus = koinInject()
    val volumeButtonService: VolumeButtonService = koinInject()

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

    // Queue panel visibility inside the expanded player. Hoisted from PlayersPager so the
    // "players" deep link (notification / TV now-playing card) can land directly on the
    // queue with the transport controls visible instead of the collapsed-controls view.
    var isQueueExpanded by remember { mutableStateOf(false) }

    // TV: the collapsed FloatingBar Surface is the D-pad's entry point to the player.
    // Expanding removes that focused Surface and the expanded layout grants no focus
    // (remote goes dead), so restore focus on the bar when collapsing back.
    val isTv = isTelevisionDevice()
    val floatingBarFocusRequester = remember { FocusRequester() }
    var wasPlayerExpanded by remember { mutableStateOf(false) }
    LaunchedEffect(playerExpanded) {
        if (isTv && !playerExpanded && wasPlayerExpanded) {
            floatingBarFocusRequester.requestFocus()
        }
        wasPlayerExpanded = playerExpanded
    }

    // TV: land initial focus on the active nav item whenever this root becomes the visible
    // destination. A fresh Nav.Main (cold start, or returning from Settings via the back arrow)
    // doesn't reliably get a focus grant once the window already holds focus, leaving the remote
    // dead until a D-pad press. The Nav.Main entry is torn down and recreated on return from
    // Settings, so keying the landing on the entry lifecycle (RESUMED = Main is on top again)
    // re-runs it on every return. The expanded player requests its own focus when shown
    // (PlayersPager).
    //
    // Compose's FocusRequester.requestFocus() is a no-op while the Android view is in touch mode
    // (a cold start or a mouse/touch click leaves it there): with nothing currently focused the
    // focus transaction first asks the owner view for focus, and View.requestFocus() declines
    // while in touch mode, so the request returns false every attempt. requestInputMode(Keyboard)
    // exits touch mode (requestFocusFromTouch() on Android) so the follow-up request can land.
    val navEntryLifecycleOwner = LocalLifecycleOwner.current
    val inputModeManager = LocalInputModeManager.current
    val selectedNavItemFocusRequester = remember { FocusRequester() }
    val selectedNavItemFocused = remember { mutableStateOf(false) }
    LaunchedEffect(navEntryLifecycleOwner) {
        navEntryLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (!isTv) return@repeatOnLifecycle
            selectedNavItemFocused.value = false
            var attempts = 0
            while (attempts++ < HOME_FOCUS_RETRIES && !playerExpanded && !selectedNavItemFocused.value) {
                inputModeManager.requestInputMode(InputMode.Keyboard)
                selectedNavItemFocusRequester.requestFocus()
                delay(HOME_FOCUS_RETRY_DELAY)
            }
        }
    }

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
            ?.dataConnectionState is DataConnectionState.Authenticated
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
                dest.mediaType?.let { multiBackStack.add(MainNav.LibraryList(it)) }
            }

            DeepLinkDestination.Search -> {
                multiBackStack.currentBackStack = 2
                multiBackStack.resetCurrentBackStack()
            }

            DeepLinkDestination.Players -> {
                // Expand the now-playing layout over the current tab (the
                // FloatingBar is global, so no tab switch needed). The pager
                // renders its own empty state if no player is present. Open the
                // queue too: this path is the "return to a playing app" entry
                // (notification tap / TV now-playing card), where the queue
                // alongside the transport is the useful landing.
                playerExpanded = true
                isQueueExpanded = true
            }
        }
        deepLinkBus.consume(dest)
    }

    // Each root screen's scroll/collapsing-top-bar state is owned by its NavEntry
    // (published here while composed) so its lifetime matches the ViewModel it
    // mirrors. The nav bar reads these to scroll the active tab to top on re-tap;
    // a tab switch disposes the entry, so re-entry starts fresh instead of stranding
    // a collapsed top bar.
    val homeScreenState = remember { mutableStateOf<HomeScreenState?>(null) }
    val libraryScreenState = remember { mutableStateOf<LibraryScreenState?>(null) }
    val searchScreenState = remember { mutableStateOf<SearchScreenState?>(null) }

    val remoteVolumeHint = stringResource(Res.string.players_remote_volume_hint)
    val viewingRemote = data?.selectedPlayer?.isLocal == false
    val currentHint by rememberUpdatedState(remoteVolumeHint)
    val observingRemote by rememberUpdatedState(viewingRemote)
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner, volumeButtonService) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            volumeButtonService.buttonPresses.collect {
                if (observingRemote) {
                    toastState.showToast(currentHint, ToastDuration.SHORT)
                }
            }
        }
    }

    val navigationItems = listOf(
        multiBackStack.createNavigationItem(
            backStack = 0,
            icon = Icons.Default.Home,
            label = stringResource(Res.string.nav_home),
            screenState = homeScreenState.value,
        ),
        multiBackStack.createNavigationItem(
            backStack = 1,
            icon = Icons.Default.LibraryMusic,
            label = stringResource(Res.string.nav_library),
            screenState = libraryScreenState.value,
        ),
        multiBackStack.createNavigationItem(
            backStack = 2,
            icon = Icons.Default.Search,
            label = stringResource(Res.string.nav_search),
            screenState = searchScreenState.value,
        ),
        NavigationItem(
            selected = false,
            onClick = { goToSettings() },
            Icons.Default.Settings,
            label = stringResource(Res.string.nav_settings),
        ),
    )

    Box(modifier = Modifier.fillMaxSize()) {
        AdaptiveNavigationBarLayout(
            showNavigation = !playerExpanded,
            navigationItems = navigationItems,
            selectedItemFocusRequester = selectedNavItemFocusRequester,
            selectedItemFocused = selectedNavItemFocused,
        ) { scaffoldContentPadding ->
            FloatingBarLayout(
                modifier = Modifier.padding(scaffoldContentPadding),
                floatingBar = {
                    FloatingBar(
                        expanded = playerExpanded,
                        onExpand = onExpandPlayer,
                        focusRequester = floatingBarFocusRequester,
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
                                isQueueExpanded = isQueueExpanded,
                                onExpandQueue = { isQueueExpanded = it },
                                navigateToItem = { item ->
                                    multiBackStack.add(
                                        MainNav.ItemDetails(
                                            itemId = item.itemId,
                                            mediaType = item.mediaType,
                                            providerId = item.provider,
                                        ),
                                    )
                                },
                            )
                        },
                    )
                },
            ) { floatingBarContentPadding ->
                BackHandler(playerExpanded) {
                    playerExpanded = !playerExpanded
                }

                ConditionalBackNavDisplay(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                    entries = rememberDecoratedNavEntries(
                        entryDecorators = listOf(
                            rememberSaveableStateHolderNavEntryDecorator(),
                            rememberViewModelStoreNavEntryDecorator(),
                        ),
                        entries = multiBackStack.toEntries(
                            mainNavEntryProvider(
                                floatingBarContentPadding,
                                multiBackStack,
                                homeScreenViewModel,
                                actionsViewModel,
                                viewModeViewModel,
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
        ToastHost(toastState = toastState)
    }
}

@Composable
private fun mainNavEntryProvider(
    contentPadding: PaddingValues,
    multiBackStack: MultiBackStack<NavKey>,
    homeScreenViewModel: HomeScreenViewModel,
    actionsViewModel: ActionsViewModel,
    viewModeViewModel: ViewModeViewModel,
    homeScreenState: MutableState<HomeScreenState?>,
    libraryScreenState: MutableState<LibraryScreenState?>,
    searchScreenState: MutableState<SearchScreenState?>,
): (NavKey) -> NavEntry<NavKey> {
    // Hoisted here (outlives the per-NavEntry SearchViewModel) to carry an empty-quick-search
    // escalation from the library tab to the Search tab. Set by ItemList, consumed by SearchScreen.
    var pendingSearch by remember { mutableStateOf<GlobalSearchRequest?>(null) }
    return entryProvider {
        entry<MainNav.Landing> {
            val screenState = rememberPublishedScreenState(homeScreenState) {
                HomeScreenState.create()
            }
            HomeScreen(
                homeScreenViewModel,
                contentPadding = contentPadding,
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
                providerIconFetcher = { modifier, provider ->
                    actionsViewModel.getProviderIcon(provider)
                        ?.let { ProviderIcon(modifier, it) }
                },
                actionsViewModel = actionsViewModel,
                state = screenState,
            )
        }

        entry<MainNav.Library> {
            val libraryCategoriesViewModel = koinViewModel<LibraryCategoriesViewModel>()
            val screenState = rememberPublishedScreenState(libraryScreenState) {
                LibraryScreenState.create()
            }

            LibraryScreen(
                libraryCategoriesViewModel,
                contentPadding = contentPadding,
                state = screenState,
                onCategoryClick = { category ->
                    if (category == LibraryCategory.BROWSE) {
                        multiBackStack.add(MainNav.Browse(path = null, title = null))
                    } else {
                        category.mediaType?.let { multiBackStack.add(MainNav.LibraryList(it)) }
                    }
                },
            )
        }

        entry<MainNav.LibraryList> {
            val libraryListViewModel = koinViewModel<LibraryListViewModel> {
                parametersOf(it.mediaType)
            }

            LibraryListScreen(
                libraryListViewModel = libraryListViewModel,
                viewModeViewModel = viewModeViewModel,
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

        entry<MainNav.ItemList> {
            val itemListViewModel = koinViewModel<ItemListViewModel> {
                parametersOf(it.itemList)
            }

            ItemListScreen(
                title = it.title,
                mediaType = it.itemList.mediaType,
                itemListViewModel = itemListViewModel,
                viewModeViewModel = viewModeViewModel,
                actionsViewModel = actionsViewModel,
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
                onBack = { multiBackStack.removeLastOrNull() },
                contentPadding = contentPadding,
                clickContext = it.clickContext,
            )
        }

        entry<MainNav.ItemDetails> {
            val itemDetailsViewModel = koinViewModel<ItemDetailsViewModel> {
                parametersOf(it.itemId, it.mediaType, it.providerId)
            }

            ItemDetailsScreen(
                itemDetailsViewModel = itemDetailsViewModel,
                viewModeViewModel = viewModeViewModel,
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
                onNavigateToList = { title, itemList, clickContext ->
                    multiBackStack.add(MainNav.ItemList(title, itemList, clickContext))
                },
                contentPadding = contentPadding,
            )
        }

        entry<MainNav.Search> {
            val searchViewModel = koinViewModel<SearchViewModel>()
            val screenState = rememberPublishedScreenState(searchScreenState) {
                SearchScreenState.create()
            }

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
                state = screenState,
                pendingSearch = pendingSearch,
                onSearchConsumed = { pendingSearch = null },
            )
        }
    }
}

/**
 * Creates a root screen's [ScreenState] scoped to the calling NavEntry and publishes it to a
 * root-level [holder] while composed, so the navigation bar can drive scroll-to-top on re-tap
 * without hoisting the state above the entry (which would desync it from the entry's ViewModel).
 */
@Composable
private fun <S : ScreenState> rememberPublishedScreenState(
    holder: MutableState<S?>,
    create: @Composable () -> S,
): S {
    val screenState = create()
    DisposableEffect(screenState) {
        holder.value = screenState
        onDispose { holder.value = null }
    }
    return screenState
}

private sealed interface MainNav : NavKey {
    @Serializable
    data object Landing : MainNav

    @Serializable
    data object Library : MainNav

    @Serializable
    data class LibraryList(val mediaType: MediaType) : MainNav

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

    @Serializable
    data class ItemList(
        val title: String,
        val itemList: io.music_assistant.client.ui.compose.item.ItemList,
        val clickContext: ClickContext,
    ) : MainNav
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
                    subclass(MainNav.LibraryList::class, MainNav.LibraryList.serializer())
                    subclass(MainNav.Browse::class, MainNav.Browse.serializer())
                    subclass(
                        MainNav.ItemDetails::class,
                        MainNav.ItemDetails.serializer(),
                    )
                    subclass(MainNav.Search::class, MainNav.Search.serializer())
                    subclass(MainNav.ItemList::class, MainNav.ItemList.serializer())
                }
            }
        },
    ),
    bottom,
)
