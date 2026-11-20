package io.music_assistant.client.ui.compose.home.players.share

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.data.model.client.items.AudioSource
import io.music_assistant.client.data.share.PhoneAudioRouter
import io.music_assistant.client.data.share.PhoneAudioShareState
import org.koin.compose.koinInject

/** What the player surfaces show about the share of the phone audio. */
data class PhoneAudioShareUi(
    /** A share can start: the platform, the server and the local player all support it. */
    val available: Boolean = false,
    val sharing: Boolean = false,
    /** The local player plays, or is about to. A share restarts its audio link, so it must stop first. */
    val blockedByLocalPlayback: Boolean = false,
    /** The player that plays the phone audio, or that is about to. Null until the server reports it. */
    val target: PlayerData? = null,
    private val localPlayer: PlayerData? = null,
) {
    /** The phone itself, and a group that has it, would play its own input late. */
    fun canStartOn(player: PlayerData): Boolean =
        available && !sharing && !player.isLocal && !player.groupsWith(localPlayer)

    private fun PlayerData.groupsWith(local: PlayerData?): Boolean =
        local?.let { phone ->
            phone.parentBind?.id == playerId || childrenBinds.any { it.isBound && it.id == phone.playerId }
        } == true

    companion object {
        fun of(
            available: Boolean,
            state: PhoneAudioShareState,
            sourceUri: String?,
            players: List<PlayerData>,
        ): PhoneAudioShareUi {
            val local = players.firstOrNull { it.isLocal }
            return PhoneAudioShareUi(
                available = available,
                sharing = state != PhoneAudioShareState.Idle,
                blockedByLocalPlayback = local?.let { it.player.isPlaying || it.playRequestPending } == true,
                target = when (state) {
                    PhoneAudioShareState.Idle -> null
                    is PhoneAudioShareState.Starting -> players.firstOrNull { it.queueOrPlayerId == state.queueId }
                    // The server queues tell, so the mark follows a move from another client.
                    PhoneAudioShareState.Active -> players.firstOrNull { it.plays(sourceUri) }
                },
                localPlayer = local,
            )
        }

        /** The server names the input as the active source, or, on an older server, as the queue item. */
        private fun PlayerData.plays(sourceUri: String?): Boolean =
            sourceUri != null &&
                (player.liveInputUri == sourceUri || (queueInfo?.currentItem?.track as? AudioSource)?.uri == sourceUri)
    }
}

@Composable
fun rememberPhoneAudioShareUi(
    players: List<PlayerData>,
    router: PhoneAudioRouter = koinInject(),
): PhoneAudioShareUi {
    val available by router.isAvailable.collectAsStateWithLifecycle()
    val state by router.state.collectAsStateWithLifecycle()
    val sourceUri by router.sourceUri.collectAsStateWithLifecycle()
    return PhoneAudioShareUi.of(available, state, sourceUri, players)
}
