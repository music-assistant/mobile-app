// Compose layout values (sizes, paddings) are visual design tokens.
@file:Suppress("MagicNumber")

package io.music_assistant.client.ui.compose.home.players.announcement

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.music_assistant.client.data.announcement.AnnouncementOptions
import io.music_assistant.client.data.announcement.AnnouncementRepository
import io.music_assistant.client.data.announcement.LiveRecording
import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.utils.formatDuration
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.announcement_chime
import musicassistantclient.composeapp.generated.resources.announcement_custom_volume
import musicassistantclient.composeapp.generated.resources.announcement_hold_to_talk
import musicassistantclient.composeapp.generated.resources.announcement_message
import musicassistantclient.composeapp.generated.resources.announcement_mic_denied
import musicassistantclient.composeapp.generated.resources.announcement_mic_stopped
import musicassistantclient.composeapp.generated.resources.announcement_open_settings
import musicassistantclient.composeapp.generated.resources.announcement_recording
import musicassistantclient.composeapp.generated.resources.announcement_send
import musicassistantclient.composeapp.generated.resources.announcement_tab_speak
import musicassistantclient.composeapp.generated.resources.announcement_tab_type
import musicassistantclient.composeapp.generated.resources.announcement_title
import musicassistantclient.composeapp.generated.resources.announcement_volume
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.TimeSource

private enum class AnnouncementMode { TYPE, SPEAK }

/**
 * Typed or spoken announcement for [player]. A mode shows only when the server supports it, and
 * speaking is never offered for the phone's own player. The repository runs the announcement,
 * so the dialog closes as soon as it is sent and playback still completes.
 */
@Composable
fun AnnouncementDialog(
    player: PlayerData,
    onDismissRequest: () -> Unit,
    repository: AnnouncementRepository = koinInject(),
) {
    val availability by repository.availability.collectAsStateWithLifecycle()
    val modes = buildList {
        if (availability.text) add(AnnouncementMode.TYPE)
        if (availability.voice && !player.isLocal) add(AnnouncementMode.SPEAK)
    }
    if (modes.isEmpty()) {
        LaunchedEffect(Unit) { onDismissRequest() }
        return
    }
    var selected by remember { mutableStateOf(modes.first()) }
    val mode = selected.takeIf { it in modes } ?: modes.first()

    // Null until the player's setting loads or the user flips the switch; the switch then wins.
    // What the switch shows is what is sent, as on the web.
    var chime by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(player.playerId) {
        repository.chimeSetting(player.playerId)?.let { setting -> if (chime == null) chime = setting }
    }
    // Kept across tab switches, so a half-typed message survives a look at the Speak tab.
    var message by remember { mutableStateOf("") }
    // Null while "Custom volume" is off: nothing is sent, so the player's own announcement volume applies.
    var volume by remember { mutableStateOf<Float?>(null) }
    val options = AnnouncementOptions(preAnnounce = chime ?: true, volumeLevel = volume?.roundToInt())

    Dialog(onDismissRequest = onDismissRequest) {
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Column(
                modifier = Modifier.padding(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        text = stringResource(Res.string.announcement_title),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        text = player.player.nameAndSuffix,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (modes.size > 1) {
                    PrimaryTabRow(selectedTabIndex = modes.indexOf(mode)) {
                        modes.forEach { tab ->
                            Tab(
                                selected = tab == mode,
                                onClick = { selected = tab },
                                text = { Text(stringResource(tab.label)) },
                            )
                        }
                    }
                }
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (mode == AnnouncementMode.TYPE) {
                        OutlinedTextField(
                            value = message,
                            onValueChange = { message = it },
                            label = { Text(stringResource(Res.string.announcement_message)) },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(Res.string.announcement_chime),
                            modifier = Modifier.weight(1f),
                        )
                        Switch(checked = chime ?: true, onCheckedChange = { chime = it })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(Res.string.announcement_custom_volume),
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = volume != null,
                            onCheckedChange = { on ->
                                volume = (player.player.currentVolume ?: DEFAULT_VOLUME).takeIf { on }
                            },
                        )
                    }
                    volume?.let { level ->
                        Text(
                            text = stringResource(Res.string.announcement_volume, level.roundToInt()),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Slider(
                            value = level,
                            onValueChange = { volume = it },
                            valueRange = 0f..100f,
                        )
                    }
                    // The main action sits last, below everything it depends on.
                    when (mode) {
                        AnnouncementMode.TYPE -> Button(
                            onClick = {
                                repository.type(player.playerId, message.trim(), options)
                                onDismissRequest()
                            },
                            enabled = message.isNotBlank(),
                            modifier = Modifier.align(Alignment.End),
                        ) {
                            Text(stringResource(Res.string.announcement_send))
                        }

                        AnnouncementMode.SPEAK -> HoldToTalk(
                            onPress = { repository.speak(player.playerId, options) },
                            onSent = onDismissRequest,
                        )
                    }
                }
            }
        }
    }
}

private val AnnouncementMode.label
    get() = when (this) {
        AnnouncementMode.TYPE -> Res.string.announcement_tab_type
        AnnouncementMode.SPEAK -> Res.string.announcement_tab_speak
    }

/**
 * Records while pressed and sends on release. The first press without the permission only asks
 * for it: the prompt takes the touch, so recording would end at once.
 */
@Composable
private fun ColumnScope.HoldToTalk(onPress: () -> LiveRecording, onSent: () -> Unit) {
    // The gesture outlives recompositions, so it must read the latest options and callbacks.
    val startRecording by rememberUpdatedState(onPress)
    val sent by rememberUpdatedState(onSent)
    val permission = rememberMicrophonePermission()
    val scope = rememberCoroutineScope()
    var denied by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf<LiveRecording?>(null) }

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Surface(
            shape = CircleShape,
            color = if (recording != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(96.dp)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            if (!permission.isGranted()) {
                                scope.launch { denied = !permission.request() }
                                return@detectTapGestures
                            }
                            denied = false
                            val live = startRecording()
                            recording = live
                            // The finally also covers a disposed dialog, so the microphone never stays on.
                            try {
                                tryAwaitRelease()
                            } finally {
                                live.finish()
                                recording = null
                            }
                            sent()
                        },
                    )
                },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = stringResource(Res.string.announcement_hold_to_talk),
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
    recording?.let { RecordingStatus(it) } ?: Text(
        text = stringResource(Res.string.announcement_hold_to_talk),
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
    )
    if (denied) {
        Text(
            text = stringResource(Res.string.announcement_mic_denied),
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
        TextButton(onClick = permission::openSettings, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(stringResource(Res.string.announcement_open_settings))
        }
    }
}

@Composable
private fun RecordingStatus(recording: LiveRecording) {
    val capturing by recording.capturing.collectAsStateWithLifecycle()
    var elapsed by remember(recording) { mutableStateOf(Duration.ZERO) }
    LaunchedEffect(recording) {
        val start = TimeSource.Monotonic.markNow()
        while (true) {
            elapsed = start.elapsedNow()
            delay(TICK_MILLIS)
        }
    }
    Text(
        text = if (capturing) {
            stringResource(Res.string.announcement_recording, elapsed.formatDuration())
        } else {
            stringResource(Res.string.announcement_mic_stopped)
        },
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        color = if (capturing) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
    )
}

private const val DEFAULT_VOLUME = 50f
private const val TICK_MILLIS = 250L
