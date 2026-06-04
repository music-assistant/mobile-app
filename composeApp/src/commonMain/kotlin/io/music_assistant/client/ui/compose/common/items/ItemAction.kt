package io.music_assistant.client.ui.compose.common.items

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.AddToQueue
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlaylistAddCircle
import androidx.compose.material.icons.filled.QueuePlayNext
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Tune
import androidx.compose.ui.graphics.vector.ImageVector
import compose.icons.TablerIcons
import compose.icons.tablericons.FolderMinus
import compose.icons.tablericons.FolderPlus
import compose.icons.tablericons.Heart
import compose.icons.tablericons.HeartBroken
import io.music_assistant.client.data.model.client.QueueOption
import io.music_assistant.client.ui.compose.common.icons.PlayIcon
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.action_add_to_library
import musicassistantclient.composeapp.generated.resources.action_add_to_playlist
import musicassistantclient.composeapp.generated.resources.action_add_to_queue
import musicassistantclient.composeapp.generated.resources.action_customize
import musicassistantclient.composeapp.generated.resources.action_favorite
import musicassistantclient.composeapp.generated.resources.action_insert_next
import musicassistantclient.composeapp.generated.resources.action_insert_next_and_play
import musicassistantclient.composeapp.generated.resources.action_mark_played
import musicassistantclient.composeapp.generated.resources.action_mark_unplayed
import musicassistantclient.composeapp.generated.resources.action_play_now
import musicassistantclient.composeapp.generated.resources.action_remove_from_library
import musicassistantclient.composeapp.generated.resources.action_remove_from_playlist
import musicassistantclient.composeapp.generated.resources.action_start_radio
import musicassistantclient.composeapp.generated.resources.action_unfavorite
import org.jetbrains.compose.resources.StringResource

sealed class ItemAction(val kind: Kind) {
    enum class Kind { PLAYBACK, OTHER }

    data class Play(val queueOption: QueueOption) : ItemAction(Kind.PLAYBACK)
    data object StartRadio : ItemAction(Kind.PLAYBACK)

    data object AddToLibrary : ItemAction(Kind.OTHER)
    data object RemoveFromLibrary : ItemAction(Kind.OTHER)
    data object Favorite : ItemAction(Kind.OTHER)
    data object Unfavorite : ItemAction(Kind.OTHER)

    data object AddToPlaylist : ItemAction(Kind.OTHER)
    data object RemoveFromPlaylist : ItemAction(Kind.OTHER)

    data object MarkPlayed : ItemAction(Kind.OTHER)
    data object MarkUnplayed : ItemAction(Kind.OTHER)

    data object Customize : ItemAction(Kind.OTHER)
}

fun ItemAction.title(): StringResource = when (this) {
    is ItemAction.Play -> when (queueOption) {
        QueueOption.REPLACE -> Res.string.action_play_now
        QueueOption.PLAY -> Res.string.action_insert_next_and_play
        QueueOption.NEXT -> Res.string.action_insert_next
        QueueOption.ADD -> Res.string.action_add_to_queue
    }
    ItemAction.StartRadio -> Res.string.action_start_radio
    ItemAction.AddToLibrary -> Res.string.action_add_to_library
    ItemAction.RemoveFromLibrary -> Res.string.action_remove_from_library
    ItemAction.Favorite -> Res.string.action_favorite
    ItemAction.Unfavorite -> Res.string.action_unfavorite
    ItemAction.AddToPlaylist -> Res.string.action_add_to_playlist
    ItemAction.RemoveFromPlaylist -> Res.string.action_remove_from_playlist
    ItemAction.MarkPlayed -> Res.string.action_mark_played
    ItemAction.MarkUnplayed -> Res.string.action_mark_unplayed
    ItemAction.Customize -> Res.string.action_customize
}

fun ItemAction.icon(): ImageVector = when (this) {
    is ItemAction.Play -> when (queueOption) {
        QueueOption.REPLACE -> PlayIcon
        QueueOption.PLAY -> Icons.Default.PlaylistAddCircle
        QueueOption.NEXT -> Icons.Default.QueuePlayNext
        QueueOption.ADD -> Icons.Default.AddToQueue
    }
    ItemAction.StartRadio -> Icons.Default.CellTower
    ItemAction.AddToLibrary -> TablerIcons.FolderPlus
    ItemAction.RemoveFromLibrary -> TablerIcons.FolderMinus
    ItemAction.Favorite -> TablerIcons.Heart
    ItemAction.Unfavorite -> TablerIcons.HeartBroken
    ItemAction.AddToPlaylist -> Icons.AutoMirrored.Filled.PlaylistAdd
    ItemAction.RemoveFromPlaylist -> Icons.Default.Delete
    ItemAction.MarkPlayed -> Icons.Default.Check
    ItemAction.MarkUnplayed -> Icons.Default.Replay
    ItemAction.Customize -> Icons.Default.Tune
}
