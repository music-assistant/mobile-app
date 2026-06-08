package io.music_assistant.client.ui.compose.common.items

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.music_assistant.client.settings.DefaultClickAction
import org.jetbrains.compose.resources.stringResource

/**
 * Single-line, ellipsizing dropdown over [DefaultClickAction]s. Shared by the per-context
 * customize dialog and the car tap-behaviour dialog so the look stays identical.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionDropdown(
    options: List<DefaultClickAction>,
    selected: DefaultClickAction,
    onSelect: (DefaultClickAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedAction = selected.toItemAction()
    val shape = RoundedCornerShape(4.dp)
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        // Custom anchor (not OutlinedTextField): a read-only text field scrolls instead of
        // ellipsizing, so we build the outlined row ourselves to get true single-line ellipsis.
        Row(
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
                .clip(shape)
                .border(1.dp, MaterialTheme.colorScheme.outline, shape)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(selectedAction.icon(), contentDescription = null, modifier = Modifier.size(20.dp))
            Text(
                text = stringResource(selectedAction.title()),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            ExposedDropdownMenuDefaults.TrailingIcon(expanded)
        }
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { action ->
                val itemAction = action.toItemAction()
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(itemAction.title()),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            itemAction.icon(),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    onClick = {
                        onSelect(action)
                        expanded = false
                    },
                )
            }
        }
    }
}
