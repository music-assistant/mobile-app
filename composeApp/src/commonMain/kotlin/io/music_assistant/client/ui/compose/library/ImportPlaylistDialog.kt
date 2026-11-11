package io.music_assistant.client.ui.compose.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.music_assistant.client.data.model.server.PlaylistMatchPolicy
import io.music_assistant.client.ui.compose.common.SelectOption
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.common_cancel
import musicassistantclient.composeapp.generated.resources.playlist_import
import musicassistantclient.composeapp.generated.resources.playlist_import_match_best_effort
import musicassistantclient.composeapp.generated.resources.playlist_import_match_best_effort_description
import musicassistantclient.composeapp.generated.resources.playlist_import_match_exact
import musicassistantclient.composeapp.generated.resources.playlist_import_match_exact_description
import musicassistantclient.composeapp.generated.resources.playlist_import_match_same_recording
import musicassistantclient.composeapp.generated.resources.playlist_import_match_same_recording_description
import musicassistantclient.composeapp.generated.resources.playlist_import_matching
import musicassistantclient.composeapp.generated.resources.playlist_import_sources
import musicassistantclient.composeapp.generated.resources.playlist_import_sources_description
import musicassistantclient.composeapp.generated.resources.playlist_import_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** A picked M3U file that waits for the import options. */
data class PlaylistImportDraft(
    val name: String,
    val m3uData: String,
    /** The server takes a [PlaylistMatchPolicy] (server 2.11). */
    val matchPolicySupported: Boolean,
)

/**
 * The import options, like the web frontend: the music sources to search for tracks, and the
 * match policy from server 2.11. Without a searchable source the dialog shows neither.
 */
@Composable
fun ImportPlaylistDialog(
    draft: PlaylistImportDraft,
    sources: List<SelectOption<String>>,
    onDismiss: () -> Unit,
    onImport: (sources: Set<String>, matchPolicy: PlaylistMatchPolicy?) -> Unit,
) {
    // Deselected rather than selected, so a source that loads later starts selected.
    var deselected by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var matchPolicy by rememberSaveable { mutableStateOf(PlaylistMatchPolicy.SAME_RECORDING) }
    val showPolicy = draft.matchPolicySupported && sources.isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.playlist_import_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = draft.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (sources.isNotEmpty()) {
                    SectionHeader(Res.string.playlist_import_sources)
                    Text(
                        text = stringResource(Res.string.playlist_import_sources_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    sources.forEach { source ->
                        val checked = source.value !in deselected
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .toggleable(value = checked, role = Role.Checkbox) {
                                    deselected = if (it) deselected - source.value else deselected + source.value
                                },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = checked, onCheckedChange = null)
                            Text(text = source.label, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
                if (showPolicy) {
                    SectionHeader(Res.string.playlist_import_matching)
                    Column(modifier = Modifier.selectableGroup()) {
                        PlaylistMatchPolicy.entries.forEach { policy ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(selected = policy == matchPolicy, role = Role.RadioButton) {
                                        matchPolicy = policy
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                RadioButton(selected = policy == matchPolicy, onClick = null)
                                Column(modifier = Modifier.padding(start = 8.dp)) {
                                    Text(stringResource(policy.title))
                                    Text(
                                        text = stringResource(policy.description),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onImport(
                        sources.map { it.value }.filterNot { it in deselected }.toSet(),
                        matchPolicy.takeIf { showPolicy },
                    )
                },
            ) {
                Text(stringResource(Res.string.playlist_import))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.common_cancel)) }
        },
    )
}

@Composable
private fun SectionHeader(text: StringResource) {
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 8.dp),
    )
}

private val PlaylistMatchPolicy.title: StringResource
    get() = when (this) {
        PlaylistMatchPolicy.EXACT -> Res.string.playlist_import_match_exact
        PlaylistMatchPolicy.SAME_RECORDING -> Res.string.playlist_import_match_same_recording
        PlaylistMatchPolicy.BEST_EFFORT -> Res.string.playlist_import_match_best_effort
    }

private val PlaylistMatchPolicy.description: StringResource
    get() = when (this) {
        PlaylistMatchPolicy.EXACT -> Res.string.playlist_import_match_exact_description
        PlaylistMatchPolicy.SAME_RECORDING -> Res.string.playlist_import_match_same_recording_description
        PlaylistMatchPolicy.BEST_EFFORT -> Res.string.playlist_import_match_best_effort_description
    }
