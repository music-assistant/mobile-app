package io.music_assistant.client.ui.compose.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.music_assistant.client.data.model.client.ClickContext
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.settings.ViewMode
import io.music_assistant.client.ui.compose.common.DataState
import io.music_assistant.client.ui.compose.common.ToastHost
import io.music_assistant.client.ui.compose.common.items.ProvideClickActions
import io.music_assistant.client.ui.compose.common.rememberToastState
import io.music_assistant.client.ui.compose.common.viewmodel.ActionsViewModel
import io.music_assistant.client.ui.compose.nav.Screen
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.common_back
import musicassistantclient.composeapp.generated.resources.library_empty
import musicassistantclient.composeapp.generated.resources.library_error
import musicassistantclient.composeapp.generated.resources.nav_browse
import org.jetbrains.compose.resources.stringResource

/**
 * Folder-style browser for one level of the server's `music/browse` tree. Folders drill down
 * (handled by [onNavigateClick] at the call site); playable/openable items reuse the shared
 * library item cards via [AdaptiveMediaGrid].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
    browseViewModel: BrowseViewModel,
    title: String?,
    contentPadding: PaddingValues,
    actionsViewModel: ActionsViewModel,
    onNavigateClick: (AppMediaItem) -> Unit,
    onBack: () -> Unit,
) {
    val toastState = rememberToastState()
    LaunchedEffect(Unit) {
        actionsViewModel.toasts.collect { toastState.showToast(it) }
    }

    val dataState by browseViewModel.state.collectAsStateWithLifecycle()

    Screen(
        topBar = {
            TopAppBar(
                title = { Text(title ?: stringResource(Res.string.nav_browse)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.common_back),
                        )
                    }
                },
            )
        },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            ProvideClickActions(ClickContext.BROWSE) {
                when (val state = dataState) {
                    is DataState.Loading -> CenteredContent { CircularProgressIndicator() }

                    is DataState.Error -> CenteredMessage(
                        text = stringResource(Res.string.library_error),
                        color = MaterialTheme.colorScheme.error,
                    )

                    is DataState.NoData -> CenteredMessage(stringResource(Res.string.library_empty))

                    is DataState.Data,
                    is DataState.Stale,
                    -> {
                        val items = state.dataOrNull.orEmpty()
                        if (items.isEmpty()) {
                            CenteredMessage(stringResource(Res.string.library_empty))
                        } else {
                            AdaptiveMediaGrid(
                                modifier = Modifier.fillMaxSize(),
                                items = items,
                                isLoadingMore = false,
                                hasMore = false,
                                viewMode = ViewMode.LIST,
                                onNavigateClick = onNavigateClick,
                                onPlayClick = { item, option, radio, _ ->
                                    browseViewModel.onPlayClick(item, option, radio)
                                },
                                playlistActions = actionsViewModel,
                                libraryActions = actionsViewModel,
                                progressActions = actionsViewModel,
                                contentPadding = contentPadding,
                            )
                        }
                    }
                }
            }

            ToastHost(
                toastState = toastState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 48.dp),
            )
        }
    }
}

@Composable
private fun CenteredContent(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun CenteredMessage(
    text: String,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    CenteredContent {
        Text(text = text, style = MaterialTheme.typography.titleMedium, color = color)
    }
}
