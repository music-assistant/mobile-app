package io.music_assistant.client.ui.compose.home.players.share

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.music_assistant.client.api.ErrorMessageBus
import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.data.share.DeviceAudioRouter
import kotlinx.coroutines.launch
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.common_cancel
import musicassistantclient.composeapp.generated.resources.device_audio_intro_body
import musicassistantclient.composeapp.generated.resources.device_audio_intro_continue
import musicassistantclient.composeapp.generated.resources.device_audio_intro_muted
import musicassistantclient.composeapp.generated.resources.device_audio_intro_title
import musicassistantclient.composeapp.generated.resources.device_audio_permission_denied
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * Tells the user what the system asks next, then starts the share on [player]. The system
 * dialogs speak of a recording, so the user must know first that only the audio is used.
 * A refusal ends here: no capture starts, and the audio link of the local player stays as it is.
 */
@Composable
fun DeviceAudioShareDialog(
    player: PlayerData,
    onDismissRequest: () -> Unit,
    router: DeviceAudioRouter = koinInject(),
    errorBus: ErrorMessageBus = koinInject(),
) {
    val launcher = rememberDeviceAudioShareLauncher()
    val scope = rememberCoroutineScope()
    val refused = stringResource(Res.string.device_audio_permission_denied)
    // The system dialogs are open: the dialog stays, so the launch is not cancelled with it.
    var launching by remember { mutableStateOf(false) }
    val queueId = player.queueOrPlayerId

    AlertDialog(
        onDismissRequest = { if (!launching) onDismissRequest() },
        title = { Text(stringResource(Res.string.device_audio_intro_title, player.player.name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(Res.string.device_audio_intro_body))
                MutedNotice()
            }
        },
        confirmButton = {
            TextButton(
                enabled = !launching,
                onClick = {
                    launching = true
                    scope.launch {
                        if (launcher.launch()) router.route(queueId) else errorBus.emit(refused)
                        onDismissRequest()
                    }
                },
            ) { Text(stringResource(Res.string.device_audio_intro_continue)) }
        },
        dismissButton = {
            TextButton(enabled = !launching, onClick = onDismissRequest) {
                Text(stringResource(Res.string.common_cancel))
            }
        },
    )
}

/** Set apart from the body: a device that goes silent with no warning looks broken. */
@Composable
private fun MutedNotice() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("DeviceAudioMutedNotice")
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.VolumeOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onTertiaryContainer,
        )
        Text(
            text = stringResource(Res.string.device_audio_intro_muted),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
    }
}
