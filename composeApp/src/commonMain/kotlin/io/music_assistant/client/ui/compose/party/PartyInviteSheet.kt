package io.music_assistant.client.ui.compose.party

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.music_assistant.client.data.model.client.PartyInvite
import io.music_assistant.client.data.model.client.PartySetting
import io.music_assistant.client.data.model.client.PartySettingInput
import io.music_assistant.client.data.model.client.PartySettings
import io.music_assistant.client.ui.compose.common.DataState
import io.music_assistant.client.ui.compose.common.QrCode
import io.music_assistant.client.ui.compose.common.boundedModalSheet
import io.music_assistant.client.ui.compose.common.plainTextClipEntry
import kotlinx.coroutines.launch
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.cd_party_qr
import musicassistantclient.composeapp.generated.resources.common_save
import musicassistantclient.composeapp.generated.resources.nav_party
import musicassistantclient.composeapp.generated.resources.party_copy_link
import musicassistantclient.composeapp.generated.resources.party_guest_access_off
import musicassistantclient.composeapp.generated.resources.party_guest_access_off_host
import musicassistantclient.composeapp.generated.resources.party_invite_error
import musicassistantclient.composeapp.generated.resources.party_link_copied
import musicassistantclient.composeapp.generated.resources.party_save_failed
import musicassistantclient.composeapp.generated.resources.party_scan_to_join
import musicassistantclient.composeapp.generated.resources.party_settings
import musicassistantclient.composeapp.generated.resources.party_settings_error
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** The QR fills the sheet on a phone, but stays a size that a camera reads at arm's length. */
private val QR_MAX_SIZE = 320.dp

/** What the host does in the sheet. */
interface PartySheetActions {
    fun setGuestAccess(on: Boolean)

    fun edit(setting: PartySetting, text: String)

    fun saveSettings()
}

/**
 * The host shows this to guests: the party QR code and its link, from `party/url`. A user who
 * may edit the plugin also gets the guest access switch and the core settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartyInviteSheet(state: PartyViewModel.State, actions: PartySheetActions, onDismiss: () -> Unit) {
    ModalBottomSheet(
        modifier = Modifier.boundedModalSheet(),
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        ),
    ) {
        val invite = state.invite.dataOrNull
        // One cast for the title and the branch. On iOS, an `as?` on `invite` followed by an
        // `is` check on it made the `is` check true for null in this composable (Kotlin 2.4.10,
        // Compose 1.12.0), and OpenInvite crashed.
        val open = invite as? PartyInvite.Open
        val settings = state.settings.dataOrNull
        Column(
            // Full height from the start: the content grows as the invite and the settings load,
            // and a sheet that follows it would jump.
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = open?.partyName ?: stringResource(Res.string.nav_party),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            settings?.let {
                SwitchRow(
                    setting = it.guestAccess,
                    checked = it.guestAccess.value.toBoolean(),
                    // Turning it on saves the edits too, so an invalid edit blocks it.
                    enabled = !state.saving && (it.guestAccess.value.toBoolean() || state.pendingEdits != null),
                    onCheckedChange = actions::setGuestAccess,
                )
            }
            when {
                open != null -> OpenInvite(open)

                invite is PartyInvite.GuestAccessOff -> Text(
                    text = stringResource(
                        if (settings != null) Res.string.party_guest_access_off_host else Res.string.party_guest_access_off,
                    ),
                    textAlign = TextAlign.Center,
                )

                state.invite is DataState.Error -> ErrorText(Res.string.party_invite_error)

                else -> CircularProgressIndicator()
            }
            when {
                settings != null -> SettingsForm(settings, state, actions)
                state.settings is DataState.Error -> ErrorText(Res.string.party_settings_error)
            }
        }
    }
}

@Composable
private fun SettingsForm(settings: PartySettings, state: PartyViewModel.State, actions: PartySheetActions) {
    HorizontalDivider()
    // While the party is off, the host sets it up here and the switch saves it. While it is on,
    // the QR comes first and the settings fold away, with their own Save.
    val partyOn = settings.guestAccess.value.toBoolean()
    var expanded by rememberSaveable(partyOn) { mutableStateOf(!partyOn) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (partyOn) Modifier.clickable(role = Role.Button) { expanded = !expanded } else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(Res.string.party_settings),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        if (partyOn) {
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
            )
        }
    }
    if (!expanded) return
    val values = settings.fields.associate { it.key to (state.edits[it.key] ?: it.value) }
    settings.fields.filter { settings.isShown(it, values) }.forEach { field ->
        SettingField(
            setting = field,
            text = values.getValue(field.key),
            enabled = !state.saving,
            onChange = { actions.edit(field, it) },
        )
    }
    if (state.saveFailed) ErrorText(Res.string.party_save_failed)
    if (partyOn) {
        Button(
            onClick = actions::saveSettings,
            enabled = !state.saving && !state.pendingEdits.isNullOrEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(Res.string.common_save))
        }
    }
}

@Composable
private fun SettingField(setting: PartySetting, text: String, enabled: Boolean, onChange: (String) -> Unit) {
    when (val input = setting.input) {
        PartySettingInput.Switch -> SwitchRow(setting, text.toBoolean(), enabled) { onChange(it.toString()) }

        is PartySettingInput.Choice -> ChoiceField(setting, input, text, enabled, onChange)

        PartySettingInput.Text,
        is PartySettingInput.Number,
            -> OutlinedTextField(
            value = text,
            onValueChange = onChange,
            enabled = enabled,
            label = { Text(setting.label) },
            supportingText = setting.description?.let { { Text(it) } },
            isError = !setting.accepts(text),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (input is PartySettingInput.Number) KeyboardType.Number else KeyboardType.Text,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChoiceField(
    setting: PartySetting,
    input: PartySettingInput.Choice,
    value: String,
    enabled: Boolean,
    onChange: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it && enabled },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            // A stored player that the server no longer lists keeps its id as the text.
            value = input.options.firstOrNull { it.value == value }?.title ?: value,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(setting.label) },
            supportingText = setting.description?.let { { Text(it) } },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            input.options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.title) },
                    onClick = {
                        onChange(option.value)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun SwitchRow(setting: PartySetting, checked: Boolean, enabled: Boolean, onCheckedChange: (Boolean) -> Unit) {
    // The row is the switch, so the label is its name for a screen reader.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // No ripple on the row: the switch itself shows the change.
            .toggleable(
                value = checked,
                interactionSource = null,
                indication = null,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = setting.label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f).padding(end = 16.dp),
        )
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
private fun ErrorText(text: StringResource) {
    Text(
        text = stringResource(text),
        color = MaterialTheme.colorScheme.error,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun OpenInvite(invite: PartyInvite.Open) {
    QrCode(
        data = invite.url,
        contentDescription = stringResource(Res.string.cd_party_qr),
        modifier = Modifier
            .widthIn(max = QR_MAX_SIZE)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp)),
    )
    Text(
        text = invite.qrText ?: stringResource(Res.string.party_scan_to_join),
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
    )
    Text(
        text = invite.url,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    CopyLinkButton(invite.url)
}

@Composable
private fun CopyLinkButton(url: String) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var copied by remember(url) { mutableStateOf(false) }
    OutlinedButton(
        onClick = {
            scope.launch {
                clipboard.setClipEntry(plainTextClipEntry(url))
                copied = true
            }
        },
    ) {
        Icon(
            imageVector = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(stringResource(if (copied) Res.string.party_link_copied else Res.string.party_copy_link))
    }
}
