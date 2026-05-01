package io.music_assistant.client.ui.compose.common.items

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.Composable
import io.music_assistant.client.data.model.client.AppMediaItem
import io.music_assistant.client.ui.compose.common.OverflowMenuOption
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.action_go_to_artist
import org.jetbrains.compose.resources.stringResource

@Composable
fun AppMediaItem.navigationOptions(navigateToItem: (AppMediaItem) -> Unit): List<OverflowMenuOption> {
    val item = this
    return buildList {
        when (item) {
            is AppMediaItem.Track -> {
                add(goToArtist(item.artists!![0], navigateToItem))
            }

            is AppMediaItem.Album -> {
                if (artists.isNotEmpty()) {
                    add(goToArtist(item.artists[0], navigateToItem))
                }
            }
        }
    }
}

@Composable
private fun goToArtist(
    artist: AppMediaItem.Artist,
    navigateToItem: (AppMediaItem) -> Unit,
): OverflowMenuOption = OverflowMenuOption(
    title = stringResource(Res.string.action_go_to_artist),
    icon = Icons.Default.Person,
    onClick = {
        navigateToItem(artist)
    },
)
