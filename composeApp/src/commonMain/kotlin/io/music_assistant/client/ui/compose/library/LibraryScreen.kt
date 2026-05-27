package io.music_assistant.client.ui.compose.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
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
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.cd_customize_tabs
import musicassistantclient.composeapp.generated.resources.nav_library
import org.jetbrains.compose.resources.stringResource

@Composable
fun LibraryScreen(
    libraryCategoriesViewModel: LibraryCategoriesViewModel,
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

    Screen(
        topBar = {
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
            )
        },
    ) {
        val libraryCategories = remember(state.categories) {
            state.categories.filter { it.enabled }.map { it.libraryCategory }
        }

        LibraryGrid(libraryCategories, onTypeClick)
    }
}

@Composable
private fun LibraryGrid(
    libraryCategories: List<LibraryCategory>,
    onTypeClick: (MediaType) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 140.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
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
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        modifier = Modifier.size(24.dp),
                        imageVector = it.icon(),
                        contentDescription = it.name,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )

                    Text(
                        modifier = Modifier.padding(start = 8.dp),
                        text = stringResource(it.stringResource()),
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun LibraryGridPreview() {
    LibraryGrid(LibraryCategory.entries, {})
}
