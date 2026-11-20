package io.music_assistant.client.ui.compose.home.players.share

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MobileScreenShare
import androidx.compose.material.icons.filled.MobileOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.device_audio_local_overlay
import musicassistantclient.composeapp.generated.resources.device_audio_local_overlay_starting
import org.jetbrains.compose.resources.stringResource

/** The mark of a device audio share, on every surface that shows one. */
val DeviceAudioIcon: ImageVector get() = Icons.AutoMirrored.Filled.MobileScreenShare

val StopDeviceAudioIcon: ImageVector get() = Icons.Filled.MobileOff

/**
 * Covers the page of the local player while the device audio is shared, like the cover of a
 * grouped player: the device does not play then. A tap goes to the player that plays the audio.
 */
@Composable
fun SharedDeviceInfo(
    targetName: String?,
    onOpenTarget: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .testTag("SharedDeviceInfo")
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f))
            // Always clickable: the cover also keeps taps away from the controls under it.
            .clickable(onClick = onOpenTarget),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = targetName?.let { stringResource(Res.string.device_audio_local_overlay, it) }
                ?: stringResource(Res.string.device_audio_local_overlay_starting),
            modifier = Modifier.padding(horizontal = 16.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}
