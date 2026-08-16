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

@Composable
fun AppMediaItem.navigationOptions(navigateToItem: (AppMediaItem) -> Unit): List<OverflowMenuOption> {
    val item = this
    // When an item has multiple artists we can't pick for the user; hold the candidates
    // here so the shared "Choose artist" dialog (emitted below) can resolve the choice.
    var artistChoices by remember { mutableStateOf<List<Artist>?>(null) }
    val options = buildList {
        when (item) {
            is Track -> {
                if (item.album != null) {
                    add(
                        OverflowMenuOption(
                            title = stringResource(Res.string.action_go_to_album),
                            icon = Icons.Default.Album,
                            onClick = {
                                navigateToItem(item.album)
                            },
                        ),
                    )
                }

                if (item.artists.isNotEmpty()) {
                    add(goToArtist(item.artists, navigateToItem, onChoose = { artistChoices = it }))
                }
            }

            is Album -> {
                if (item.artists.isNotEmpty()) {
                    add(goToArtist(item.artists, navigateToItem, onChoose = { artistChoices = it }))
                }
            }

            else -> Unit
        }
    }

    artistChoices?.let { artists ->
        ChooseArtistDialog(
            artists = artists,
            onSelect = {
                navigateToItem(it)
                artistChoices = null
            },
            onDismiss = { artistChoices = null },
        )
    }

    return options
}

@Composable
private fun goToArtist(
    artists: List<Artist>,
    navigateToItem: (AppMediaItem) -> Unit,
    onChoose: (List<Artist>) -> Unit,
): OverflowMenuOption {
    return OverflowMenuOption(
        title = stringResource(Res.string.action_go_to_artist),
        icon = Icons.Default.Person,
        onClick = {
            // A single artist navigates straight through; multiple defers to the dialog.
            if (artists.size == 1) navigateToItem(artists[0]) else onChoose(artists)
        },
    )
}
