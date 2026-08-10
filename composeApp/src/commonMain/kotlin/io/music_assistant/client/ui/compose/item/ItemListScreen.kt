package io.music_assistant.client.ui.compose.item

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.music_assistant.client.data.model.client.ClickContext
import io.music_assistant.client.data.model.client.MediaType
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.ui.compose.common.items.ItemSortChip
import io.music_assistant.client.ui.compose.common.viewmodel.ActionsViewModel
import io.music_assistant.client.ui.compose.library.ItemList
import io.music_assistant.client.ui.compose.nav.TopBarLayout
import io.music_assistant.client.ui.compose.nav.TwoRowTopAppBar
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.common_back
import org.jetbrains.compose.resources.stringResource

@Composable
fun ItemListScreen(
    title: String,
    mediaType: MediaType,
    itemListViewModel: ItemListViewModel,
    viewModeViewModel: ViewModeViewModel,
    actionsViewModel: ActionsViewModel,
    onNavigateClick: (AppMediaItem) -> Unit,
    onBack: () -> Unit,
    contentPadding: PaddingValues,
    clickContext: ClickContext,
) {
    val state by itemListViewModel.state.collectAsStateWithLifecycle()
    val viewMode by viewModeViewModel.viewModeFor(mediaType).collectAsStateWithLifecycle()

    TopBarLayout(
        topBar = {
            TwoRowTopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            stringResource(Res.string.common_back),
                        )
                    }
                },
                secondRow = {
                    ItemSortChip(
                        sortOption = state.sortOption,
                        mediaType = mediaType,
                        onSortChanged = itemListViewModel::sort,
                    )

                    ViewModeToggle(
                        viewMode = viewMode,
                        onToggleViewMode = { viewModeViewModel.toggleFor(mediaType) },
                    )
                },
            )
        },
    ) {
        ItemList(
            data = state.items,
            onNavigateClick = onNavigateClick,
            onPlayClick = { item, option, radio, _ ->
                actionsViewModel.onPlayClick(item, option, radio)
            },
            playlistActions = actionsViewModel,
            libraryActions = actionsViewModel,
            progressActions = actionsViewModel,
            contentPadding = contentPadding,
            viewMode = viewMode,
            clickContext = clickContext,
        )
    }
}
