package io.music_assistant.client.ui.compose.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.music_assistant.client.ui.theme.AccentColor
import io.music_assistant.client.ui.theme.accentColorScheme
import kotlinx.coroutines.flow.first
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

/** The size of an accent swatch. */
private val SWATCH_SIZE = 40.dp

/** The space between two accent swatches. */
private val SWATCH_SPACING = 12.dp

/**
 * One swatch for each [AccentColor] that this platform can give, in the primary color that the
 * accent gives in [darkTheme], in one row that scrolls sideways. When the section opens, the row
 * scrolls the current choice to its center. The line under the swatches names the current
 * choice. It is a part of [InterfaceSection], so it has no card of its own.
 */
@Composable
fun AccentColorSection(
    current: AccentColor,
    darkTheme: Boolean,
    onChange: (AccentColor) -> Unit,
) {
    val swatches = AccentColor.entries.mapNotNull { accent ->
        accentColorScheme(accent, darkTheme)?.let { scheme -> accent to scheme }
    }
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    // Once, when the section opens: a tap only selects a swatch that is already in view. The
    // row scrolls itself, because bringIntoView would also scroll the settings page.
    LaunchedEffect(Unit) {
        val index = swatches.indexOfFirst { (accent, _) -> accent == current }
            .takeIf { it >= 0 } ?: return@LaunchedEffect
        val viewport = snapshotFlow { scrollState.viewportSize }.first { it > 0 }
        val (step, swatch) = with(density) {
            (SWATCH_SIZE + SWATCH_SPACING).roundToPx() to SWATCH_SIZE.roundToPx()
        }
        // scrollTo clamps the value to the scroll range.
        scrollState.scrollTo(index * step - (viewport - swatch) / 2)
    }
    SectionTitle(stringResource(Res.string.settings_accent_color))
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(SWATCH_SPACING),
    ) {
        swatches.forEach { (accent, scheme) ->
            AccentSwatch(
                scheme = scheme,
                label = stringResource(accent.label),
                selected = accent == current,
                onClick = { onChange(accent) },
            )
        }
    }
    Text(
        modifier = Modifier.padding(top = 12.dp),
        text = stringResource(current.label),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onBackground,
    )
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
            .size(SWATCH_SIZE)
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
