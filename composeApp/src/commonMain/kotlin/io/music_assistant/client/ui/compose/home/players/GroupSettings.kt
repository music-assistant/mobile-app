package io.music_assistant.client.ui.compose.home.players

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.ui.compose.common.action.PlayerAction

@Composable
fun GroupSettings(
    item: PlayerData,
    onDismiss: () -> Unit,
    playerAction: (String, PlayerAction) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Non-scrollable Done button at top
        OutlinedButton(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        ) {
            Text("Done")
        }

        // Scrollable list of players
        LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Current player at the very top
            item {
                GroupPlayerItem(
                    playerId = item.player.id,
                    playerName = item.player.name,
                    isGroup = item.player.isGroup,
                    volume = if (item.player.isGroup) item.player.groupVolume else item.player.volumeLevel,
                    isMuted = item.player.volumeMuted.takeIf { item.player.canMute },
                    simplePlayerAction = playerAction,
                )
            }

            // Bound players
            val boundChildren = item.groupChildren.filter { it.isBound }
            items(boundChildren, key = { "${it.id}_${it.volume}" }) { child ->
                GroupPlayerItem(
                    playerId = child.id,
                    playerName = child.name,
                    volume = child.volume,
                    isMuted = child.isMuted,
                    simplePlayerAction = playerAction,
                    bindItem = child,
                )
            }

            // Unbound players
            val unboundChildren = item.groupChildren.filter { !it.isBound }
            items(unboundChildren, key = { it.id }) { child ->
                GroupPlayerItem(
                    playerId = child.id,
                    playerName = child.name,
                    volume = child.volume,
                    isMuted = child.isMuted,
                    simplePlayerAction = playerAction,
                    bindItem = child,
                )
            }
        }
    }
}

/**
 * Group player item with name and volume
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupPlayerItem(
    playerId: String,
    playerName: String,
    isGroup: Boolean = false,
    volume: Float?,
    isMuted: Boolean?,
    simplePlayerAction: (String, PlayerAction) -> Unit,
    bindItem: PlayerData.Bind? = null,
) {
    var currentVolume by remember(volume) {
        mutableStateOf(volume ?: 0f)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy((-4).dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                modifier = Modifier.alpha(if (bindItem?.isBound != false) 1f else 0.4f).weight(1f),
                text = playerName,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Show button only for non-current players (when bindItem is provided)
            bindItem?.let { bind ->
                val itemId = listOf(playerId)
                IconButton(
                    enabled = bindItem.isManageable,
                    onClick = {
                        simplePlayerAction(
                            bind.parentId,
                            PlayerAction.GroupManage(
                                toAdd = itemId.takeIf { !bind.isBound },
                                toRemove = itemId.takeIf { bind.isBound }
                            )
                        )
                    }
                ) {
                    Icon(
                        modifier = Modifier.alpha(if(bindItem.isManageable) 1f else 0.4f),
                        imageVector = if (bindItem.isBound) Icons.Default.Remove else Icons.Default.Add,
                        contentDescription = if (bindItem.isBound) "Remove from group" else "Add to group",
                        tint = if (bindItem.isBound)
                            MaterialTheme.colorScheme.error
                        else
                            MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        val volumeEnabled = volume != null && bindItem?.isBound != false
        Row {
            isMuted?.let {
                IconButton(onClick = {
                    simplePlayerAction(
                        playerId,
                        PlayerAction.ToggleMute(isMuted)
                    )
                }, enabled = volumeEnabled) {
                    Icon(
                        imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeMute else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = if (isMuted) "Unmute" else "Mute"
                    )
                }
            }
            Slider(
                modifier = Modifier.fillMaxWidth().alpha(if (volumeEnabled) 1f else 0.4f),
                value = currentVolume,
                valueRange = 0f..100f,
                enabled = volumeEnabled,
                onValueChange = {
                    currentVolume = it
                },
                onValueChangeFinished = {
                    simplePlayerAction(
                        playerId,
                        if (isGroup) PlayerAction.GroupVolumeSet(currentVolume.toDouble())
                        else PlayerAction.VolumeSet(currentVolume.toDouble())
                    )
                },
                thumb = {
                    SliderDefaults.Thumb(
                        interactionSource = remember { MutableInteractionSource() },
                        thumbSize = DpSize(16.dp, 16.dp),
                        colors = SliderDefaults.colors()
                            .copy(thumbColor = MaterialTheme.colorScheme.secondary),
                    )
                },
                track = { sliderState ->
                    SliderDefaults.Track(
                        sliderState = sliderState,
                        thumbTrackGapSize = 0.dp,
                        trackInsideCornerSize = 0.dp,
                        drawStopIndicator = null,
                        modifier = Modifier.height(4.dp)
                    )
                }
            )
        }
    }
}