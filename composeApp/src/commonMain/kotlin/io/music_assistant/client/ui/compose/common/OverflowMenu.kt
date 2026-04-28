package io.music_assistant.client.ui.compose.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Composable
fun OverflowMenu(
    modifier: Modifier = Modifier,
    options: List<OverflowMenuOption>,
    buttonContent: @Composable (onClick: () -> Unit) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(
        modifier = modifier.wrapContentSize(Alignment.TopStart),
    ) {
        buttonContent { expanded = true }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    onClick = {
                        option.onClick()
                        expanded = false
                    },
                    leadingIcon = option.icon?.let {
                        {
                            Icon(
                                imageVector = it,
                                contentDescription = option.title,
                            )
                        }
                    },
                    text = {
                        Text(modifier = Modifier.padding(all = 4.dp), text = option.title)
                    },
                )
            }
        }
    }
}

data class OverflowMenuOption(
    val title: String,
    val icon: ImageVector? = null,
    val onClick: () -> Unit,
)
