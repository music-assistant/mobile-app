package io.music_assistant.client.ui.compose.home.players

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.music_assistant.client.data.model.client.Player
import io.music_assistant.client.ui.compose.common.icons.VolumeIcon
import io.music_assistant.client.ui.compose.common.icons.VolumeMutedIcon

/**
 * Shows the volume of the player that the hardware volume keys change, because the system
 * volume panel does not show it. The value comes from the player state, so it is the server
 * value. A null [player] hides the overlay.
 */
@Composable
fun RemoteVolumeOverlay(player: Player?, modifier: Modifier = Modifier) {
    // Keep the last player while the exit animation runs.
    var shown by remember { mutableStateOf(player) }
    player?.let { shown = it }
    Box(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(top = 8.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        AnimatedVisibility(visible = player != null, enter = fadeIn(), exit = fadeOut()) {
            shown?.let { RemoteVolumeCard(it) }
        }
    }
}

@Composable
private fun RemoteVolumeCard(player: Player) {
    val volume = player.currentVolume ?: 0f
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.widthIn(max = 360.dp).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = if (player.currentMuteState) VolumeMutedIcon else VolumeIcon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = player.name,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 140.dp),
            )
            LinearProgressIndicator(
                progress = { volume / 100f },
                modifier = Modifier.widthIn(min = 80.dp, max = 120.dp),
            )
            VolumeValue(volume = volume.toInt(), style = MaterialTheme.typography.labelLarge)
        }
    }
}
