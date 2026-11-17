package io.music_assistant.client.ui.compose.settings.sections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.music_assistant.client.discovery.DiscoveredServer
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.settings_discovered_server_ha_addon
import musicassistantclient.composeapp.generated.resources.settings_discovered_servers
import org.jetbrains.compose.resources.stringResource

/** The servers that mDNS found. A tap connects to one. Nothing shows while none is found. */
@Composable
fun DiscoveredServersList(
    servers: List<DiscoveredServer>,
    enabled: Boolean,
    onSelect: (DiscoveredServer) -> Unit,
) {
    if (servers.isEmpty()) return
    Column(modifier = Modifier.padding(bottom = 12.dp)) {
        Text(
            text = stringResource(Res.string.settings_discovered_servers),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        servers.forEach { server ->
            val address = server.connection.run { "$host:$port$basePath" }
            val haAddon = stringResource(Res.string.settings_discovered_server_ha_addon)
            ListItem(
                modifier = Modifier.clickable(enabled = enabled) { onSelect(server) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                leadingContent = { Icon(Icons.Default.Dns, contentDescription = null) },
                headlineContent = { Text(server.name) },
                supportingContent = {
                    Text(if (server.isHomeAssistantAddon) "$address · $haAddon" else address)
                },
            )
        }
    }
}
