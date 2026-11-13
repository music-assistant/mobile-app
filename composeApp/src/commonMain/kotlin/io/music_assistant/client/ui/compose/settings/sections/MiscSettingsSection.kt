package io.music_assistant.client.ui.compose.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.music_assistant.client.ui.compose.settings.SectionCard
import io.music_assistant.client.ui.compose.settings.SectionTitle
import io.music_assistant.client.utils.supportsVolumeKeyRouting
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.cd_delete_crash_logs
import musicassistantclient.composeapp.generated.resources.settings_allow_landscape
import musicassistantclient.composeapp.generated.resources.settings_allow_landscape_hint
import musicassistantclient.composeapp.generated.resources.settings_misc
import musicassistantclient.composeapp.generated.resources.settings_share_crash_logs
import musicassistantclient.composeapp.generated.resources.settings_share_logs
import musicassistantclient.composeapp.generated.resources.settings_volume_keys_control_player
import musicassistantclient.composeapp.generated.resources.settings_volume_keys_control_player_hint
import org.jetbrains.compose.resources.stringResource

@Composable
fun MiscSettingsSection(
    allowLandscape: Boolean,
    onAllowLandscapeChange: (Boolean) -> Unit,
    volumeKeysControlPlayer: Boolean,
    onVolumeKeysControlPlayerChange: (Boolean) -> Unit,
    onShareLogs: () -> Unit,
    hasCrashLog: Boolean,
    isPreparingShare: Boolean,
    onShareCrashLog: () -> Unit,
    onDeleteCrashLog: () -> Unit,
) {
    SectionCard {
        SectionTitle(stringResource(Res.string.settings_misc))
        CheckboxSetting(
            checked = allowLandscape,
            onCheckedChange = onAllowLandscapeChange,
            label = stringResource(Res.string.settings_allow_landscape),
            hint = stringResource(Res.string.settings_allow_landscape_hint),
        )
        if (supportsVolumeKeyRouting) {
            CheckboxSetting(
                checked = volumeKeysControlPlayer,
                onCheckedChange = onVolumeKeysControlPlayerChange,
                label = stringResource(Res.string.settings_volume_keys_control_player),
                hint = stringResource(Res.string.settings_volume_keys_control_player_hint),
            )
        }
        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            enabled = !isPreparingShare,
            onClick = onShareLogs,
        ) {
            if (isPreparingShare) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                )
                Spacer(modifier = Modifier.size(8.dp))
            }
            Text(stringResource(Res.string.settings_share_logs))
        }
        if (hasCrashLog) {
            Spacer(modifier = Modifier.size(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    enabled = !isPreparingShare,
                    onClick = onShareCrashLog,
                ) {
                    Text(stringResource(Res.string.settings_share_crash_logs))
                }
                OutlinedButton(
                    enabled = !isPreparingShare,
                    onClick = onDeleteCrashLog,
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(Res.string.cd_delete_crash_logs),
                    )
                }
            }
        }
    }
}

@Composable
private fun CheckboxSetting(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    hint: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
    Text(
        // Aligned under the label: the checkbox occupies a 48.dp touch target.
        modifier = Modifier.padding(start = 48.dp, bottom = 12.dp),
        text = hint,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
