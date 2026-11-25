package io.music_assistant.client.ui.compose.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.cd_delete_crash_logs
import musicassistantclient.composeapp.generated.resources.settings_logs
import musicassistantclient.composeapp.generated.resources.settings_share_crash_logs
import musicassistantclient.composeapp.generated.resources.settings_share_logs
import org.jetbrains.compose.resources.stringResource

@Composable
fun LogsSection(
    onShareLogs: () -> Unit,
    hasCrashLog: Boolean,
    isPreparingShare: Boolean,
    onShareCrashLog: () -> Unit,
    onDeleteCrashLog: () -> Unit,
) {
    SectionCard {
        SectionTitle(stringResource(Res.string.settings_logs))

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
