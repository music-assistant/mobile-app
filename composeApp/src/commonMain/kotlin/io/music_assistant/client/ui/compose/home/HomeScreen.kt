@file:OptIn(ExperimentalMaterial3Api::class)
@file:Suppress("MagicNumber")

package io.music_assistant.client.ui.compose.home

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import compose.icons.TablerIcons
import compose.icons.tablericons.GripVertical
import io.music_assistant.client.data.model.client.ClickContext
import io.music_assistant.client.data.model.client.Shortcut
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
import io.music_assistant.client.settings.SettingsRepository
import io.music_assistant.client.ui.compose.common.CenteredProgress
import io.music_assistant.client.ui.compose.common.CenteredText
import io.music_assistant.client.ui.compose.common.DataState
import io.music_assistant.client.ui.compose.common.items.CategoryRow
import io.music_assistant.client.ui.compose.common.items.ItemCategory
import io.music_assistant.client.ui.compose.common.items.ProvideClickActions
import io.music_assistant.client.ui.compose.common.items.lazyListKey
import io.music_assistant.client.ui.compose.common.moveToEnabledBoundary
import io.music_assistant.client.ui.compose.common.toDisplayString
import io.music_assistant.client.ui.compose.common.viewmodel.ActionsViewModel
import io.music_assistant.client.ui.compose.nav.BackHandler
import io.music_assistant.client.ui.compose.nav.ScreenState
import io.music_assistant.client.ui.compose.nav.TopBarLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.home_edit_rows
import musicassistantclient.composeapp.generated.resources.home_save_rows
import musicassistantclient.composeapp.generated.resources.home_shortcuts
import musicassistantclient.composeapp.generated.resources.library_error
import musicassistantclient.composeapp.generated.resources.nav_home
import musicassistantclient.composeapp.generated.resources.refresh
import org.jetbrains.compose.resources.stringResource
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun HomeScreen(
    homeScreenViewModel: HomeScreenViewModel,
    contentPadding: PaddingValues,
    onNavigateClick: (AppMediaItem) -> Unit,
    providerIconFetcher: (@Composable (Modifier, String) -> Unit),
    actionsViewModel: ActionsViewModel,
    state: HomeScreenState,
    // Android TV: routes D-pad DOWN out of the last row to the persistent mini-player, which
    // otherwise isn't reachable via D-pad (it's a separate overlay, not an ordinary sibling).
    floatingBarFocusRequester: FocusRequester? = null,
) {
    val homeScreenState by homeScreenViewModel.state.collectAsStateWithLifecycle()

    // Reconciled, enabled-first ordering. Authoritative for normal-mode display.
    val recommendationsState = homeScreenState.recommendations
    val shortcutsState = homeScreenState.shortcuts
    val homeRowsConfig = homeScreenState.homeRowsConfig
    val working = remember(recommendationsState, shortcutsState, homeRowsConfig) {
        getCategories(recommendationsState, shortcutsState, homeRowsConfig)
    }

    // Edit-mode working copy — isolated from external (real-time) updates while editing;
    // snapshotted fresh on entering edit mode.
    var items by remember { mutableStateOf(working) }
    val enabledCount = items.count { it.second }
    val enabledByKey =
        remember(items) { items.associate { it.first.category.lazyListKey to it.second } }

    var editMode by remember { mutableStateOf(false) }
    val displayedData =
        if (editMode) items.map { it.first } else working.filter { it.second }.map { it.first }

    val reorderableState = rememberReorderableLazyListState(state.lazyListState) { from, to ->
        // Constrain reorder to the contiguous enabled section.
        if (from.index >= enabledCount || to.index >= enabledCount) return@rememberReorderableLazyListState
        items = items.toMutableList().apply { add(to.index, removeAt(from.index)) }
    }

    BackHandler(enabled = editMode) { editMode = false }

    TopBarLayout(
        topBar = {
            LandingPageTopBar(
                editMode = editMode,
                onRefresh = { homeScreenViewModel.loadData() },
                onToggleEditMode = {
                    if (editMode) {
                        homeScreenViewModel.saveHomeRows(
                            items.map {
                                SettingsRepository.HomeRowPref(
                                    it.first.category.id,
                                    it.second,
                                )
                            },
                        )
                        editMode = false
                    } else {
                        items = working
                        editMode = true
                    }
                },
            )
        },
        topAppBarState = state.topAppBarState,
    ) {
        if (recommendationsState is DataState.Loading) {
            CenteredProgress()
        } else if (recommendationsState !is DataState.Data) {
            CenteredText(
                text = stringResource(Res.string.library_error),
                color = MaterialTheme.colorScheme.error,
            )
        } else {
            val lastRowKey = displayedData.lastOrNull()?.category?.lazyListKey
            val rowContent: @Composable (HomeRow) -> Unit = { row ->
                CategoryRow(
                    data = if (row.loading) DataState.Loading() else DataState.Data(row.category),
                    itemCategoryProvider = { it },
                    onNavigateClick = onNavigateClick,
                    onPlayClick = { item, option, radio, _ ->
                        homeScreenViewModel.onPlayClick(item, option, radio)
                    },
                    playlistActions = actionsViewModel,
                    libraryActions = actionsViewModel,
                    progressActions = actionsViewModel,
                    providerIconFetcher = providerIconFetcher,
                    bottomFocusRequester = if (row.category.lazyListKey == lastRowKey) {
                        floatingBarFocusRequester
                    } else {
                        null
                    },
                )
            }

            ProvideClickActions(ClickContext.HOME) {
                LazyColumn(
                    modifier = Modifier.testTag(HomeScreenSemantics.LIST_TAG),
                    state = state.lazyListState,
                    contentPadding = contentPadding,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(
                        items = displayedData,
                        key = { it.category.lazyListKey },
                    ) { homeRow ->
                        if (editMode) {
                            val enabled = enabledByKey[homeRow.category.lazyListKey] ?: true
                            ReorderableItem(reorderableState, key = homeRow.category.lazyListKey) {
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    rowContent(homeRow)
                                    Box(
                                        modifier = Modifier
                                            .matchParentSize()
                                            .background(
                                                MaterialTheme.colorScheme.background.copy(
                                                    alpha = if (enabled) 0.60f else 0.80f,
                                                ),
                                            )
                                            .pointerInput(Unit) {
                                                // Swallow taps & long-presses on the row;
                                                // vertical drags pass through so the
                                                // LazyColumn can still scroll.
                                                detectTapGestures(onTap = {}, onLongPress = {})
                                            },
                                    )
                                    Row(
                                        modifier = Modifier
                                            .align(Alignment.CenterEnd)
                                            .padding(end = 16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        // Keep at least one row visible: block disabling the last enabled one.
                                        Switch(
                                            checked = enabled,
                                            enabled = !enabled || enabledCount > 1,
                                            onCheckedChange = { newEnabled ->
                                                items =
                                                    moveToEnabledBoundary(
                                                        items,
                                                        homeRow,
                                                        newEnabled,
                                                    )
                                            },
                                        )
                                        Icon(
                                            modifier = Modifier
                                                .padding(start = 12.dp)
                                                .then(
                                                    if (enabled) Modifier.draggableHandle() else Modifier,
                                                )
                                                .alpha(if (enabled) 1f else 0.3f)
                                                .size(24.dp),
                                            imageVector = TablerIcons.GripVertical,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.secondary,
                                        )
                                    }
                                }
                            }
                        } else {
                            rowContent(homeRow)
                        }
                    }
                }
            }
        }
    }
}

