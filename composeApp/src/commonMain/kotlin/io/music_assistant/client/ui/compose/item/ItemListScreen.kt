package io.music_assistant.client.ui.compose.item

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.music_assistant.client.data.model.client.ClickContext
import io.music_assistant.client.data.model.client.MediaType
import io.music_assistant.client.data.model.client.SubItemContext
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.ui.compose.common.items.ItemSortChip
import io.music_assistant.client.ui.compose.common.viewmodel.ActionsViewModel
import io.music_assistant.client.ui.compose.library.ItemListContent
import io.music_assistant.client.ui.compose.nav.TopBarLayout
import io.music_assistant.client.ui.compose.nav.TwoRowTopAppBar
import io.music_assistant.client.ui.compose.search.SearchInput
import io.music_assistant.client.ui.compose.search.SearchInputMode
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.cd_close
import musicassistantclient.composeapp.generated.resources.cd_find_in_list
import musicassistantclient.composeapp.generated.resources.common_back
import org.jetbrains.compose.resources.stringResource

@Composable
fun ItemListScreen(
    title: String,
    mediaType: MediaType,
    sortContext: SubItemContext,
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
            val query = state.query
            TwoRowTopAppBar(
                title = {
                    query?.let {
                        SearchInput(
                            mode = SearchInputMode.FIND_IN_LIST,
                            query = it,
                            onQueryChanged = itemListViewModel::filter,
                        )
                    } ?: Text(title)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            stringResource(Res.string.common_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { itemListViewModel.filter(if (query == null) "" else null) }) {
                        if (query == null) {
                            Icon(Icons.Default.FindInPage, stringResource(Res.string.cd_find_in_list))
                        } else {
                            Icon(Icons.Default.Close, stringResource(Res.string.cd_close))
                        }
                    }
                },
                secondRow = {
                    ItemSortChip(
                        sortOption = state.sortOption,
                        sortContext = sortContext,
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
        ItemListContent(
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
