package io.music_assistant.client.ui.compose.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import io.music_assistant.client.ui.compose.common.TvFocusFlow.Links
import kotlinx.coroutines.delay
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.common_cancel
import musicassistantclient.composeapp.generated.resources.common_done
import org.jetbrains.compose.resources.stringResource

/**
 * A settings row for Android TV: label + current value, D-pad focusable via [focusModifier], and
 * the trailing chevron signalling that [onClick] opens an editor.
 *
 * TV settings follow the platform pattern of "rows show values, selecting a row opens a dedicated
 * editor", so screens never need to host inline text fields (which is what forced the old
 * pixel-fitted, non-scrolling layouts). Rows are compact and fit any screen height.
 */
@Composable
fun TvPreferenceRow(
    label: String,
    value: String? = null,
    onClick: (() -> Unit)?,
    focusModifier: Modifier,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick ?: {},
        enabled = onClick != null,
        modifier = modifier
            .fillMaxWidth()
            .then(focusModifier),
        color = Color.Transparent,
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (onClick == null) {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    } else {
                        MaterialTheme.colorScheme.onBackground
                    },
                )
                if (value != null) {
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (onClick != null) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Full-screen keyboard editor for a single TV settings row. The row's value is edited in the
 * dialog's own window (so D-pad and the Leanback keyboard can't collide with the settings screen),
 * and Done commits while Back/Cancel discards. The settings screen re-lands focus on the row that
 * opened the editor when this closes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TvTextEditorDialog(
    title: String,
    initialValue: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    validate: (String) -> Boolean = { true },
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember { mutableStateOf(initialValue) }
    val flow = rememberTvFocusFlow()
    val links = mapOf(
        "input" to Links(down = "done"),
        "done" to Links(up = "input", left = "cancel"),
        "cancel" to Links(up = "input", right = "done"),
    )

    // Land focus on the field (the Leanback keyboard opens with it). This hardware can drop the
    // first requestFocus, so retry like the settings screen does.
    LaunchedEffect(Unit) {
        var attempt = 0
        while (attempt < 5) {
            flow.requestFocus("input")
            attempt++
            delay(100)
        }
    }

    val commit = {
        if (validate(value)) {
            onConfirm(value)
        }
    }

    BasicAlertDialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(12.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            TextField(
                modifier = flow.modifierFor("input", links.getValue("input"), textField = true)
                    .fillMaxWidth(),
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = keyboardType,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { commit() }),
                colors = TextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onBackground,
                    unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                OutlinedButton(
                    modifier = flow.modifierFor("cancel", links.getValue("cancel")),
                    onClick = onDismiss,
                ) {
                    Text(stringResource(Res.string.common_cancel))
                }
                Button(
                    modifier = flow.modifierFor("done", links.getValue("done")),
                    onClick = { commit() },
                    enabled = validate(value),
                ) {
                    Text(stringResource(Res.string.common_done))
                }
            }
        }
    }
}
