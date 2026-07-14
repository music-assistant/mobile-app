@file:Suppress("MagicNumber")

package io.music_assistant.client.ui.compose.home.players

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import io.music_assistant.client.data.model.server.AudioFidelity
import io.music_assistant.client.data.model.server.AudioFormat
import io.music_assistant.client.data.model.server.AudioProcessingChain
import io.music_assistant.client.data.model.server.AudioQueueProcessing
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.quality_dialog_bit_perfect
import musicassistantclient.composeapp.generated.resources.quality_dialog_crossfade
import musicassistantclient.composeapp.generated.resources.quality_dialog_fidelity_hi_res
import musicassistantclient.composeapp.generated.resources.quality_dialog_fidelity_lossless
import musicassistantclient.composeapp.generated.resources.quality_dialog_fidelity_low
import musicassistantclient.composeapp.generated.resources.quality_dialog_fidelity_standard
import musicassistantclient.composeapp.generated.resources.quality_dialog_handoff
import musicassistantclient.composeapp.generated.resources.quality_dialog_input
import musicassistantclient.composeapp.generated.resources.quality_dialog_normalization
import musicassistantclient.composeapp.generated.resources.quality_dialog_output
import musicassistantclient.composeapp.generated.resources.quality_dialog_overlay
import musicassistantclient.composeapp.generated.resources.quality_dialog_processing
import musicassistantclient.composeapp.generated.resources.quality_dialog_server_input
import musicassistantclient.composeapp.generated.resources.quality_dialog_tempo
import musicassistantclient.composeapp.generated.resources.quality_dialog_title
import org.jetbrains.compose.resources.stringResource

@Composable
fun AudioChainDialog(
    queueTrack: QueueTrack,
    player: PlayerData,
    audioProcessingChain: AudioProcessingChain? = null,
    onDismissRequest: () -> Unit,
) {
    val playerNames: Map<String, String> = buildMap {
        put(player.player.id, player.player.name)
        player.childrenBinds.forEach { put(it.id, it.name) }
        player.parentBind?.let { put(it.id, it.name) }
    }
    Dialog(onDismissRequest = onDismissRequest) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(),
            contentAlignment = Alignment.Center,
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxHeight * 0.9f),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = stringResource(Res.string.quality_dialog_title),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    audioProcessingChain?.let { chain ->
                        AudioProcessingSnapshot(chain, playerNames)
                    } ?: LegacyAudioProcessingSnapshot(queueTrack, playerNames)
                }
            }
        }
    }
}

@Composable
private fun AudioProcessingSnapshot(
    chain: AudioProcessingChain,
    playerNames: Map<String, String>,
) {
    val input = chain.input
    val sourceFormat = input?.sourceFormat ?: input?.serverInputFormat
    val serverInputFormat = input?.serverInputFormat?.takeIf { it != sourceFormat }
    ChainStage(
        header = stringResource(Res.string.quality_dialog_input),
        format = sourceFormat,
        fidelity = input?.fidelity,
        secondaryFormatLabel = stringResource(Res.string.quality_dialog_server_input),
        secondaryFormat = serverInputFormat,
    )

    chain.queueProcessing?.let { processing ->
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        ChainStage(
            header = stringResource(Res.string.quality_dialog_processing),
            title = processingDescription(processing),
            format = processing.outputFormat ?: processing.inputFormat,
        )
    }

    chain.outputs.forEach { output ->
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        val groupedPlayers = output.playerIds
            .map { playerNames[it] ?: it }
            .joinToString()
            .takeIf { it.isNotEmpty() }
        ChainStage(
            header = stringResource(Res.string.quality_dialog_output),
            title = groupedPlayers,
            format = output.outputFormat,
            fidelity = output.fidelity,
            secondaryFormatLabel = stringResource(Res.string.quality_dialog_handoff),
            secondaryFormat = output.handoffFormat?.takeIf { it != output.outputFormat },
        )
    }
}

@Composable
private fun LegacyAudioProcessingSnapshot(
    queueTrack: QueueTrack,
    playerNames: Map<String, String>,
) {
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

@Composable
private fun processingDescription(processing: AudioQueueProcessing): String? {
    val parts = mutableListOf<String>()
    processing.normalization?.let {
        val mode = it.mode.toDisplayValue()
        parts += listOfNotNull(
            stringResource(Res.string.quality_dialog_normalization),
            mode,
        ).joinToString(": ")
    }
    processing.tempo?.let {
        parts += "${stringResource(Res.string.quality_dialog_tempo)} " +
                "${formatDecimal(it.playbackSpeed, 2)}x"
    }
    processing.crossfade?.let {
        val state = it.state.toDisplayValue()
        parts += listOfNotNull(
            stringResource(Res.string.quality_dialog_crossfade),
            state,
        ).joinToString(": ")
    }
    processing.overlay?.let {
        parts += listOfNotNull(
            stringResource(Res.string.quality_dialog_overlay),
            it.source?.name,
        ).joinToString(": ")
    }
    return parts.joinToString(" \u00b7 ").takeIf { it.isNotEmpty() }
}

private fun String.toDisplayValue(): String? =
    takeUnless { equals("unknown", ignoreCase = true) }
        ?.replace('_', ' ')
        ?.replaceFirstChar { it.uppercaseChar() }

@Composable
private fun FidelityBadge(fidelity: AudioFidelity) {
    val label = when (fidelity.quality.lowercase()) {
        "low" -> stringResource(Res.string.quality_dialog_fidelity_low)
        "standard" -> stringResource(Res.string.quality_dialog_fidelity_standard)
        "lossless" -> stringResource(Res.string.quality_dialog_fidelity_lossless)
        "hi_res" -> stringResource(Res.string.quality_dialog_fidelity_hi_res)
        else -> null
    }
    label?.let {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(
                    if (fidelity.quality.equals("low", ignoreCase = true)) {
                        MaterialTheme.colorScheme.surfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    },
                )
                .padding(horizontal = 6.dp, vertical = 1.dp),
        ) {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (fidelity.quality.equals("low", ignoreCase = true)) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onPrimaryContainer
                },
            )
        }
    }
}

@Composable
private fun FormatDetails(
    format: AudioFormat?,
    fidelity: AudioFidelity?,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        format?.let {
            Text(
                text = it.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        when (fidelity) {
            null -> format?.qualityTier?.let { tier -> QualityBadge(tier) }
            else -> fidelity.takeIf {
                it.quality.lowercase() in setOf("low", "standard", "lossless", "hi_res")
            }?.let { FidelityBadge(it) }
        }
    }
    if (fidelity?.bitPerfect == true) {
        Text(
            text = stringResource(Res.string.quality_dialog_bit_perfect),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SecondaryFormat(
    label: String,
    format: AudioFormat,
) {
    Text(
        text = "$label: ${format.description}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
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
    title: String? = null,
    format: AudioFormat?,
    fidelity: AudioFidelity? = null,
    secondaryFormatLabel: String? = null,
    secondaryFormat: AudioFormat? = null,
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
                .height(
                    when {
                        secondaryFormat != null -> 88.dp
                        fidelity?.bitPerfect == true -> 72.dp
                        title != null && format != null -> 56.dp
                        else -> 32.dp
                    },
                )
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
            if (format != null || fidelity != null) {
                FormatDetails(format, fidelity)
            }
            if (secondaryFormatLabel != null && secondaryFormat != null) {
                SecondaryFormat(secondaryFormatLabel, secondaryFormat)
            }
        }
    }
}
