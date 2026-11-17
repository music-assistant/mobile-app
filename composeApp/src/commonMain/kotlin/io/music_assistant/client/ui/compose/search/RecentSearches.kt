package io.music_assistant.client.ui.compose.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.search_recent
import musicassistantclient.composeapp.generated.resources.search_start
import org.jetbrains.compose.resources.stringResource

/**
 * The idle search screen: the recent searches that contain [query], like the web frontend,
 * or the start hint when none match.
 */
@Composable
fun RecentSearches(
    recentSearches: List<String>,
    query: String,
    onClick: (String) -> Unit,
    contentPadding: PaddingValues,
) {
    // Another client writes the list too, so an exact duplicate must not become a duplicate key.
    val matching = remember(recentSearches, query) {
        query.trim().let { term -> recentSearches.distinct().filter { it.contains(term, ignoreCase = true) } }
    }
    if (matching.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(Res.string.search_start))
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = contentPadding) {
            item(contentType = "header") {
                ListItem(
                    headlineContent = {
                        Text(
                            text = stringResource(Res.string.search_recent),
                            style = MaterialTheme.typography.titleSmall,
                        )
                    },
                )
            }
            items(matching, key = { it }, contentType = { "recent" }) { term ->
                ListItem(
                    headlineContent = { Text(term, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingContent = { Icon(Icons.Default.History, contentDescription = null) },
                    modifier = Modifier.clickable { onClick(term) },
                )
            }
        }
    }
}
