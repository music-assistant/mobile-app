package io.music_assistant.client.ui.compose.common

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import io.music_assistant.client.data.model.client.items.AppMediaItem
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.action_remove
import musicassistantclient.composeapp.generated.resources.common_cancel
import musicassistantclient.composeapp.generated.resources.dialog_remove_from_library_message
import musicassistantclient.composeapp.generated.resources.dialog_remove_from_library_title
import org.jetbrains.compose.resources.stringResource

/**
 * Generic confirmation dialog for destructive actions. The confirm button is styled with the
 * error color to signal irreversibility; confirming also dismisses.
 */
@Composable
fun ConfirmationDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm()
                    onDismiss()
                },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.common_cancel))
            }
        },
    )
}

/**
 * Confirmation for removing an item from the library. Wording is uniform across item types
 * (a playlist is removed the same way as any other item), so it lives here as the single
 * source of truth shared by every menu surface that dispatches `RemoveFromLibrary`.
 */
@Composable
fun RemoveFromLibraryConfirmationDialog(
    item: AppMediaItem,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) = ConfirmationDialog(
    title = stringResource(Res.string.dialog_remove_from_library_title),
    message = stringResource(Res.string.dialog_remove_from_library_message, item.displayName),
    confirmLabel = stringResource(Res.string.action_remove),
    onConfirm = onConfirm,
    onDismiss = onDismiss,
)
