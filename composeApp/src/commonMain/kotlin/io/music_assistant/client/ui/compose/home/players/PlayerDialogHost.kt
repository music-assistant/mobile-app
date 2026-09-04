package io.music_assistant.client.ui.compose.home.players

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.data.model.client.byId
import io.music_assistant.client.data.model.client.items.Track
import io.music_assistant.client.data.model.client.lyrics
import io.music_assistant.client.ui.compose.common.action.PlayerAction
import io.music_assistant.client.ui.compose.common.items.AddToPlaylistDialog
import io.music_assistant.client.ui.compose.common.items.PlaylistActions
import io.music_assistant.client.ui.compose.home.HomeScreenViewModel

/**
 * Renders the one open player dialog, outside the pager.
 *
 * Every dialog on the players screen lives here so that removing or reordering a pager page
 * can never tear down an open dialog. The host resolves [request] against the newest
 * [players] on each composition: when the player or the track the request names is gone, it
 * clears the request instead of leaving a stale id behind that a returning player would
 * revive.
 *
 * [homeScreenViewModel] is passed whole rather than as one callback per action: this is a
 * feature composable for a single screen, and the narrow form needs eleven parameters.
 */
@Composable
fun PlayerDialogHost(
    request: PlayerDialogRequest?,
    players: List<PlayerData>,
    homeScreenViewModel: HomeScreenViewModel,
    dspSettingsViewModel: DspSettingsViewModel,
    playlistActions: PlaylistActions?,
    canLeaveGroup: Boolean,
    onMoveToPlayer: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    request ?: return
    val player = players.byId(request.playerId)?.takeIf { request.hasAnchor(it) }
        ?: return DismissEffect(request, onDismiss)

    when (request) {
        is PlayerDialogRequest.Select -> SelectPlayerDialog(
            selectedPlayer = player,
            players = players,
            onDismissRequest = onDismiss,
            onMoveToPlayer = onMoveToPlayer,
            onReorder = { homeScreenViewModel.onPlayersSortChanged(it) },
        )

        is PlayerDialogRequest.Group -> GroupSettingsDialog(
            player = player,
            onDismissRequest = onDismiss,
            groupAction = { playerId, action ->
                homeScreenViewModel.playerAction(playerId, action)
            },
            localPlayerId = homeScreenViewModel.localPlayerId,
            onAdjustPlaybackDelay = { homeScreenViewModel.adjustSendspinStaticDelayMs(it) },
            canLeaveGroup = canLeaveGroup,
        )

        is PlayerDialogRequest.Dsp -> DspSettingsDialog(
            playerId = request.playerId,
            dspSettingsViewModel = dspSettingsViewModel,
            onDismissRequest = onDismiss,
        )

        is PlayerDialogRequest.SleepTimer -> SleepTimerDialog(
            expiresAtSec = player.player.sleepTimerExpiresAt,
            onSelect = { homeScreenViewModel.setSleepTimer(request.playerId, it) },
            onClear = { homeScreenViewModel.clearSleepTimer(request.playerId) },
            onDismissRequest = onDismiss,
        )

        is PlayerDialogRequest.Lyrics -> {
            val lyrics = (player.queueInfo?.currentItem?.track as? Track)?.lyrics
                ?: return DismissEffect(request, onDismiss)
            val queueId = player.queueId
            val livePositionFlow = remember(queueId) {
                queueId?.let { homeScreenViewModel.observePosition(it) }
            }
            LyricsSheet(
                lyrics = lyrics,
                livePositionFlow = livePositionFlow,
                onDismiss = onDismiss,
            )
        }

        is PlayerDialogRequest.AudioChain -> player.queueInfo?.currentItem?.let { queueTrack ->
            AudioChainDialog(
                queueTrack = queueTrack,
                player = player,
                onDismissRequest = onDismiss,
            )
        }

        is PlayerDialogRequest.PlaybackSpeed -> player.queueInfo?.playbackSpeed?.let { speed ->
            PlaybackSpeedDialog(
                currentSpeed = speed,
                onConfirm = {
                    homeScreenViewModel.playerAction(player, PlayerAction.SetPlaybackSpeed(it))
                },
                onDismissRequest = onDismiss,
            )
        }

        is PlayerDialogRequest.AddToPlaylist -> playlistActions?.let { actions ->
            AddToPlaylistDialog(
                item = request.item,
                playlistActions = actions,
                onDismiss = onDismiss,
            )
        }
    }
}

/** Clears a request whose anchor is gone, once per request. */
@Composable
private fun DismissEffect(request: PlayerDialogRequest, onDismiss: () -> Unit) {
    LaunchedEffect(request) { onDismiss() }
}
