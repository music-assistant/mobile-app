@file:Suppress("MagicNumber")

package io.music_assistant.client.ui.compose.library

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import compose.icons.TablerIcons
import compose.icons.tablericons.GripVertical
import io.music_assistant.client.ui.compose.common.GridButton
import io.music_assistant.client.ui.compose.common.moveToEnabledBoundary
import io.music_assistant.client.ui.compose.nav.BackHandler
import io.music_assistant.client.ui.compose.nav.ScreenState
import io.music_assistant.client.ui.compose.nav.TopBarLayout
import io.music_assistant.client.utils.libraryItemMinWidth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.cd_customize_tabs
import musicassistantclient.composeapp.generated.resources.common_done
import musicassistantclient.composeapp.generated.resources.nav_library
import org.jetbrains.compose.resources.stringResource
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.ReorderableLazyGridState
import sh.calvin.reorderable.rememberReorderableLazyGridState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    libraryCategoriesViewModel: LibraryCategoriesViewModel,
    contentPadding: PaddingValues,
    state: LibraryScreenState,
    onCategoryClick: (LibraryCategory) -> Unit,
) {
    val categoriesState by libraryCategoriesViewModel.state.collectAsStateWithLifecycle()

    // Edit-mode working copy — isolated from live settings updates while editing;
    // snapshotted fresh on entering edit mode.
    var items by remember { mutableStateOf(categoriesState.categories.map { it.libraryCategory to it.enabled }) }
    val enabledCount = items.count { it.second }

    var editMode by remember { mutableStateOf(false) }
    val displayed = if (editMode) {
        items
    } else {
        categoriesState.categories.filter { it.enabled }.map { it.libraryCategory to true }
    }

    val reorderableGridState = rememberReorderableLazyGridState(state.lazyGridState) { from, to ->
        // Constrain reorder to the contiguous enabled section.
        if (from.index >= enabledCount || to.index >= enabledCount) return@rememberReorderableLazyGridState
        items = items.toMutableList().apply { add(to.index, removeAt(from.index)) }
    }

    BackHandler(enabled = editMode) { editMode = false }

    TopBarLayout(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.nav_library)) },
                actions = {
                    IconButton(
                        onClick = {
                            if (editMode) {
                                libraryCategoriesViewModel.onTabsConfigChanged(items)
                                editMode = false
                            } else {
                                items = categoriesState.categories.map { it.libraryCategory to it.enabled }
                                editMode = true
                            }
                        },
                    ) {
                        Icon(
                            imageVector = if (editMode) Icons.Default.Done else Icons.Default.Edit,
                            contentDescription = stringResource(
                                if (editMode) Res.string.common_done else Res.string.cd_customize_tabs,
                            ),
                        )
                    }
                },
            )
        },
        topAppBarState = state.topAppBarState,
    ) {
        LibraryGrid(
            gridState = state.lazyGridState,
            paddingValues = contentPadding,
            categories = displayed,
            editMode = editMode,
            enabledCount = enabledCount,
            reorderableGridState = reorderableGridState,
            onToggle = { category, enabled -> items = moveToEnabledBoundary(items, category, enabled) },
            onCategoryClick = onCategoryClick,
        )
    }
}

@Composable
private fun LibraryGrid(
    gridState: LazyGridState = rememberLazyGridState(),
    paddingValues: PaddingValues,
    categories: List<Pair<LibraryCategory, Boolean>>,
    editMode: Boolean = false,
    enabledCount: Int = categories.size,
    reorderableGridState: ReorderableLazyGridState? = null,
    onToggle: (LibraryCategory, Boolean) -> Unit = { _, _ -> },
    onCategoryClick: (LibraryCategory) -> Unit,
) {
    val width = libraryItemMinWidth()
    val tileHeight = width / ITEM_HEIGHT_RATIO
    LazyVerticalGrid(
        modifier = Modifier.padding(horizontal = 16.dp),
        state = gridState,
        columns = GridCells.Adaptive(minSize = width),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = paddingValues,
    ) {
        items(
            items = categories,
            key = { it.first.name },
        ) { (category, enabled) ->
            val button: @Composable (Modifier, () -> Unit) -> Unit = { modifier, onClick ->
                GridButton(
                    modifier = modifier,
                    text = stringResource(category.stringResource()),
                    icon = category.icon(),
                    onClick = onClick,
                )
            }
            if (editMode && reorderableGridState != null) {
                ReorderableItem(reorderableGridState, key = category.name) {
                    Box(modifier = Modifier.fillMaxWidth().height(tileHeight)) {
                        button(Modifier.fillMaxSize()) {}
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(
                                    MaterialTheme.colorScheme.background.copy(
                                        alpha = if (enabled) 0.60f else 0.80f,
                                    ),
                                )
                                .pointerInput(Unit) {
                                    // Swallow taps & long-presses; vertical drags pass through.
                                    detectTapGestures(onTap = {}, onLongPress = {})
                                },
                        )
                        Row(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Keep at least one category visible: block disabling the last enabled one.
                            Switch(
                                checked = enabled,
                                enabled = !enabled || enabledCount > 1,
                                onCheckedChange = { onToggle(category, it) },
                            )
                            Icon(
                                modifier = Modifier
                                    .padding(start = 12.dp)
                                    .then(if (enabled) Modifier.draggableHandle() else Modifier)
                                    .alpha(if (enabled) 1f else 0.3f)
                                    .size(20.dp),
                                imageVector = TablerIcons.GripVertical,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                            )
                        }
                    }
                }
            } else {
                button(Modifier.fillMaxWidth().height(tileHeight)) { onCategoryClick(category) }
            }
        }
    }
}

class LibraryScreenState(
    val topAppBarState: TopAppBarState,
    val lazyGridState: LazyGridState,
    val coroutineScope: CoroutineScope,
) : ScreenState {
    override fun reset() {
        topAppBarState.heightOffset = 0f
        coroutineScope.launch {
            lazyGridState.animateScrollToItem(0)
        }
    }

    companion object {
        @Composable
        fun create(): LibraryScreenState {
            return LibraryScreenState(
                rememberTopAppBarState(),
                rememberLazyGridState(),
                rememberCoroutineScope(),
            )
        }
    }
}

private const val ITEM_HEIGHT_RATIO = 3

@Preview
@Composable
private fun LibraryGridPreview() {
    LibraryGrid(
        paddingValues = PaddingValues(vertical = 16.dp),
        categories = LibraryCategory.entries.map { it to true },
    ) {}
}
