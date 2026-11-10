package io.music_assistant.client.ui.compose.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material3.Icon
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import io.music_assistant.client.ui.theme.ThemeSetting
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.settings_theme_dark
import musicassistantclient.composeapp.generated.resources.settings_theme_light
import musicassistantclient.composeapp.generated.resources.settings_theme_system
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** The order of the theme choices, as the user reads them. */
private val THEME_CHOICES = listOf(ThemeSetting.FollowSystem, ThemeSetting.Light, ThemeSetting.Dark)

/**
 * A segmented button for the light or dark choice. The selected segment shows a check instead
 * of its icon.
 */
@Composable
fun ThemeChooser(
    modifier: Modifier = Modifier,
    currentTheme: ThemeSetting,
    onThemeChange: (ThemeSetting) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        THEME_CHOICES.forEachIndexed { index, theme ->
            val selected = theme == currentTheme
            SegmentedButton(
                selected = selected,
                onClick = { onThemeChange(theme) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = THEME_CHOICES.size),
                icon = {
                    SegmentedButtonDefaults.Icon(active = selected) {
                        Icon(imageVector = theme.icon, contentDescription = null)
                    }
                },
                label = { Text(stringResource(theme.label)) },
            )
        }
    }
}

private val ThemeSetting.icon: ImageVector
    get() = when (this) {
        ThemeSetting.FollowSystem -> Icons.Outlined.BrightnessAuto
        ThemeSetting.Light -> Icons.Outlined.LightMode
        ThemeSetting.Dark -> Icons.Outlined.DarkMode
    }

private val ThemeSetting.label: StringResource
    get() = when (this) {
        ThemeSetting.FollowSystem -> Res.string.settings_theme_system
        ThemeSetting.Light -> Res.string.settings_theme_light
        ThemeSetting.Dark -> Res.string.settings_theme_dark
    }
