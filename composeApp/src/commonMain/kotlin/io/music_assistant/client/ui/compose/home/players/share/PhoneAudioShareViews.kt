package io.music_assistant.client.ui.compose.home.players.share

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ScreenShare
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.phone_audio_local_overlay
import musicassistantclient.composeapp.generated.resources.phone_audio_local_overlay_starting
import musicassistantclient.composeapp.generated.resources.phone_audio_stop
import musicassistantclient.composeapp.generated.resources.phone_audio_strip
import musicassistantclient.composeapp.generated.resources.phone_audio_strip_starting
import org.jetbrains.compose.resources.stringResource

/**
 * The indication that the phone audio is shared, above every player page. [targetName] is
 * null until the server reports the player. A tap on the text goes to that player.
 */
@Composable
fun PhoneAudioStrip(
    targetName: String?,
    onOpenTarget: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag("PhoneAudioStrip")
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .clickable(enabled = targetName != null, onClick = onOpenTarget)
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ScreenShare,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onTertiaryContainer,
        )
        Text(
            text = targetName?.let { stringResource(Res.string.phone_audio_strip, it) }
                ?: stringResource(Res.string.phone_audio_strip_starting),
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        TextButton(onClick = onStop) { Text(stringResource(Res.string.phone_audio_stop)) }
    }
}

/**
 * Covers the page of the local player while the phone audio is shared, like the cover of a
 * grouped player: the phone does not play then. A tap goes to the player that plays the audio.
 */
@Composable
fun SharedPhoneInfo(
    targetName: String?,
    onOpenTarget: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .testTag("SharedPhoneInfo")
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f))
            // Always clickable: the cover also keeps taps away from the controls under it.
            .clickable(onClick = onOpenTarget),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = targetName?.let { stringResource(Res.string.phone_audio_local_overlay, it) }
                ?: stringResource(Res.string.phone_audio_local_overlay_starting),
            modifier = Modifier.padding(horizontal = 16.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}
