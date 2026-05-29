package io.music_assistant.client.ui.compose.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.music_assistant.client.data.model.client.MediaType
import io.music_assistant.client.ui.compose.nav.Screen
import io.music_assistant.client.utils.libraryItemMinWidth
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.cd_customize_tabs
import musicassistantclient.composeapp.generated.resources.nav_library
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    libraryCategoriesViewModel: LibraryCategoriesViewModel,
    contentPadding: PaddingValues,
    onTypeClick: (MediaType) -> Unit,
) {
    val state by libraryCategoriesViewModel.state.collectAsStateWithLifecycle()

    var showCustomizeDialog by remember { mutableStateOf(false) }
    if (showCustomizeDialog) {
        CustomizeLibraryCategoriesDialog(
            initialConfig = state.categories.map { it.libraryCategory to it.enabled },
            onDismissRequest = { showCustomizeDialog = false },
            onConfirm = libraryCategoriesViewModel::onTabsConfigChanged,
        )
    }

    val gridState = rememberLazyGridState()

    Screen(
        topBar = { scrollBehavior ->
            TopAppBar(
                title = { Text(stringResource(Res.string.nav_library)) },
                actions = {
                    IconButton(onClick = { showCustomizeDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = stringResource(Res.string.cd_customize_tabs),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) {
        val libraryCategories = remember(state.categories) {
            state.categories.filter { it.enabled }.map { it.libraryCategory }
        }

        LibraryGrid(
            gridState = gridState,
            paddingValues = contentPadding,
            libraryCategories = libraryCategories,
            onTypeClick = onTypeClick,
        )
    }
}

@Composable
private fun LibraryGrid(
    gridState: LazyGridState = rememberLazyGridState(),
    paddingValues: PaddingValues,
    libraryCategories: List<LibraryCategory>,
    onTypeClick: (MediaType) -> Unit,
) {
    val width = libraryItemMinWidth()
    LazyVerticalGrid(
        modifier = Modifier.padding(horizontal = 16.dp),
        state = gridState,
        columns = GridCells.Adaptive(minSize = width),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = paddingValues,
    ) {
        items(libraryCategories) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = { onTypeClick(it.mediaType) })
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(16.dp),
            ) {
                Row(
                    modifier = Modifier.height(width / ITEM_HEIGHT_RATIO),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Icon(
                        modifier = Modifier.size(30.dp),
                        imageVector = it.icon(),
                        contentDescription = it.name,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )

                    Text(
                        text = stringResource(it.stringResource()),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
    }
}

private const val ITEM_HEIGHT_RATIO = 4

@Preview
@Composable
private fun LibraryGridPreview() {
    LibraryGrid(
        libraryCategories = LibraryCategory.entries,
        paddingValues = PaddingValues(vertical = 16.dp),
    ) {}
}
