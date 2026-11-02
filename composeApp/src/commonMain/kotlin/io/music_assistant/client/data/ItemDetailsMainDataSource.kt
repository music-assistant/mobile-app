package io.music_assistant.client.data

import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.data.model.server.GrantedScopes
import io.music_assistant.client.data.model.server.RefreshItemAccess
import kotlinx.coroutines.flow.StateFlow

interface ItemDetailsMainDataSource {
    val refreshItemAccess: StateFlow<RefreshItemAccess>
    val playlistAccessSupported: StateFlow<Boolean>
    val grantedScopes: StateFlow<GrantedScopes>
    val selectedPlayer: PlayerData?
}
