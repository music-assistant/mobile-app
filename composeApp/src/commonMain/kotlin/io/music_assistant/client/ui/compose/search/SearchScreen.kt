@file:OptIn(ExperimentalMaterial3Api::class)

package io.music_assistant.client.ui.compose.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.music_assistant.client.data.model.client.ClickContext
import io.music_assistant.client.data.model.client.MediaType
import io.music_assistant.client.data.model.client.items.Album
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.Artist
import io.music_assistant.client.data.model.client.items.Audiobook
import io.music_assistant.client.data.model.client.items.Genre
import io.music_assistant.client.data.model.client.items.Playlist
import io.music_assistant.client.data.model.client.items.Podcast
import io.music_assistant.client.data.model.client.items.RadioStation
import io.music_assistant.client.data.model.client.items.Track
import io.music_assistant.client.data.model.client.stringResource
import io.music_assistant.client.settings.ViewMode
import io.music_assistant.client.ui.compose.common.DataState
import io.music_assistant.client.ui.compose.common.SettingsSheet
import io.music_assistant.client.ui.compose.common.ToastHost
import io.music_assistant.client.ui.compose.common.ToastState
import io.music_assistant.client.ui.compose.common.clearFocusOnScroll
import io.music_assistant.client.ui.compose.common.items.AlbumWithMenu
import io.music_assistant.client.ui.compose.common.items.ArtistWithMenu
import io.music_assistant.client.ui.compose.common.items.AudiobookWithMenu
import io.music_assistant.client.ui.compose.common.items.CategoryRow
import io.music_assistant.client.ui.compose.common.items.GenreWithMenu
import io.music_assistant.client.ui.compose.common.items.LibraryActions
import io.music_assistant.client.ui.compose.common.items.PlayHandler
import io.music_assistant.client.ui.compose.common.items.PlaylistActions
import io.music_assistant.client.ui.compose.common.items.PlaylistWithMenu
import io.music_assistant.client.ui.compose.common.items.PodcastWithMenu
import io.music_assistant.client.ui.compose.common.items.ProgressActions
import io.music_assistant.client.ui.compose.common.items.ProvideClickActions
import io.music_assistant.client.ui.compose.common.items.RadioWithMenu
import io.music_assistant.client.ui.compose.common.items.TrackWithMenu
import io.music_assistant.client.ui.compose.common.items.lazyListOccurrenceKeys
import io.music_assistant.client.ui.compose.common.providers.ProviderIcon
import io.music_assistant.client.ui.compose.common.rememberToastState
import io.music_assistant.client.ui.compose.common.viewmodel.ActionsViewModel
import io.music_assistant.client.ui.compose.library.FilterAction
import io.music_assistant.client.ui.compose.nav.ScreenState
import io.music_assistant.client.ui.compose.nav.TopBarLayout
import io.music_assistant.client.utils.isTelevisionDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.genre_filter_media_type
import musicassistantclient.composeapp.generated.resources.search_error
import musicassistantclient.composeapp.generated.resources.search_in_library_only
import musicassistantclient.composeapp.generated.resources.search_no_results
import musicassistantclient.composeapp.generated.resources.search_start
import org.jetbrains.compose.resources.stringResource

// Android TV: search results load asynchronously after the user submits, so landing D-pad focus
// on them means polling for that data rather than moving focus once, synchronously, before it
// exists (verified: that fails the same way clearing focus does). Same
// poll-over-a-short-window shape as CONFIG_FOCUS_RETRIES in SettingsScreen.kt.
private const val SEARCH_RESULTS_FOCUS_RETRIES = 10
private const val SEARCH_RESULTS_FOCUS_RETRY_DELAY = 250L

