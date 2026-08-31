package io.music_assistant.client.ui.compose.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.music_assistant.client.ui.compose.nav.TopBarLayout
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.ai_radio_empty
import musicassistantclient.composeapp.generated.resources.ai_radio_load_failed
import musicassistantclient.composeapp.generated.resources.ai_radio_no_player
import musicassistantclient.composeapp.generated.resources.ai_radio_stop
import musicassistantclient.composeapp.generated.resources.ai_radio_title
import musicassistantclient.composeapp.generated.resources.common_back
import org.jetbrains.compose.resources.stringResource

/**
 * Library section for the optional `ai_radio` plugin. Reached only while
 * [io.music_assistant.client.data.MainDataSource.aiRadioAvailable] is true, which is what hides
 * the category when the plugin is absent or the user lacks its scope.
 *
 * Stations are authored in the web frontend; this only lists and runs them. The provider emits
 * no events, so the run state is re-read on entry and after a start or stop. Do NOT add a
 * background poll — it would hit the server forever for a screen the user rarely opens.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiRadioScreen(
    viewModel: AiRadioViewModel,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val hasTargetPlayer by viewModel.hasTargetPlayer.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.load() }

    TopBarLayout(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.ai_radio_title)) },
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
        AiRadioStationList(
            state = state,
            hasTargetPlayer = hasTargetPlayer,
            contentPadding = contentPadding,
            onStart = viewModel::start,
            onStop = viewModel::stop,
        )
    }
}

@Composable
private fun AiRadioStationList(
    state: AiRadioViewModel.State,
    hasTargetPlayer: Boolean,
    contentPadding: PaddingValues,
    onStart: (String) -> Unit,
    onStop: (String) -> Unit,
) {
    when (state) {
        is AiRadioViewModel.State.Loading -> Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }

        is AiRadioViewModel.State.Failed -> ScreenMessage(
            stringResource(Res.string.ai_radio_load_failed),
        )

        is AiRadioViewModel.State.Ready -> when {
            // Distinct from a failed load: nothing is wrong, there is just nothing to play
            // until the user authors a station in the web frontend.
            state.stations.isEmpty() -> ScreenMessage(stringResource(Res.string.ai_radio_empty))

            // Said up front rather than left to fail on tap: the server's own error would be
            // about a missing player id, which tells the user nothing they can act on.
            !hasTargetPlayer -> ScreenMessage(stringResource(Res.string.ai_radio_no_player))

            else -> LazyColumn(contentPadding = contentPadding) {
                items(state.stations, key = { it.id }) { station ->
                    StationRow(
                        name = station.name,
                        runningSessionId = state.running
                            ?.takeIf { it.stationId == station.id }
                            ?.sessionId,
                        onClick = { onStart(station.id) },
                        onStop = onStop,
                    )
                }
            }
        }
    }
}

@Composable
private fun StationRow(
    name: String,
    runningSessionId: String?,
    onClick: () -> Unit,
    onStop: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // A running station has nothing to start, so only Stop stays clickable.
            .clickable(enabled = runningSessionId == null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        runningSessionId?.let { sessionId ->
            TextButton(onClick = { onStop(sessionId) }) {
                Text(stringResource(Res.string.ai_radio_stop))
            }
        }
    }
}

@Composable
private fun ScreenMessage(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 32.dp),
        )
    }
}
