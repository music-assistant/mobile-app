package io.music_assistant.client.ui.compose.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.music_assistant.client.data.model.client.ItemKind
import io.music_assistant.client.settings.DefaultClickOption
import io.music_assistant.client.settings.carBrowsableKinds
import io.music_assistant.client.settings.carPlayableKinds
import io.music_assistant.client.settings.carTapAction
import io.music_assistant.client.settings.currentCarPlatform
import io.music_assistant.client.settings.defaultCarBulkActions
import io.music_assistant.client.settings.isCarSupported
import io.music_assistant.client.ui.compose.common.ReorderableEnabledList
import io.music_assistant.client.ui.compose.common.items.ActionDropdown
import io.music_assistant.client.ui.compose.common.items.LocalClickActionConfig
import io.music_assistant.client.ui.compose.common.items.labelRes
import io.music_assistant.client.ui.compose.common.items.title
import io.music_assistant.client.ui.compose.common.items.toItemAction
import io.music_assistant.client.ui.compose.library.CustomizeLibraryCategoriesDialog
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.common_cancel
import musicassistantclient.composeapp.generated.resources.common_done
import musicassistantclient.composeapp.generated.resources.default_click_dialog_save
import musicassistantclient.composeapp.generated.resources.settings_car
import musicassistantclient.composeapp.generated.resources.settings_car_bulk_for
import musicassistantclient.composeapp.generated.resources.settings_car_dsp
import musicassistantclient.composeapp.generated.resources.settings_car_enqueue_action
import musicassistantclient.composeapp.generated.resources.settings_car_item_actions
import musicassistantclient.composeapp.generated.resources.settings_car_tabs
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

private val KIND_LABEL_WIDTH = 110.dp

/**
 * Settings → Car section. The car has no in-app surface and its templates can't host real settings
 * UI, so its options live here: a single "Item actions" row reveals a bottom sheet (enqueue action +
 * per-browsable-kind bulk lists), and a "Tabs" row customizes the Android Auto root tabs.
 */
@Composable
fun CarSection() {
    val viewModel = koinViewModel<CarActionsViewModel>()
    var showSheet by remember { mutableStateOf(false) }
    var showEnqueue by remember { mutableStateOf(false) }
    var bulkKind by remember { mutableStateOf<ItemKind?>(null) }
    var showTabs by remember { mutableStateOf(false) }
    var showDsp by remember { mutableStateOf(false) }

    SectionCard {
        SectionTitle(stringResource(Res.string.settings_car))
        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = { showTabs = true },
        ) { Text(stringResource(Res.string.settings_car_tabs)) }
        Spacer(Modifier.size(8.dp))
        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = { showSheet = true },
        ) { Text(stringResource(Res.string.settings_car_item_actions)) }
        Spacer(Modifier.size(8.dp))
        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = { showDsp = true },
        ) { Text(stringResource(Res.string.settings_car_dsp)) }
    }

    // Tap a sheet row → close the sheet, then open its dialog (no stacked overlays).
    if (showSheet) {
        CarItemActionsSheet(
            onDismiss = { showSheet = false },
            onEnqueue = { showEnqueue = true },
            onBulk = { kind -> bulkKind = kind },
        )
    }
    if (showEnqueue) CarEnqueueActionDialog(viewModel) { showEnqueue = false }
    bulkKind?.let { kind -> CarBulkActionsDialog(viewModel, kind) { bulkKind = null } }
    if (showTabs) {
        val tabs by viewModel.tabsConfig.collectAsStateWithLifecycle()
        CustomizeLibraryCategoriesDialog(
            initialConfig = tabs,
            onDismissRequest = { showTabs = false },
            onConfirm = viewModel::saveTabs,
        )
    }
    if (showDsp) CarDspSettingsDialog { showDsp = false }
}

/** Bottom sheet listing the configurable action groups; each row opens its dialog. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CarItemActionsSheet(
    onDismiss: () -> Unit,
    onEnqueue: () -> Unit,
    onBulk: (ItemKind) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text(
                text = stringResource(Res.string.settings_car_item_actions),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            SheetRow(stringResource(Res.string.settings_car_enqueue_action), onEnqueue)
            carBrowsableKinds.forEach { kind ->
                SheetRow(
                    text = stringResource(
                        Res.string.settings_car_bulk_for,
                        stringResource(kind.labelRes()),
                    ),
                    onClick = { onBulk(kind) },
                )
            }
        }
    }
}

@Composable
private fun SheetRow(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
    )
}

/** One labelled [ActionDropdown] per playable kind; persists every kind on Save. */
@Composable
private fun CarEnqueueActionDialog(viewModel: CarActionsViewModel, onDismiss: () -> Unit) {
    val stored by viewModel.playableClickActions.collectAsStateWithLifecycle()
    val platform = remember { currentCarPlatform() }
    val selection = remember {
        mutableStateMapOf<ItemKind, DefaultClickOption>().apply {
            carPlayableKinds.forEach { put(it, stored.carTapAction(it)) }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.settings_car_enqueue_action)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                carPlayableKinds.forEach { kind ->
                    val options = remember(kind) {
                        DefaultClickOption.entries.filter { it.isCarSupported(platform, kind) }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(kind.labelRes()),
                            modifier = Modifier.width(KIND_LABEL_WIDTH),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        ActionDropdown(
                            context = LocalClickActionConfig.current.context,
                            options = options,
                            selected = selection[kind] ?: DefaultClickOption.PLAY_NOW,
                            onSelect = { selection[kind] = it },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                carPlayableKinds.forEach { kind ->
                    selection[kind]?.let { viewModel.savePlayableClickAction(kind, it) }
                }
                onDismiss()
            }) { Text(stringResource(Res.string.default_click_dialog_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.common_cancel)) }
        },
    )
}

/** Reorderable/toggleable bulk-action list for a single browsable [kind]. */
@Composable
private fun CarBulkActionsDialog(
    viewModel: CarActionsViewModel,
    kind: ItemKind,
    onDismiss: () -> Unit,
) {
    val stored by viewModel.browsableBulkActions.collectAsStateWithLifecycle()
    val platform = remember { currentCarPlatform() }
    // Enabled+ordered actions first, then the remaining applicable ones (disabled) so they can be added.
    val initial = remember(kind, stored) {
        val universe = DefaultClickOption.entries.filter { it.isCarSupported(platform, kind) }
        val enabled = (stored[kind] ?: defaultCarBulkActions).filter { it in universe }
        val disabled = universe.filter { it !in enabled }
        enabled.map { it to true } + disabled.map { it to false }
    }
    var working by remember(kind) { mutableStateOf(initial) }

    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Box(modifier = Modifier.padding(vertical = 16.dp)) {
                Column {
                    Text(
                        text = stringResource(
                            Res.string.settings_car_bulk_for,
                            stringResource(kind.labelRes()),
                        ),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    Box(modifier = Modifier.padding(top = 16.dp)) {
                        ReorderableEnabledList(
                            initialItems = initial,
                            key = { it.name },
                            label = { stringResource(it.toItemAction().title()) },
                            onItemsChange = { working = it },
                            canDisableLast = true,
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, end = 8.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = onDismiss) { Text(stringResource(Res.string.common_cancel)) }
                        TextButton(onClick = {
                            viewModel.saveBrowsableBulkActions(
                                kind,
                                working.filter { it.second }.map { it.first },
                            )
                            onDismiss()
                        }) { Text(stringResource(Res.string.common_done)) }
                    }
                }
            }
        }
    }
}