@Composable
fun SearchScreen(
    searchViewModel: SearchViewModel,
    onNavigateToItem: (String, MediaType, String) -> Unit,
    actionsViewModel: ActionsViewModel,
    contentPadding: PaddingValues,
    state: SearchScreenState,
    pendingSearch: GlobalSearchRequest? = null,
    onSearchConsumed: () -> Unit = {},
) {
    val searchState by searchViewModel.state.collectAsStateWithLifecycle()
    val toastState = rememberToastState()

    LaunchedEffect(Unit) {
        actionsViewModel.toasts.collect { toast ->
            toastState.showToast(toast)
        }
    }

    // Escalation from an empty in-library quick search (state hoisted in MainNavigationRoot,
    // which outlives the per-NavEntry SearchViewModel). Apply once, then clear.
    LaunchedEffect(pendingSearch) {
        pendingSearch?.let {
            searchViewModel.applyGlobalSearch(it)
            onSearchConsumed()
        }
    }

    // Android TV: the search field and results list aren't otherwise linked, so once results
    // appear D-pad has nothing to move into. A relative focusManager.moveFocus() was tried first
    // and verified live to fail: the on-screen TV keyboard closes on submit and takes Compose's
    // focus state with it, so by the time results exist there's no reliable "current focus" for
    // a relative move to start from (moveFocus ended up landing on the nav rail's default entry
    // point instead). Target the first result directly instead, via a FocusRequester threaded
    // into CategoryRow's first item -- absolute, not relative, so it doesn't depend on whatever
    // did or didn't stay focused after the keyboard closed.
    //
    // A first attempt at this called requestFocus() exactly once, the instant results appeared,
    // and was also verified live to fail even though it threw no exception (proving the
    // requester *was* attached to a real node): the on-screen keyboard's own touch-driven submit
    // leaves the window in touch mode, where View.requestFocus() silently declines every call --
    // the same failure mode already documented and solved for HOME_FOCUS_RETRIES in
    // MainNavRoot.kt. requestInputMode(Keyboard) exits touch mode so the follow-up request can
    // land; mirror that retry shape here instead of a single call.
    val isTv = isTelevisionDevice()
    val inputModeManager = LocalInputModeManager.current
    val firstResultFocusRequester = remember { FocusRequester() }
    var pendingResultsFocus by remember { mutableStateOf(false) }
    if (isTv) {
        LaunchedEffect(pendingResultsFocus) {
            if (!pendingResultsFocus) return@LaunchedEffect
            var attempt = 0
            var hasResults = false
            while (attempt < SEARCH_RESULTS_FOCUS_RETRIES) {
                if (!hasResults) {
                    hasResults = when (val resultsState = searchState.resultsState) {
                        is DataState.Data -> resultsState.data.nonEmptyLists.isNotEmpty()
                        is DataState.Stale -> resultsState.data.nonEmptyLists.isNotEmpty()
                        else -> false
                    }
                }
                if (hasResults) {
                    inputModeManager.requestInputMode(InputMode.Keyboard)
                    firstResultFocusRequester.requestFocus()
                }
                attempt++
                delay(SEARCH_RESULTS_FOCUS_RETRY_DELAY)
            }
            pendingResultsFocus = false
        }
    }

    TopBarLayout(
        topBar = {
            SearchTopBar(
                searchState.searchState,
                onQueryChanged = searchViewModel::onQueryChanged,
                onSearch = {
                    searchViewModel.onSearch()
                    if (isTv) {
                        pendingResultsFocus = true
                    }
                },
                onFiltersChanged = searchViewModel::onFiltersChanged,
            )
        },
        topAppBarState = state.topAppBarState,
    ) {
        ProvideClickActions(ClickContext.SEARCH) {
            SearchContent(
                state = searchState,
                toastState = toastState,
                onItemClick = { item ->
                    when (item) {
                        is Artist,
                        is Album,
                        is Playlist,
                        is Podcast,
                        is Audiobook,
                            -> {
                            onNavigateToItem(item.itemId, item.mediaType, item.provider)
                        }

                        else -> Unit
                    }
                },
                onPlayClick = { track, option, radio, _ ->
                    searchViewModel.onPlayClick(track, option, radio)
                },
                playlistActions = actionsViewModel,
                libraryActions = actionsViewModel,
                progressActions = actionsViewModel,
                providerIconFetcher = { modifier, provider ->
                    actionsViewModel.getProviderIcon(provider)
                        ?.let { ProviderIcon(modifier, it) }
                },
                contentPadding = contentPadding,
                lazyListState = state.lazyListState,
                firstResultFocusRequester = if (isTv) firstResultFocusRequester else null,
            )
        }
    }
}

