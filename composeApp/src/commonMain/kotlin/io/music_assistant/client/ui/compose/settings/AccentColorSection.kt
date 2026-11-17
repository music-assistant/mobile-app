package io.music_assistant.client.ui.compose.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.music_assistant.client.ui.theme.AccentColor
import io.music_assistant.client.ui.theme.accentColorScheme
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.accent_color_blue
import musicassistantclient.composeapp.generated.resources.accent_color_default
import musicassistantclient.composeapp.generated.resources.accent_color_green
import musicassistantclient.composeapp.generated.resources.accent_color_indigo
import musicassistantclient.composeapp.generated.resources.accent_color_orange
import musicassistantclient.composeapp.generated.resources.accent_color_pink
import musicassistantclient.composeapp.generated.resources.accent_color_purple
import musicassistantclient.composeapp.generated.resources.accent_color_red
import musicassistantclient.composeapp.generated.resources.accent_color_teal
import musicassistantclient.composeapp.generated.resources.accent_color_wallpaper
import musicassistantclient.composeapp.generated.resources.accent_color_yellow
import musicassistantclient.composeapp.generated.resources.settings_accent_color
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * One swatch for each [AccentColor] that this platform can give, in the primary color that the
 * accent gives in [darkTheme]. The line under the swatches names the current choice.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccentColorSection(
    current: AccentColor,
    darkTheme: Boolean,
    onChange: (AccentColor) -> Unit,
) {
    SectionCard {
        SectionTitle(stringResource(Res.string.settings_accent_color))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AccentColor.entries.forEach { accent ->
                accentColorScheme(accent, darkTheme)?.let { scheme ->
                    AccentSwatch(
                        scheme = scheme,
                        label = stringResource(accent.label),
                        selected = accent == current,
                        onClick = { onChange(accent) },
                    )
                }
            }
        }
        Text(
            modifier = Modifier.padding(top = 12.dp),
            text = stringResource(current.label),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AccentSwatch(
    scheme: ColorScheme,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val border = if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.outline
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(scheme.primary)
            .border(width = if (selected) 3.dp else 1.dp, color = border, shape = CircleShape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(Icons.Default.Check, contentDescription = null, tint = scheme.onPrimary)
        }
    }
}

private val AccentColor.label: StringResource
    get() = when (this) {
        AccentColor.DEFAULT -> Res.string.accent_color_default
        AccentColor.WALLPAPER -> Res.string.accent_color_wallpaper
        AccentColor.RED -> Res.string.accent_color_red
        AccentColor.ORANGE -> Res.string.accent_color_orange
        AccentColor.YELLOW -> Res.string.accent_color_yellow
        AccentColor.GREEN -> Res.string.accent_color_green
        AccentColor.TEAL -> Res.string.accent_color_teal
        AccentColor.BLUE -> Res.string.accent_color_blue
        AccentColor.INDIGO -> Res.string.accent_color_indigo
        AccentColor.PURPLE -> Res.string.accent_color_purple
        AccentColor.PINK -> Res.string.accent_color_pink
    }
