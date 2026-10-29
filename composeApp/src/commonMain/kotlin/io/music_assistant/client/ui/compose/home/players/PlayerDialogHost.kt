package io.music_assistant.client.ui.compose.home.players

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.data.model.client.byId
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.PodcastEpisode
import io.music_assistant.client.data.model.client.items.Track
import io.music_assistant.client.data.repository.QueueOverlayRepository
import io.music_assistant.client.ui.compose.common.action.PlayerAction
import io.music_assistant.client.ui.compose.common.items.AddToPlaylistDialog
import io.music_assistant.client.ui.compose.common.items.ChooseArtistDialog
import io.music_assistant.client.ui.compose.common.items.PlaylistActions
import io.music_assistant.client.ui.compose.home.HomeScreenViewModel
import io.music_assistant.client.ui.compose.home.players.announcement.AnnouncementDialog
import io.music_assistant.client.ui.compose.provider.ProviderViewModel
import org.koin.compose.koinInject

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
    providerViewModel: ProviderViewModel,
    playlistActions: PlaylistActions?,
    onNavigateToItem: (AppMediaItem) -> Unit,
    onDismiss: () -> Unit,
) {
    request ?: return
    val player = players.byId(request.playerId)?.takeIf { request.hasAnchor(it) }
        ?: return DismissEffect(request, onDismiss)

    when (request) {
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
            val item = player.queueInfo?.currentItem?.track
            val lyrics = when (item) {
                is Track -> item.lyrics
                is PodcastEpisode -> remember(item.provider, item.itemId) { homeScreenViewModel.transcript(item) }
                    .collectAsStateWithLifecycle().value.dataOrNull

                else -> null
            } ?: return DismissEffect(request, onDismiss)
            val queueId = player.queueId
            val livePositionFlow = remember(queueId) {
                queueId?.let { homeScreenViewModel.observePosition(it) }
            }
            LyricsSheet(
                lyrics = lyrics,
                livePositionFlow = livePositionFlow,
                onDismiss = onDismiss,
                kind = if (item is PodcastEpisode) LyricsSheetKind.TRANSCRIPT else LyricsSheetKind.LYRICS,
            )
        }

        is PlayerDialogRequest.ChooseArtist -> (player.queueInfo?.currentItem?.track as? Track)?.let {
            ChooseArtistDialog(
                artists = it.artists,
                onSelect = { artist ->
                    onDismiss()
                    onNavigateToItem(artist)
                },
                onDismiss = onDismiss,
            )
        }

        is PlayerDialogRequest.AudioChain -> player.queueInfo?.currentItem?.let { queueTrack ->
            AudioChainDialog(
                queueTrack = queueTrack,
                player = player,
                providerDetails = providerViewModel::getProviderDetails,
                onOpenOrigin = { origin ->
                    onDismiss()
                    onNavigateToItem(origin)
                },
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

        is PlayerDialogRequest.Announcement -> AnnouncementDialog(
            player = player,
            onDismissRequest = onDismiss,
        )

        is PlayerDialogRequest.AmbientSound -> player.queueInfo?.let { queueInfo ->
            queueInfo.overlay?.let { overlay ->
                val repository = koinInject<QueueOverlayRepository>()
                AmbientSoundDialog(
                    overlay = overlay,
                    playerName = player.player.nameAndSuffix,
                    loadSounds = repository::soundEffects,
                    onApply = { uri, volume -> repository.apply(queueInfo.id, uri, volume) },
                    onDismissRequest = onDismiss,
                )
            }
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