@Composable
private fun SearchTopBar(
    searchState: SearchViewModel.SearchState,
    onQueryChanged: (String) -> Unit,
    onSearch: () -> Unit,
    onFiltersChanged: (List<MediaType>, Boolean) -> Unit,
) {
    TopAppBar(
        title = {
            SearchInput(
                query = searchState.query,
                onQueryChanged = onQueryChanged,
                onSearch = onSearch,
            )
        },
        actions = {
            SearchFilterAction(
                searchState.mediaTypes.map { it.type },
                searchState.selectedMediaTypes,
                searchState.libraryOnly,
                onFiltersChanged,
            )
        },
    )
}

@Composable
private fun SearchFilterAction(
    mediaTypes: List<MediaType>,
    selectedMediaTypes: List<MediaType>,
    libraryOnly: Boolean,
    onFiltersChanged: (List<MediaType>, Boolean) -> Unit,
) {
    FilterAction(
        state = { SearchFilterState(selectedMediaTypes, libraryOnly) },
        active = selectedMediaTypes.isNotEmpty() || libraryOnly,
        onApply = { onFiltersChanged(it.selectedMediaTypes, it.libraryOnly.value) },
    ) { state ->
        val workingSelectedMediaTypes = state.selectedMediaTypes
        var workingLibraryOnly by state.libraryOnly

        SettingsSheet.MultiChoiceChipsRow(
            label = Res.string.genre_filter_media_type,
            options = mediaTypes,
            selected = workingSelectedMediaTypes.toList(),
            optionLabel = { it.stringResource() },
            onToggle = {
                if (workingSelectedMediaTypes.contains(it)) {
                    workingSelectedMediaTypes.remove(it)
                } else {
                    workingSelectedMediaTypes.add(it)
                }
            },
        )

        SettingsSheet.SwitchRow(
            label = Res.string.search_in_library_only,
            checked = workingLibraryOnly,
            onChange = { workingLibraryOnly = it },
        )
    }
}

private class SearchFilterState(selectedMediaTypes: List<MediaType>, libraryOnly: Boolean) {
    val selectedMediaTypes = selectedMediaTypes.toMutableStateList()
    val libraryOnly = mutableStateOf(libraryOnly)
}

