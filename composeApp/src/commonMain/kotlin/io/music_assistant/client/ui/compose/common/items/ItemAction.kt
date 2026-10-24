package io.music_assistant.client.ui.compose.common.items

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.AddToQueue
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlaylistAddCircle
import androidx.compose.material.icons.filled.PlaylistRemove
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.QueuePlayNext
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Tune
import androidx.compose.ui.graphics.vector.ImageVector
import compose.icons.TablerIcons
import compose.icons.tablericons.FolderMinus
import compose.icons.tablericons.FolderPlus
import compose.icons.tablericons.Heart
import compose.icons.tablericons.HeartBroken
import compose.icons.tablericons.ThumbDown
import io.music_assistant.client.data.model.client.ClickContext
import io.music_assistant.client.data.model.client.QueueOption
import io.music_assistant.client.data.play.PlayMode
import io.music_assistant.client.ui.compose.common.icons.AlbumIcon
import io.music_assistant.client.ui.compose.common.icons.PlayIcon
import io.music_assistant.client.ui.compose.common.icons.PlaylistIcon
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.action_add_to_library
import musicassistantclient.composeapp.generated.resources.action_add_to_playlist
import musicassistantclient.composeapp.generated.resources.action_add_to_queue
import musicassistantclient.composeapp.generated.resources.action_customize
import musicassistantclient.composeapp.generated.resources.action_dislike
import musicassistantclient.composeapp.generated.resources.action_favorite
import musicassistantclient.composeapp.generated.resources.action_insert_next
import musicassistantclient.composeapp.generated.resources.action_insert_next_and_play
import musicassistantclient.composeapp.generated.resources.action_mark_played
import musicassistantclient.composeapp.generated.resources.action_mark_unplayed
import musicassistantclient.composeapp.generated.resources.action_play_album_from_here
import musicassistantclient.composeapp.generated.resources.action_play_from_beginning
import musicassistantclient.composeapp.generated.resources.action_play_from_here
import musicassistantclient.composeapp.generated.resources.action_play_now
import musicassistantclient.composeapp.generated.resources.action_play_playlist_from_here
import musicassistantclient.composeapp.generated.resources.action_play_podcast_from_here
import musicassistantclient.composeapp.generated.resources.action_play_shuffled
import musicassistantclient.composeapp.generated.resources.action_remove_dislike
import musicassistantclient.composeapp.generated.resources.action_remove_from_library
import musicassistantclient.composeapp.generated.resources.action_remove_from_playlist
import musicassistantclient.composeapp.generated.resources.action_replace_next
import musicassistantclient.composeapp.generated.resources.action_start_endless_mix
import musicassistantclient.composeapp.generated.resources.action_unfavorite
import org.jetbrains.compose.resources.StringResource

sealed class ItemAction(val kind: Kind) {
    enum class Kind { PLAYBACK, OTHER }

    data class Play(val queueOption: QueueOption) : ItemAction(Kind.PLAYBACK)
    data object PlayFromHere : ItemAction(Kind.PLAYBACK)
    data object PlayShuffled : ItemAction(Kind.PLAYBACK)
    data object PlayFromBeginning : ItemAction(Kind.PLAYBACK)
    data object StartEndlessMix : ItemAction(Kind.PLAYBACK)

    data object AddToLibrary : ItemAction(Kind.OTHER)
    data object RemoveFromLibrary : ItemAction(Kind.OTHER)

    /** An entry of the favorite menu. [favorite] is the state that it sets. */
    sealed class SetFavorite(val favorite: Boolean?) : ItemAction(Kind.OTHER)
    data object Favorite : SetFavorite(true)
    data object Unfavorite : SetFavorite(null)
    data object Dislike : SetFavorite(false)
    data object RemoveDislike : SetFavorite(null)

    data object AddToPlaylist : ItemAction(Kind.OTHER)
    data object RemoveFromPlaylist : ItemAction(Kind.OTHER)

    data object MarkPlayed : ItemAction(Kind.OTHER)
    data object MarkUnplayed : ItemAction(Kind.OTHER)

    data object Customize : ItemAction(Kind.PLAYBACK)
}

