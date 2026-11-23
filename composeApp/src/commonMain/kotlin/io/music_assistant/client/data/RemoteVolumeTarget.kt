package io.music_assistant.client.data

import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.ui.compose.common.action.PlayerAction
import kotlinx.coroutines.flow.StateFlow

/** What the hardware volume keys need from [MainDataSource] to step the selected remote player. */
interface RemoteVolumeTarget {
    val selectedPlayer: PlayerData?
    val isReadyForCommands: StateFlow<Boolean>

    /**
     * Sends [action] to a player that is not the local player, and returns after the server
     * answers: true if the server took it, false if the request failed or could not be built.
     */
    suspend fun sendRemotePlayerAction(data: PlayerData, action: PlayerAction): Boolean
}