/**
 * One reconciled home row: its display/reorder identity ([category], valid before
 * the row's items resolve) plus whether those items are still loading, which
 * [CategoryRow]'s [DataState] overload renders as a placeholder.
 */
internal data class HomeRow(
    val category: ItemCategory<Nothing>,
    val loading: Boolean,
) : IdProvider {
    override val id: String get() = category.id
}

// Internal so HomeScreenCategoriesTest can pin the row visibility rules: loading
// rows stay, resolved-empty rows are hidden.
internal fun getCategories(
    recommendationsState: DataState<List<RecommendationRowState>>,
    shortcutsState: DataState<List<Shortcut>>,
    homeRowsConfig: List<SettingsRepository.HomeRowPref>,
): List<Pair<HomeRow, Boolean>> {
    val baseList = if (recommendationsState is DataState.Data) {
        val recommendations = recommendationsState.data
            // A still-loading row stays visible as a placeholder; a resolved row
            // must contain something the home page can render.
            .filter { row ->
                row.items is DataState.Loading || row.resolvedItems?.any { item ->
                    item is Track ||
                            item is Artist ||
                            item is Album ||
                            item is Playlist ||
                            item is Audiobook ||
                            item is Podcast ||
                            item is PodcastEpisode ||
                            item is RadioStation ||
                            item is Genre
                } == true
            }
            .distinctBy { it.folder.lazyListKey() }
            .map { row ->
                HomeRow(
                    category = ItemCategory(
                        id = row.folder.itemId,
                        title = row.folder.displayName.toDisplayString(),
                        items = row.resolvedItems.orEmpty(),
                        lazyListKey = row.folder.lazyListKey(),
                        tag = HomeScreenSemantics.rowTag(row.folder.itemId),
                    ),
                    loading = row.items is DataState.Loading,
                )
            }

        if (shortcutsState is DataState.Data && shortcutsState.data.isNotEmpty()) {
            val shortcuts = shortcutsState.data
            val shortcutsRow = HomeRow(
                category = ItemCategory(
                    id = SHORTCUTS_CATEGORY_ID,
                    title = Res.string.home_shortcuts.toDisplayString(),
                    items = shortcuts.map { it.item },
                    lazyListKey = SHORTCUTS_CATEGORY_ID,
                    tag = HomeScreenSemantics.SHORTCUTS_ROW_TAG,
                ),
                loading = false,
            )

            recommendations + shortcutsRow
        } else {
            recommendations
        }
    } else {
        emptyList()
    }

    return reconcileHomeRows(baseList, homeRowsConfig, onTop = SHORTCUTS_CATEGORY_ID)
}

