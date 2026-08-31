package io.music_assistant.client.ui.compose.home.players

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.ai_radio_empty
import musicassistantclient.composeapp.generated.resources.ai_radio_load_failed
import musicassistantclient.composeapp.generated.resources.ai_radio_stop
import musicassistantclient.composeapp.generated.resources.ai_radio_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Picker for the stations of the optional `ai_radio` plugin. Show it only when
 * [io.music_assistant.client.data.MainDataSource.aiRadioAvailable] is true.
 *
 * Stations are authored in the web frontend; this only lists and runs them.
 *
 * @param playerId the PLAYER to run the station on — the server rejects a queue id here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiRadioSheet(
    playerId: String,
    onDismiss: () -> Unit,
    viewModel: AiRadioViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(playerId) { viewModel.load() }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text(
                text = stringResource(Res.string.ai_radio_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            when (val current = state) {
                is AiRadioViewModel.State.Loading -> Box(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }

                is AiRadioViewModel.State.Failed -> SheetMessage(
                    stringResource(Res.string.ai_radio_load_failed),
                )

                is AiRadioViewModel.State.Ready -> if (current.stations.isEmpty()) {
                    // Distinct from a failed load: nothing is wrong, there is just nothing
                    // to play until the user authors a station in the web frontend.
                    SheetMessage(stringResource(Res.string.ai_radio_empty))
                } else {
                    LazyColumn {
                        items(current.stations, key = { it.id }) { station ->
                            val runningId = current.running
                                ?.takeIf { it.stationId == station.id }
                                ?.sessionId
                            StationRow(
                                name = station.name,
                                runningSessionId = runningId,
                                onClick = {
                                    viewModel.start(station.id, playerId, onStarted = onDismiss)
                                },
                                onStop = viewModel::stop,
                            )
                        }
                    }
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
private fun SheetMessage(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp),
    )
}