/** The play request that this action asks for, or null for an action that does not play. */
fun ItemAction.toPlayMode(): PlayMode? = when (this) {
    is ItemAction.Play -> PlayMode(queueOption)
    ItemAction.PlayFromHere -> PlayMode(QueueOption.REPLACE, fromHereInParent = true)
    ItemAction.PlayShuffled -> PlayMode(QueueOption.REPLACE, shuffle = true)
    // The web app inserts the episode next and plays it, so the rest of the queue stays.
    ItemAction.PlayFromBeginning -> PlayMode(QueueOption.PLAY, startFromBeginning = true)
    ItemAction.StartEndlessMix -> PlayMode(QueueOption.REPLACE, endlessMix = true)
    else -> null
}

fun ItemAction.title(context: ClickContext? = null): StringResource = when (this) {
    is ItemAction.Play -> when (queueOption) {
        QueueOption.REPLACE -> Res.string.action_play_now
        QueueOption.PLAY -> Res.string.action_insert_next_and_play
        QueueOption.NEXT -> Res.string.action_insert_next
        QueueOption.ADD -> Res.string.action_add_to_queue
        QueueOption.REPLACE_NEXT -> Res.string.action_replace_next
    }

    is ItemAction.PlayFromHere -> when (context) {
        ClickContext.ALBUM -> Res.string.action_play_album_from_here
        ClickContext.PLAYLIST -> Res.string.action_play_playlist_from_here
        ClickContext.PODCAST -> Res.string.action_play_podcast_from_here
        // Context-less surfaces (e.g. the per-kind car tap-action dropdown) apply to both
        // containers, so they get the generic label rather than an album/playlist-specific one.
        else -> Res.string.action_play_from_here
    }

    ItemAction.PlayShuffled -> Res.string.action_play_shuffled
    ItemAction.PlayFromBeginning -> Res.string.action_play_from_beginning
    ItemAction.StartEndlessMix -> Res.string.action_start_endless_mix
    ItemAction.AddToLibrary -> Res.string.action_add_to_library
    ItemAction.RemoveFromLibrary -> Res.string.action_remove_from_library
    ItemAction.Favorite -> Res.string.action_favorite
    ItemAction.Unfavorite -> Res.string.action_unfavorite
    ItemAction.Dislike -> Res.string.action_dislike
    ItemAction.RemoveDislike -> Res.string.action_remove_dislike
    ItemAction.AddToPlaylist -> Res.string.action_add_to_playlist
    ItemAction.RemoveFromPlaylist -> Res.string.action_remove_from_playlist
    ItemAction.MarkPlayed -> Res.string.action_mark_played
    ItemAction.MarkUnplayed -> Res.string.action_mark_unplayed
    ItemAction.Customize -> Res.string.action_customize
}

fun ItemAction.icon(context: ClickContext?): ImageVector = when (this) {
    is ItemAction.Play -> when (queueOption) {
        QueueOption.REPLACE -> PlayIcon
        QueueOption.PLAY -> Icons.Default.PlaylistAddCircle
        QueueOption.NEXT -> Icons.Default.QueuePlayNext
        QueueOption.ADD -> Icons.Default.AddToQueue
        QueueOption.REPLACE_NEXT -> Icons.Default.PlaylistRemove
    }

    is ItemAction.PlayFromHere -> when (context) {
        ClickContext.ALBUM -> AlbumIcon
        ClickContext.PLAYLIST -> PlaylistIcon
        ClickContext.PODCAST -> Icons.Default.Podcasts
        else -> Icons.AutoMirrored.Filled.PlaylistPlay
    }

    ItemAction.PlayShuffled -> Icons.Default.Shuffle
    ItemAction.PlayFromBeginning -> Icons.Default.RestartAlt
    ItemAction.StartEndlessMix -> Icons.Default.CellTower
    ItemAction.AddToLibrary -> TablerIcons.FolderPlus
    ItemAction.RemoveFromLibrary -> TablerIcons.FolderMinus
    ItemAction.Favorite -> TablerIcons.Heart
    ItemAction.Unfavorite -> TablerIcons.HeartBroken
    ItemAction.Dislike,
    ItemAction.RemoveDislike,
    -> TablerIcons.ThumbDown
    ItemAction.AddToPlaylist -> Icons.AutoMirrored.Filled.PlaylistAdd
    ItemAction.RemoveFromPlaylist -> Icons.Default.Delete
    ItemAction.MarkPlayed -> Icons.Default.Check
    ItemAction.MarkUnplayed -> Icons.Default.Replay
    ItemAction.Customize -> Icons.Default.Tune
}
