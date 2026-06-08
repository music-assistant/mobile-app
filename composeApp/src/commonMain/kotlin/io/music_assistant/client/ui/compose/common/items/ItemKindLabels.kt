package io.music_assistant.client.ui.compose.common.items

import io.music_assistant.client.data.model.client.ItemKind
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.kind_album
import musicassistantclient.composeapp.generated.resources.kind_artist
import musicassistantclient.composeapp.generated.resources.kind_audiobook
import musicassistantclient.composeapp.generated.resources.kind_playlist
import musicassistantclient.composeapp.generated.resources.kind_podcast
import musicassistantclient.composeapp.generated.resources.kind_podcast_episode
import musicassistantclient.composeapp.generated.resources.kind_radio
import musicassistantclient.composeapp.generated.resources.kind_track
import org.jetbrains.compose.resources.StringResource

/** Display label for an item kind. Shared by the customize and car-actions dialogs. */
fun ItemKind.labelRes(): StringResource = when (this) {
    ItemKind.TRACK -> Res.string.kind_track
    ItemKind.RADIO -> Res.string.kind_radio
    ItemKind.PODCAST_EPISODE -> Res.string.kind_podcast_episode
    ItemKind.ALBUM -> Res.string.kind_album
    ItemKind.ARTIST -> Res.string.kind_artist
    ItemKind.PLAYLIST -> Res.string.kind_playlist
    ItemKind.PODCAST -> Res.string.kind_podcast
    ItemKind.AUDIOBOOK -> Res.string.kind_audiobook
}
