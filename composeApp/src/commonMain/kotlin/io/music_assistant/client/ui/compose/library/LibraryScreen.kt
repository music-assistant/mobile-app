package io.music_assistant.client.ui.compose.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.music_assistant.client.data.model.client.MediaType
import io.music_assistant.client.ui.compose.common.icons.AlbumIcon
import io.music_assistant.client.ui.compose.common.icons.ArtistIcon
import io.music_assistant.client.ui.compose.common.icons.BookAudioIcon
import io.music_assistant.client.ui.compose.common.icons.GenreIcon
import io.music_assistant.client.ui.compose.common.icons.PlaylistIcon
import io.music_assistant.client.ui.compose.common.icons.RadioIcon
import io.music_assistant.client.ui.compose.common.icons.TrackIcon
import io.music_assistant.client.ui.compose.nav.Screen
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.nav_library
import org.jetbrains.compose.resources.stringResource

@Composable
fun LibraryScreen(onTypeClick: (MediaType) -> Unit) {
    Screen(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.nav_library)) },
            )
        },
    ) {
        val libraryItems = remember {
            listOf(
                LibraryItem("Artists", ArtistIcon, MediaType.ARTIST),
                LibraryItem("Albums", AlbumIcon, MediaType.ALBUM),
                LibraryItem("Tracks", TrackIcon, MediaType.TRACK),
                LibraryItem("Playlists", PlaylistIcon, MediaType.PLAYLIST),
                LibraryItem("Audiobooks", BookAudioIcon, MediaType.AUDIOBOOK),
                LibraryItem("Podcasts", Icons.Default.Podcasts, MediaType.PODCAST),
                LibraryItem("Radio", RadioIcon, MediaType.RADIO),
                LibraryItem("Genres", GenreIcon, MediaType.GENRE),
            )
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 180.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
        ) {
            items(libraryItems) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = { onTypeClick(it.type) })
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 8.dp, vertical = 16.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            modifier = Modifier.size(24.dp),
                            imageVector = it.icon,
                            contentDescription = it.name,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )

                        Text(
                            modifier = Modifier.padding(start = 16.dp),
                            text = it.name,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
        }
    }
}

private data class LibraryItem(
    val name: String,
    val icon: ImageVector,
    val type: MediaType,
)
