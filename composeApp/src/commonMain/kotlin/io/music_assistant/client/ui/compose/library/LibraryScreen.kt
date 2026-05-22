package io.music_assistant.client.ui.compose.library

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
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
import io.music_assistant.client.ui.compose.common.painters.rememberPlaceholderPainter

@Composable
fun LibraryScreen(onTypeClick: (MediaType) -> Unit) {
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

    LazyColumn {
        items(libraryItems) {
            LibraryItemCard(
                name = it.name,
                icon = it.icon,
                onClick = { onTypeClick(it.type) },
            )
        }
    }
}

private data class LibraryItem(
    val name: String,
    val icon: ImageVector,
    val type: MediaType,
)

@Composable
fun LibraryItemCard(
    modifier: Modifier = Modifier,
    name: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val primary = MaterialTheme.colorScheme.primary

    Column(
        modifier = modifier
            .wrapContentSize()
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(width = 96.dp, height = 40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            val placeholder = rememberPlaceholderPainter(
                backgroundColor = primaryContainer,
                iconColor = primary,
                icon = icon,
            )

            Image(
                painter = placeholder,
                contentDescription = name,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Spacer(Modifier.height(4.dp))

        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}