@Composable
private fun SearchContent(
    state: SearchViewModel.State,
    toastState: ToastState,
    onItemClick: (AppMediaItem) -> Unit,
    onPlayClick: PlayHandler<AppMediaItem>,
    playlistActions: PlaylistActions,
    libraryActions: LibraryActions,
    progressActions: ProgressActions? = null,
    providerIconFetcher: (@Composable (Modifier, String) -> Unit),
    contentPadding: PaddingValues,
    lazyListState: LazyListState,
    firstResultFocusRequester: FocusRequester? = null,
) {
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // Results
            when (val resultsState = state.resultsState) {
                is DataState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }

                is DataState.Error -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(Res.string.search_error),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }

                is DataState.Stale,
                is DataState.Data,
                    -> {
                    // Handle both Data and Stale - both contain valid search results
                    val results = when (resultsState) {
                        is DataState.Stale -> resultsState.data
                        is DataState.Data -> resultsState.data
                    }
                    when (results.nonEmptyLists.size) {
                        0 -> Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(stringResource(Res.string.search_no_results))
                        }

                        1 -> LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                                .clearFocusOnScroll(),
                            state = lazyListState,
                            contentPadding = contentPadding,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            val (title, items) = results.nonEmptyLists.first()
                            item {
                                Text(
                                    text = stringResource(title),
                                    style = MaterialTheme.typography.titleLarge,
                                )
                            }
                            item {
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                            val itemKeys = items.lazyListOccurrenceKeys()
                            itemsIndexed(
                                items = items,
                                key = { index, _ -> itemKeys[index] },
                            ) { index, item ->
                                val itemFocusRequester = if (index == 0) firstResultFocusRequester else null
                                when (item) {
                                    is Track -> TrackWithMenu(
                                        viewMode = ViewMode.LIST,
                                        item = item,
                                        onPlayOption = onPlayClick,
                                        libraryActions = libraryActions,
                                        providerIconFetcher = providerIconFetcher,
                                        firstItemFocusRequester = itemFocusRequester,
                                    )

                                    is Artist -> ArtistWithMenu(
                                        viewMode = ViewMode.LIST,
                                        item = item,
                                        onNavigateClick = onItemClick,
                                        onPlayOption = onPlayClick,
                                        libraryActions = libraryActions,
                                        providerIconFetcher = providerIconFetcher,
                                        firstItemFocusRequester = itemFocusRequester,
                                    )

                                    is Album -> AlbumWithMenu(
                                        viewMode = ViewMode.LIST,
                                        item = item,
                                        onNavigateClick = onItemClick,
                                        onPlayOption = onPlayClick,
                                        playlistActions = playlistActions,
                                        libraryActions = libraryActions,
                                        providerIconFetcher = providerIconFetcher,
                                        firstItemFocusRequester = itemFocusRequester,
                                    )

                                    is Playlist -> PlaylistWithMenu(
                                        viewMode = ViewMode.LIST,
                                        item = item,
                                        onNavigateClick = onItemClick,
                                        onPlayOption = onPlayClick,
                                        libraryActions = libraryActions,
                                        providerIconFetcher = providerIconFetcher,
                                        firstItemFocusRequester = itemFocusRequester,
                                    )

                                    is Podcast -> PodcastWithMenu(
                                        viewMode = ViewMode.LIST,
                                        item = item,
                                        onNavigateClick = onItemClick,
                                        onPlayOption = onPlayClick,
                                        libraryActions = libraryActions,
                                        providerIconFetcher = providerIconFetcher,
                                        firstItemFocusRequester = itemFocusRequester,
                                    )

                                    is Audiobook -> AudiobookWithMenu(
                                        viewMode = ViewMode.LIST,
                                        item = item,
                                        onNavigateClick = onItemClick,
                                        onPlayOption = onPlayClick,
                                        playlistActions = playlistActions,
                                        libraryActions = libraryActions,
                                        providerIconFetcher = providerIconFetcher,
                                        firstItemFocusRequester = itemFocusRequester,
                                    )

                                    is RadioStation -> RadioWithMenu(
                                        viewMode = ViewMode.LIST,
                                        item = item,
                                        onPlayOption = onPlayClick,
                                        libraryActions = libraryActions,
                                        providerIconFetcher = providerIconFetcher,
                                        firstItemFocusRequester = itemFocusRequester,
                                    )

                                    is Genre -> GenreWithMenu(
                                        viewMode = ViewMode.LIST,
                                        item = item,
                                        onNavigateClick = onItemClick,
                                        onPlayOption = onPlayClick,
                                        libraryActions = libraryActions,
                                        providerIconFetcher = providerIconFetcher,
                                        firstItemFocusRequester = itemFocusRequester,
                                    )

                                    else -> Unit
                                }
                            }
                        }

                        else -> {
                            val preparedItems = results.nonEmptyLists
                                .map { (title, items) -> Pair(stringResource(title), items) }
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clearFocusOnScroll(),
                                state = lazyListState,
                                contentPadding = contentPadding,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                preparedItems.forEachIndexed { index, (stringTitle, items) ->
                                    if (items.isNotEmpty()) {
                                        item(key = stringTitle, contentType = "category") {
                                            CategoryRow(
                                                title = stringTitle,
                                                onNavigateClick = onItemClick,
                                                onPlayClick = onPlayClick,
                                                mediaItems = items,
                                                playlistActions = playlistActions,
                                                libraryActions = libraryActions,
                                                progressActions = progressActions,
                                                providerIconFetcher = providerIconFetcher,
                                                firstItemFocusRequester = if (index == 0) {
                                                    firstResultFocusRequester
                                                } else {
                                                    null
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                is DataState.NoData -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(stringResource(Res.string.search_start))
                    }
                }
            }
        }

        // Toast host
        ToastHost(
            toastState = toastState,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 48.dp),
        )
    }
}

class SearchScreenState(
    val topAppBarState: TopAppBarState,
    val lazyListState: LazyListState,
    val coroutineScope: CoroutineScope,
) : ScreenState {
    override fun reset() {
        topAppBarState.heightOffset = 0f
        coroutineScope.launch {
            lazyListState.animateScrollToItem(0)
        }
    }

    companion object {
        @Composable
        fun create(): SearchScreenState {
            val topAppBarState = rememberTopAppBarState()
            val lazyListState = rememberLazyListState()
            val coroutineScope = rememberCoroutineScope()
            return remember(topAppBarState, lazyListState, coroutineScope) {
                SearchScreenState(topAppBarState, lazyListState, coroutineScope)
            }
        }
    }
}
