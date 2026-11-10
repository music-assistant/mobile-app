// Compose layout values (sizes, spacing) are visual design tokens.
@file:Suppress("MagicNumber")

package io.music_assistant.client.ui.compose.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.music_assistant.client.settings.BadgeVisibility
import io.music_assistant.client.ui.compose.common.icons.AlbumIcon
import io.music_assistant.client.ui.compose.common.icons.BookshelfIcon
import io.music_assistant.client.ui.compose.common.items.ExplicitBadge
import io.music_assistant.client.ui.compose.common.items.GRID_BADGE_PADDING
import io.music_assistant.client.ui.compose.common.items.GRID_BADGE_SIZE
import io.music_assistant.client.ui.compose.common.items.MediaArtwork
import io.music_assistant.client.ui.compose.common.items.ProgressBadge
import io.music_assistant.client.ui.compose.common.items.TypeBadge
import io.music_assistant.client.ui.compose.grid.gridItemMinSize
import io.music_assistant.client.ui.theme.AccentColor
import io.music_assistant.client.ui.theme.ThemeSetting
import io.music_assistant.client.ui.theme.isDark
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.settings_allow_landscape
import musicassistantclient.composeapp.generated.resources.settings_allow_landscape_hint
import musicassistantclient.composeapp.generated.resources.settings_badge_explicit
import musicassistantclient.composeapp.generated.resources.settings_badge_preview_subtitle
import musicassistantclient.composeapp.generated.resources.settings_badge_preview_title
import musicassistantclient.composeapp.generated.resources.settings_badge_progress
import musicassistantclient.composeapp.generated.resources.settings_badge_source
import musicassistantclient.composeapp.generated.resources.settings_badge_type
import musicassistantclient.composeapp.generated.resources.settings_badges
import musicassistantclient.composeapp.generated.resources.settings_dynamic_colors
import musicassistantclient.composeapp.generated.resources.settings_dynamic_colors_hint
import musicassistantclient.composeapp.generated.resources.settings_interface
import musicassistantclient.composeapp.generated.resources.settings_theme
import org.jetbrains.compose.resources.stringResource

/** The space between two groups of the interface card. */
private val GROUP_SPACING = 24.dp

/**
 * The look of the app: the theme, the accent color, the dynamic colors, the item badges, and the
 * landscape escape hatch. The badge switches sit next to a sample tile that shows their effect.
 */
@Composable
fun InterfaceSection(
    theme: ThemeSetting,
    onThemeChange: (ThemeSetting) -> Unit,
    accent: AccentColor,
    onAccentChange: (AccentColor) -> Unit,
    dynamicColors: Boolean,
    onDynamicColorsChange: (Boolean) -> Unit,
    badges: BadgeVisibility,
    onBadgesChange: (BadgeVisibility) -> Unit,
    allowLandscape: Boolean,
    onAllowLandscapeChange: (Boolean) -> Unit,
) {
    Column {
        // The same shape as the local player heading, whose switch makes it 48.dp tall, so the
        // two cards line up when they sit side by side.
        Box(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .heightIn(min = 48.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = stringResource(Res.string.settings_interface),
                style = MaterialTheme.typography.titleLargeEmphasized,
            )
        }
        SectionCard {
            SectionTitle(stringResource(Res.string.settings_theme))
            ThemeChooser(
                modifier = Modifier.fillMaxWidth(),
                currentTheme = theme,
                onThemeChange = onThemeChange,
            )

            Spacer(Modifier.height(GROUP_SPACING))
            AccentColorSection(
                current = accent,
                darkTheme = theme.isDark(),
                onChange = onAccentChange,
            )

            Spacer(Modifier.height(GROUP_SPACING))
            SettingSwitch(
                title = stringResource(Res.string.settings_dynamic_colors),
                hint = stringResource(Res.string.settings_dynamic_colors_hint),
                checked = dynamicColors,
                onCheckedChange = onDynamicColorsChange,
            )

            Spacer(Modifier.height(GROUP_SPACING))
            SectionTitle(stringResource(Res.string.settings_badges))
            BadgeSettings(badges, onBadgesChange)

            Spacer(Modifier.height(GROUP_SPACING))
            SettingSwitch(
                title = stringResource(Res.string.settings_allow_landscape),
                hint = stringResource(Res.string.settings_allow_landscape_hint),
                checked = allowLandscape,
                onCheckedChange = onAllowLandscapeChange,
            )
        }
    }
}

@Composable
private fun BadgeSettings(
    badges: BadgeVisibility,
    onChange: (BadgeVisibility) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SettingSwitch(
                title = stringResource(Res.string.settings_badge_progress),
                checked = badges.progress,
                onCheckedChange = { onChange(badges.copy(progress = it)) },
            )
            SettingSwitch(
                title = stringResource(Res.string.settings_badge_type),
                checked = badges.type,
                onCheckedChange = { onChange(badges.copy(type = it)) },
            )
            SettingSwitch(
                title = stringResource(Res.string.settings_badge_source),
                checked = badges.source,
                onCheckedChange = { onChange(badges.copy(source = it)) },
            )
            SettingSwitch(
                title = stringResource(Res.string.settings_badge_explicit),
                checked = badges.explicit,
                onCheckedChange = { onChange(badges.copy(explicit = it)) },
            )
        }
        // As wide as the smallest grid tile on this screen, so it does not grow on a tablet.
        BadgePreview(
            modifier = Modifier.weight(1f, fill = false).widthIn(max = gridItemMinSize()),
            badges = badges,
        )
    }
}

/**
 * A sample album tile with the chosen badges: the type, the played state, the library as the
 * source, and the explicit mark. It is a picture of the switches next to it, so screen readers
 * skip it.
 */
@Composable
private fun BadgePreview(
    modifier: Modifier,
    badges: BadgeVisibility,
) {
    val title = stringResource(Res.string.settings_badge_preview_title)
    Column(modifier = modifier.clearAndSetSemantics {}) {
        Box {
            MediaArtwork(imageUrl = null, contentDescription = title, icon = AlbumIcon)
            if (badges.type) {
                TypeBadge(
                    icon = AlbumIcon,
                    size = GRID_BADGE_SIZE,
                    modifier = Modifier.align(Alignment.TopStart).padding(GRID_BADGE_PADDING),
                )
            }
            if (badges.explicit) {
                ExplicitBadge(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(GRID_BADGE_PADDING)
                        .size(GRID_BADGE_SIZE),
                )
            }
            // Gated by LocalBadgeVisibility, which follows the same setting.
            ProgressBadge(fullyPlayed = true, resumePositionMs = null, badgeSize = GRID_BADGE_SIZE)
            if (badges.source) {
                Icon(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(GRID_BADGE_PADDING)
                        .size(GRID_BADGE_SIZE)
                        .background(Color.Gray, CircleShape)
                        .padding(2.dp),
                    imageVector = BookshelfIcon,
                    contentDescription = null,
                    tint = Color.White,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = stringResource(Res.string.settings_badge_preview_subtitle),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A setting that is on or off: the title, an optional hint under it, and a switch at the end. */
@Composable
private fun SettingSwitch(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    hint: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            hint?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // The row handles the toggle, so the switch only shows the state.
        Switch(checked = checked, onCheckedChange = null)
    }
}
