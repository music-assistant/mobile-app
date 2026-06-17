package io.music_assistant.client.ui.compose.common.items

import androidx.compose.runtime.Composable
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.Genre
import io.music_assistant.client.data.model.client.items.Playlist
import io.music_assistant.client.data.model.client.items.Podcast
import io.music_assistant.client.data.model.client.items.RadioStation
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.item_subtitle_genre
import musicassistantclient.composeapp.generated.resources.item_subtitle_playlist
import musicassistantclient.composeapp.generated.resources.item_subtitle_playlist_dynamic
import musicassistantclient.composeapp.generated.resources.item_subtitle_podcast
import musicassistantclient.composeapp.generated.resources.item_subtitle_radio
import org.jetbrains.compose.resources.stringResource

/**
 * Returns the item's `subtitle` if non-null; otherwise a localized fallback
 * for types whose subtitle is just the content-type label.
 */
@Composable
fun AppMediaItem.localizedSubtitle(): String? = subtitle ?: when (this) {
    is Playlist -> stringResource(if (this.isDynamic) Res.string.item_subtitle_playlist_dynamic else Res.string.item_subtitle_playlist)
    is Genre -> stringResource(Res.string.item_subtitle_genre)
    is Podcast -> stringResource(Res.string.item_subtitle_podcast)
    is RadioStation -> stringResource(Res.string.item_subtitle_radio)
    else -> null
}
