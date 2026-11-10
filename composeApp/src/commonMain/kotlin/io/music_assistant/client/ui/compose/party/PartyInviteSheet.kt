package io.music_assistant.client.ui.compose.party

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.music_assistant.client.data.model.client.PartyInvite
import io.music_assistant.client.ui.compose.common.DataState
import io.music_assistant.client.ui.compose.common.QrCode
import io.music_assistant.client.ui.compose.common.plainTextClipEntry
import kotlinx.coroutines.launch
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.cd_party_qr
import musicassistantclient.composeapp.generated.resources.nav_party
import musicassistantclient.composeapp.generated.resources.party_copy_link
import musicassistantclient.composeapp.generated.resources.party_guest_access_off
import musicassistantclient.composeapp.generated.resources.party_invite_error
import musicassistantclient.composeapp.generated.resources.party_link_copied
import musicassistantclient.composeapp.generated.resources.party_scan_to_join
import org.jetbrains.compose.resources.stringResource

/** The QR fills the sheet on a phone, but stays a size that a camera reads at arm's length. */
private val QR_MAX_SIZE = 320.dp

/** The host shows this to guests: the party QR code and its link, from `party/url`. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartyInviteSheet(state: DataState<PartyInvite>, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        ),
    ) {
        val invite = state.dataOrNull
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = (invite as? PartyInvite.Open)?.partyName ?: stringResource(Res.string.nav_party),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            when {
                invite is PartyInvite.Open -> OpenInvite(invite)

                invite is PartyInvite.GuestAccessOff -> Text(
                    text = stringResource(Res.string.party_guest_access_off),
                    textAlign = TextAlign.Center,
                )

                state is DataState.Error -> Text(
                    text = stringResource(Res.string.party_invite_error),
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )

                else -> CircularProgressIndicator()
            }
        }
    }
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
