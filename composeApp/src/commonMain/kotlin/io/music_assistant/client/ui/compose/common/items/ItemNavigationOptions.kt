package io.music_assistant.client.ui.compose.common.items

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.music_assistant.client.data.model.client.items.Album
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.Artist
import io.music_assistant.client.data.model.client.items.Track
import io.music_assistant.client.ui.compose.common.OverflowMenuOption
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.action_go_to_album
import musicassistantclient.composeapp.generated.resources.action_go_to_artist
import org.jetbrains.compose.resources.stringResource

/**
 * "Go to album" / "Go to artist" entries for [this] item.
 *
 * @param containerItem the item whose screen this list belongs to. Its own entry is dropped,
 * because navigating there would push a duplicate of the screen the user already sees.
 */
@Composable
fun AppMediaItem.navigationOptions(
    navigateToItem: (AppMediaItem) -> Unit,
    containerItem: AppMediaItem? = null,
): List<OverflowMenuOption> {
    val item = this
    val artists = when (item) {
        is Track -> item.artists
        is Album -> item.artists
        else -> emptyList()
    }.otherThan(containerItem)
    val goToArtist = rememberArtistNavigation(artists, navigateToItem)
    return buildList {
        if (item is Track && item.album != null && !item.album.isSameItemAs(containerItem)) {
            add(
                OverflowMenuOption(
                    title = stringResource(Res.string.action_go_to_album),
                    icon = Icons.Default.Album,
                    onClick = { navigateToItem(item.album) },
                ),
            )
        }
        goToArtist?.let {
            add(
                OverflowMenuOption(
                    title = stringResource(Res.string.action_go_to_artist),
                    icon = Icons.Default.Person,
                    onClick = it,
                ),
            )
        }
    }
}

/**
 * Click handler that opens one of [artists]: a single artist navigates straight through,
 * several go to [onChoose]. Null when [artists] is empty.
 */
fun artistNavigation(
    artists: List<Artist>,
    navigateToItem: (AppMediaItem) -> Unit,
    onChoose: (List<Artist>) -> Unit,
): (() -> Unit)? = artists.takeIf { it.isNotEmpty() }?.let { candidates ->
    {
        candidates.singleOrNull()?.let(navigateToItem) ?: onChoose(candidates)
    }
}

/** [artistNavigation] that resolves several artists with the "Choose artist" dialog (emitted here). */
@Composable
fun rememberArtistNavigation(
    artists: List<Artist>,
    navigateToItem: (AppMediaItem) -> Unit,
): (() -> Unit)? {
    // We can't pick for the user among several artists; hold the candidates for the dialog.
    var artistChoices by remember { mutableStateOf<List<Artist>?>(null) }
    artistChoices?.let { choices ->
        ChooseArtistDialog(
            artists = choices,
            onSelect = {
                navigateToItem(it)
                artistChoices = null
            },
            onDismiss = { artistChoices = null },
        )
    }
    return artistNavigation(artists, navigateToItem) { artistChoices = it }
}

/** Drops the artist whose screen the list belongs to; navigating there would go nowhere. */
private fun List<Artist>.otherThan(containerItem: AppMediaItem?): List<Artist> =
    filterNot { it.isSameItemAs(containerItem) }

/** The same library entry, regardless of which provider mapping it arrived through. */
private fun AppMediaItem.isSameItemAs(other: AppMediaItem?): Boolean =
    other != null && itemId == other.itemId && mediaType == other.mediaType
