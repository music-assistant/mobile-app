package io.music_assistant.client.ui.compose.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Brands
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.brands.Adn
import compose.icons.fontawesomeicons.solid.Moon
import compose.icons.fontawesomeicons.solid.Sun
import io.music_assistant.client.ui.theme.ThemeSetting

@Composable
fun ThemeChooser(
    modifier: Modifier = Modifier,
    currentTheme: ThemeSetting,
    onThemeChange: (ThemeSetting) -> Unit,
) {
    Row(
        modifier = modifier
            .background(MaterialTheme.colorScheme.onSecondary, RoundedCornerShape(6.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ThemeToggleButton(
            icon = FontAwesomeIcons.Solid.Sun,
            selected = currentTheme == ThemeSetting.Light,
            onClick = { onThemeChange(ThemeSetting.Light) },
        )
        ThemeToggleButton(
            icon = FontAwesomeIcons.Brands.Adn,
            selected = currentTheme == ThemeSetting.FollowSystem,
            onClick = { onThemeChange(ThemeSetting.FollowSystem) },
        )
        ThemeToggleButton(
            icon = FontAwesomeIcons.Solid.Moon,
            selected = currentTheme == ThemeSetting.Dark,
            onClick = { onThemeChange(ThemeSetting.Dark) },
        )
    }
}

@Composable
fun ThemeToggleButton(
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Icon(
        modifier = Modifier
            .size(24.dp)
            .clickable { onClick() }
            .background(
                if (selected) MaterialTheme.colorScheme.secondary else Color.Transparent,
                RoundedCornerShape(12.dp),
            )
            .padding(4.dp),
        imageVector = icon,
        contentDescription = null,
        tint = if (selected) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.secondary,
    )
}
