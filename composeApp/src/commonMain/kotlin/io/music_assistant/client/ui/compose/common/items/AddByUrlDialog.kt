package io.music_assistant.client.ui.compose.common.items

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.add_by_url_name_label
import musicassistantclient.composeapp.generated.resources.add_by_url_title
import musicassistantclient.composeapp.generated.resources.add_by_url_url_label
import musicassistantclient.composeapp.generated.resources.common_add
import musicassistantclient.composeapp.generated.resources.common_cancel
import org.jetbrains.compose.resources.stringResource

/**
 * Asks for a stream URL and an optional display name. The server requires a name,
 * so a blank one falls back to the URL.
 */
@Composable
fun AddByUrlDialog(
    onDismiss: () -> Unit,
    onAdd: (url: String, name: String) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    var url by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    val trimmedUrl = url.trim()

    fun submit() {
        if (trimmedUrl.isEmpty()) return
        onAdd(trimmedUrl, name.trim().ifEmpty { trimmedUrl })
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.add_by_url_title)) },
        text = {
            Column {
                OutlinedTextField(
                    modifier = Modifier.focusRequester(focusRequester),
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(Res.string.add_by_url_url_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Next,
                    ),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(Res.string.add_by_url_name_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                            submit()
                        },
                    ),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = ::submit,
                enabled = trimmedUrl.isNotEmpty(),
            ) {
                Text(stringResource(Res.string.common_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.common_cancel))
            }
        },
    )
}
