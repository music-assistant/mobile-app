package io.music_assistant.client.data

import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.data.model.server.GrantedScopes
import io.music_assistant.client.data.model.server.RefreshItemAccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeItemDetailsMainDataSource : ItemDetailsMainDataSource {
    override val refreshItemAccess: StateFlow<RefreshItemAccess> =
        MutableStateFlow(RefreshItemAccess.NONE)

    override val playlistAccessSupported: StateFlow<Boolean> =
        MutableStateFlow(false)

    override val grantedScopes: StateFlow<GrantedScopes> =
        MutableStateFlow(GrantedScopes.NONE)

    override val selectedPlayer: PlayerData? = null
}
