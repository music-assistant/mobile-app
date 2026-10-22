@file:Suppress("MagicNumber")

package io.music_assistant.client.ui.compose.home.players

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.data.model.client.ProviderDetails
import io.music_assistant.client.data.model.client.QueueTrack
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.description
import io.music_assistant.client.data.model.server.AudioFormat
import io.music_assistant.client.ui.compose.item.opensItemDetails
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.quality_dialog_input
import musicassistantclient.composeapp.generated.resources.quality_dialog_open_origin
import musicassistantclient.composeapp.generated.resources.quality_dialog_output
import musicassistantclient.composeapp.generated.resources.quality_dialog_played_from
import musicassistantclient.composeapp.generated.resources.quality_dialog_title
import org.jetbrains.compose.resources.stringResource

@Composable
fun AudioChainDialog(
    queueTrack: QueueTrack,
    player: PlayerData,
    providerDetails: (String) -> ProviderDetails?,
    onOpenOrigin: (AppMediaItem) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val playerNames: Map<String, String> = buildMap {
        put(player.player.id, player.player.name)
        player.childrenBinds.forEach { put(it.id, it.name) }
        player.parentBind?.let { put(it.id, it.name) }
    }

    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = stringResource(Res.string.quality_dialog_title),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Spacer(modifier = Modifier.height(12.dp))

                ChainStage(
                    header = stringResource(Res.string.quality_dialog_input),
                    name = queueTrack.provider?.let(providerDetails)?.name,
                    format = queueTrack.format,
                ) {
                    queueTrack.origin?.let { OriginRow(origin = it, onOpen = onOpenOrigin) }
                }

                queueTrack.audioProcessingChain?.outputs.orEmpty().forEach { output ->
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    ChainStage(
                        header = stringResource(Res.string.quality_dialog_output),
                        name = output.playerIds.orEmpty().let {
                            it.firstOrNull()?.let { id -> playerNames[id] }
                        },
                        format = output.format,
                    )
                }
            }
        }
    }
}

@Composable
private fun ChainStage(
    header: String,
    name: String?,
    format: AudioFormat?,
    footer: @Composable () -> Unit = {},
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
        )
        Text(
            text = header,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Spacer(modifier = Modifier.width(4.dp))
        Box(
            modifier = Modifier
                .width(2.dp)
                .heightIn(min = 32.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(
            modifier = Modifier
                .padding(vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            name?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            format?.let {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = it.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            footer()
        }
    }
}

/** "Playing from {container}"; opens the container when it has a details screen. */
@Composable
private fun OriginRow(
    origin: AppMediaItem,
    onOpen: (AppMediaItem) -> Unit,
) {
    val color = if (origin.opensItemDetails) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val openLabel = stringResource(Res.string.quality_dialog_open_origin, origin.displayName)
    Text(
        text = stringResource(Res.string.quality_dialog_played_from, origin.displayName),
        modifier = if (origin.opensItemDetails) {
            Modifier.clickable(role = Role.Button, onClickLabel = openLabel) { onOpen(origin) }
        } else {
            Modifier
        },
        style = MaterialTheme.typography.bodyMedium,
        color = color,
    )
}
