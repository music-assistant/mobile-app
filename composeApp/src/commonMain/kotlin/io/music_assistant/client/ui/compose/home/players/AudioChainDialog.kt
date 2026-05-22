@file:Suppress("MagicNumber")

package io.music_assistant.client.ui.compose.home.players

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.data.model.client.QueueTrack
import io.music_assistant.client.data.model.client.items.QualityTier
import io.music_assistant.client.data.model.client.items.description
import io.music_assistant.client.data.model.client.items.qualityTier
import io.music_assistant.client.data.model.server.AudioFormat
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.quality_dialog_input
import musicassistantclient.composeapp.generated.resources.quality_dialog_output
import musicassistantclient.composeapp.generated.resources.quality_dialog_title
import org.jetbrains.compose.resources.stringResource

@Composable
fun AudioChainDialog(
    queueTrack: QueueTrack,
    player: PlayerData,
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
                    title = queueTrack.provider
                        ?.substringBefore("--")
                        ?.replaceFirstChar { it.uppercaseChar() },
                    format = queueTrack.format,
                )

                queueTrack.dsp.orEmpty().forEach { (playerId, dspSettings) ->
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    val playerName = playerNames[playerId] ?: playerId
                    ChainStage(
                        header = stringResource(Res.string.quality_dialog_output),
                        title = playerName,
                        format = dspSettings.outputFormat,
                    )
                }
            }
        }
    }
}

@Composable
private fun QualityBadge(tier: QualityTier) {
    val isLq = tier == QualityTier.LQ
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(
                if (isLq) {
                    MaterialTheme.colorScheme.surfaceVariant
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                },
            )
            .padding(horizontal = 6.dp, vertical = 1.dp),
    ) {
        Text(
            text = tier.name,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = if (isLq) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onPrimaryContainer
            },
        )
    }
}

@Composable
private fun ChainStage(
    header: String,
    title: String?,
    format: AudioFormat?,
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
    Row(modifier = Modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.width(4.dp))
        Box(
            modifier = Modifier
                .width(2.dp)
                .height(if (title != null && format != null) 56.dp else 32.dp)
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(
            modifier = Modifier
                .padding(vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            title?.let {
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
                    it.qualityTier?.let { tier -> QualityBadge(tier) }
                }
            }
        }
    }
}
