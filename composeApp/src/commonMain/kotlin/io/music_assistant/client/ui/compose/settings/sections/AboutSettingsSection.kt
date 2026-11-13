package io.music_assistant.client.ui.compose.settings.sections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import io.music_assistant.client.ui.compose.settings.SectionCard
import io.music_assistant.client.utils.AppVersion
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.settings_about_description
import musicassistantclient.composeapp.generated.resources.settings_about_learn_more
import musicassistantclient.composeapp.generated.resources.settings_app_documentation
import musicassistantclient.composeapp.generated.resources.settings_app_version_info
import org.jetbrains.compose.resources.stringResource

@Composable
fun AboutSettingsSection(appVersion: AppVersion) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        MusicAssistantDescription()
        AppVersionSection(appVersion)
    }
}

@Composable
private fun MusicAssistantDescription() {
    val uriHandler = LocalUriHandler.current
    SectionCard {
        Text(
            text = stringResource(Res.string.settings_about_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.size(4.dp))
        Text(
            text = stringResource(Res.string.settings_about_learn_more),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable { uriHandler.openUri("https://music-assistant.io") },
        )
    }
}

@Composable
private fun AppVersionSection(appVersion: AppVersion) {
    val uriHandler = LocalUriHandler.current
    SectionCard {
        Text(
            text = stringResource(Res.string.settings_app_version_info, appVersion.name, appVersion.code),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
        )
        Spacer(modifier = Modifier.size(4.dp))
        Text(
            text = stringResource(Res.string.settings_app_documentation),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable { uriHandler.openUri("https://music-assistant.github.io/mobile-app/") },
        )
    }
}
