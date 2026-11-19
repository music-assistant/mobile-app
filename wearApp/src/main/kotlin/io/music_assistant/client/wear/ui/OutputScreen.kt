// Compose layout values (icon sizes, padding) are visual design tokens.
@file:Suppress("MagicNumber")

package io.music_assistant.client.wear.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.SpeakerGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import io.music_assistant.client.wear.R
import io.music_assistant.client.wear.data.WearPlayer

/**
 * Where the sound goes: every player the watch can hand playback to, the current one first
 * in line and marked as such.
 *
 * Only players the server can reach and does not hide are listed, which is the same set the
 * phone app offers. Picking one returns to the volume screen, because the volume the user
 * reaches for next is the volume of whatever they just picked.
 */
@Composable
fun OutputScreen(
    state: WearUiState,
    onSelect: (String) -> Unit,
) {
    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()
    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxWidth(),
        ) {
            item {
                ListHeader(
                    modifier = Modifier.transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                ) {
                    Text(
                        text = stringResource(R.string.select_output),
                        textAlign = TextAlign.Center,
                    )
                }
            }
            if (state.selectablePlayers.isEmpty()) {
                item {
                    Caption(stringResource(R.string.no_players))
                }
            }
            items(state.selectablePlayers, key = { it.id }) { player ->
                PlayerButton(
                    player = player,
                    isSelected = player.id == state.selected?.id,
                    enabled = state.isConnected,
                    onClick = { onSelect(player.id) },
                    modifier = Modifier.transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                )
            }
            if (state.selectablePlayers.isNotEmpty()) {
                item {
                    Caption(stringResource(R.string.output_hint))
                }
            }
        }
    }
}

/**
 * One player. The current one is filled rather than ticked: on a watch, colour is read
 * before a glyph is, and the second line says in words what the colour means.
 */
@Composable
private fun PlayerButton(
    player: WearPlayer,
    isSelected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    transformation: SurfaceTransformation,
) {
    val secondary = if (isSelected) {
        stringResource(R.string.output_current)
    } else {
        player.secondaryLabel()
    }
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth(),
        transformation = transformation,
        colors = if (isSelected) {
            ButtonDefaults.filledVariantButtonColors()
        } else {
            ButtonDefaults.filledTonalButtonColors()
        },
        icon = {
            Icon(
                imageVector = if (player.isGroup) Icons.Rounded.SpeakerGroup else Icons.Rounded.Speaker,
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize),
            )
        },
        secondaryLabel = secondary?.let {
            {
                Text(text = it, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        label = {
            Text(text = player.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
    )
}

/** The small print under the list, which the rim leaves room for anyway. */
@Composable
private fun Caption(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

/**
 * The second line of a player row: what a group holds, otherwise what it is playing. A group
 * with members is worth naming as such — it is the difference between one speaker and five.
 */
@Composable
private fun WearPlayer.secondaryLabel(): String? = when {
    memberCount > 0 -> pluralStringResource(R.plurals.group_members, memberCount, memberCount)
    else -> subtitle
}
