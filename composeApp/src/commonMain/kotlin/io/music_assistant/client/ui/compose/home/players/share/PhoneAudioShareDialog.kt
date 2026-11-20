package io.music_assistant.client.ui.compose.home.players.share

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import io.music_assistant.client.api.ErrorMessageBus
import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.data.share.PhoneAudioRouter
import kotlinx.coroutines.launch
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.common_cancel
import musicassistantclient.composeapp.generated.resources.phone_audio_intro_body
import musicassistantclient.composeapp.generated.resources.phone_audio_intro_continue
import musicassistantclient.composeapp.generated.resources.phone_audio_intro_title
import musicassistantclient.composeapp.generated.resources.phone_audio_permission_denied
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * Tells the user what the system asks next, then starts the share on [player]. The system
 * dialogs speak of a recording, so the user must know first that only the audio is used.
 * A refusal ends here: no capture starts, and the audio link of the local player stays as it is.
 */
@Composable
fun PhoneAudioShareDialog(
    player: PlayerData,
    onDismissRequest: () -> Unit,
    router: PhoneAudioRouter = koinInject(),
    errorBus: ErrorMessageBus = koinInject(),
) {
    val launcher = rememberPhoneAudioShareLauncher()
    val scope = rememberCoroutineScope()
    val refused = stringResource(Res.string.phone_audio_permission_denied)
    // The system dialogs are open: the dialog stays, so the launch is not cancelled with it.
    var launching by remember { mutableStateOf(false) }
    val queueId = player.queueOrPlayerId

    AlertDialog(
        onDismissRequest = { if (!launching) onDismissRequest() },
        title = { Text(stringResource(Res.string.phone_audio_intro_title, player.player.name)) },
        text = { Text(stringResource(Res.string.phone_audio_intro_body)) },
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
            ) { Text(stringResource(Res.string.phone_audio_intro_continue)) }
        },
        dismissButton = {
            TextButton(enabled = !launching, onClick = onDismissRequest) {
                Text(stringResource(Res.string.common_cancel))
            }
        },
    )
}