private const val SHORTCUTS_CATEGORY_ID = "shortcuts"

@Composable
private fun LandingPageTopBar(
    editMode: Boolean,
    onRefresh: () -> Unit,
    onToggleEditMode: () -> Unit,
) {
    TopAppBar(
        title = { Text(stringResource(Res.string.nav_home)) },
        actions = {
            IconButton(onClick = onToggleEditMode) {
                Icon(
                    imageVector = if (editMode) Icons.Default.Done else Icons.Default.Edit,
                    contentDescription = stringResource(
                        if (editMode) Res.string.home_save_rows else Res.string.home_edit_rows,
                    ),
                )
            }

            if (!editMode) {
                IconButton(onClick = onRefresh) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(Res.string.refresh),
                    )
                }
            }
        },
    )
}

class HomeScreenState(
    val topAppBarState: TopAppBarState,
    val lazyListState: LazyListState,
    val coroutineScope: CoroutineScope,
) : ScreenState {
    override fun reset() {
        coroutineScope.launch {
            topAppBarState.heightOffset = 0f
            lazyListState.animateScrollToItem(0)
        }
    }

    companion object {
        @Composable
        fun create(): HomeScreenState {
            val topAppBarState = rememberTopAppBarState()
            val lazyListState = rememberLazyListState()
            val coroutineScope = rememberCoroutineScope()
            return remember(topAppBarState, lazyListState, coroutineScope) {
                HomeScreenState(topAppBarState, lazyListState, coroutineScope)
            }
        }
    }
}

object HomeScreenSemantics {
    fun rowTag(categoryId: String): String = "Row:$categoryId"
    const val SHORTCUTS_ROW_TAG = "ShortcutsRow"
    const val LIST_TAG = "List"
}
