package io.music_assistant.client.ui.compose.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Stop
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import io.music_assistant.client.ui.compose.common.items.RowItem
import io.music_assistant.client.ui.compose.common.items.WavyHexagonShape
import io.music_assistant.client.ui.compose.common.painters.rememberPlaceholderPainter
import io.music_assistant.client.ui.compose.nav.TopBarLayout
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.action_play
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
            onPlay = viewModel::start,
            onStop = viewModel::stop,
        )
    }
}

@Composable
private fun AiRadioStationList(
    state: AiRadioViewModel.State,
    hasTargetPlayer: Boolean,
    contentPadding: PaddingValues,
    onPlay: (String) -> Unit,
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
                        artworkUrl = state.artwork[station.id],
                        isOnAir = state.running?.stationId == station.id,
                        onPlay = { onPlay(station.id) },
                        onStop = { onStop(station.id) },
                    )
                }
            }
        }
    }
}

/**
 * A station row. The action lives in an explicit trailing button, not in the row body: a bare
 * row gives no hint that tapping it puts a whole radio show on air.
 *
 * One button, two states — Play, or Stop while [isOnAir]. The station on air is the one the
 * server reports, which is re-read after every start and stop.
 */
@Composable
private fun StationRow(
    name: String,
    artworkUrl: String?,
    isOnAir: Boolean,
    onPlay: () -> Unit,
    onStop: () -> Unit,
) {
    RowItem(
        modifier = Modifier.padding(horizontal = 8.dp),
        name = name,
        subtitle = null,
        description = name,
        // The row body is inert — no ripple to suggest a tap does something.
        enabled = false,
        prefixContent = { AiRadioStationImage(url = artworkUrl, name = name) },
        suffixContent = {
            IconButton(onClick = if (isOnAir) onStop else onPlay) {
                Icon(
                    imageVector = if (isOnAir) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = stringResource(
                        if (isOnAir) Res.string.ai_radio_stop else Res.string.action_play,
                    ),
                )
            }
        },
        onClick = {},
        onLongClick = {},
    )
}

/**
 * A station's row image. Stations have no artwork, so [url] is the cover of the playlist the
 * station plays from, and it arrives after the row does. The wavy hexagon matches the Radios
 * section; the placeholder is the category's own icon, so an unresolved row still reads as
 * AI Radio rather than as a broken image.
 */
@Composable
private fun AiRadioStationImage(url: String?, name: String) {
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val onPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(WavyHexagonShape())
            .background(primaryContainer),
    ) {
        val placeholder = rememberPlaceholderPainter(
            backgroundColor = primaryContainer,
            iconColor = onPrimaryContainer,
            icon = Icons.Default.SmartToy,
        )
        AsyncImage(
            placeholder = placeholder,
            fallback = placeholder,
            model = url,
            contentDescription = name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
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
