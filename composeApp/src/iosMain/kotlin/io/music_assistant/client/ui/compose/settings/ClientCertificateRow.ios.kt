package io.music_assistant.client.ui.compose.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.uikit.LocalUIViewController
import io.music_assistant.client.utils.KeychainClientIdentity
import io.music_assistant.client.utils.KeychainClientIdentity.ImportResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.action_ok
import musicassistantclient.composeapp.generated.resources.common_cancel
import musicassistantclient.composeapp.generated.resources.settings_client_certificate_import_failed
import musicassistantclient.composeapp.generated.resources.settings_client_certificate_password
import musicassistantclient.composeapp.generated.resources.settings_client_certificate_password_title
import musicassistantclient.composeapp.generated.resources.settings_client_certificate_wrong_password
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UniformTypeIdentifiers.UTTypePKCS12
import platform.darwin.NSObject

/** iOS apps cannot use system certificates, so the user imports a `.p12` file into the Keychain. */
@Composable
actual fun ClientCertificateSetting(
    host: String,
    port: Int,
    alias: String?,
    onAliasChange: (String?) -> Unit,
) {
    val keychain = koinInject<KeychainClientIdentity>()
    val viewController = LocalUIViewController.current
    val scope = rememberCoroutineScope()
    val currentOnAliasChange by rememberUpdatedState(onAliasChange)
    var picked by remember { mutableStateOf<PickedFile?>(null) }
    var importing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<StringResource?>(null) }
    // UIKit holds the delegate weakly.
    val pickerDelegate = remember { Pkcs12PickerDelegate { picked = it } }

    // A backup restored on another device brings the alias back, but not the Keychain item.
    LaunchedEffect(alias) {
        if (alias != null && withContext(Dispatchers.Default) { keychain.credential() } == null) {
            currentOnAliasChange(null)
        }
    }

    ClientCertificateRow(
        alias = alias,
        onChoose = {
            val picker = UIDocumentPickerViewController(
                forOpeningContentTypes = listOf(UTTypePKCS12),
                asCopy = true,
            ).apply { delegate = pickerDelegate }
            viewController.presentViewController(picker, animated = true, completion = null)
        },
        onClear = {
            keychain.clear()
            onAliasChange(null)
        },
    )

    picked?.let { file ->
        Pkcs12PasswordDialog(
            fileName = file.name,
            error = error,
            importing = importing,
            onConfirm = { password ->
                scope.launch {
                    importing = true
                    val result = withContext(Dispatchers.Default) {
                        keychain.import(file.data, password, file.name)
                    }
                    importing = false
                    when (result) {
                        is ImportResult.Imported -> {
                            picked = null
                            error = null
                            currentOnAliasChange(result.name)
                        }

                        ImportResult.WrongPassword ->
                            error = Res.string.settings_client_certificate_wrong_password

                        ImportResult.Failed ->
                            error = Res.string.settings_client_certificate_import_failed
                    }
                }
            },
            onDismiss = {
                picked = null
                error = null
            },
        )
    }
}

@Composable
private fun Pkcs12PasswordDialog(
    fileName: String,
    error: StringResource?,
    importing: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.settings_client_certificate_password_title)) },
        text = {
            Column {
                Text(
                    text = fileName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(Res.string.settings_client_certificate_password)) },
                    singleLine = true,
                    enabled = !importing,
                    isError = error != null,
                    supportingText = error?.let { { Text(stringResource(it)) } },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(password) }, enabled = !importing) {
                Text(stringResource(Res.string.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.common_cancel)) }
        },
    )
}

private class PickedFile(val data: NSData, val name: String)

private class Pkcs12PickerDelegate(
    private val onPicked: (PickedFile) -> Unit,
) : NSObject(), UIDocumentPickerDelegateProtocol {
    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL ?: return
        NSData.dataWithContentsOfURL(url)?.let { onPicked(PickedFile(it, url.lastPathComponent.orEmpty())) }
    }
}
