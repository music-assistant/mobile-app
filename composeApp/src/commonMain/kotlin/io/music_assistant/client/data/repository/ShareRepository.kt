package io.music_assistant.client.data.repository

import io.music_assistant.client.api.Request
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.data.model.server.ServerShareItem
import io.music_assistant.client.utils.HasConnectionData
import io.music_assistant.client.utils.SessionState
import io.music_assistant.client.utils.resultAs

fun SessionState.supportsSharing(): Boolean =
    ((this as? HasConnectionData)?.serverInfo?.schemaVersion ?: 0) >= 100

class ShareRepository(private val apiClient: ServiceClient) {
    val sessionState = apiClient.sessionState

    suspend fun shareItem(uri: String): ServerShareItem? {
        if (!sessionState.value.supportsSharing()) return null
        return apiClient.sendRequest(Request.Library.shareItem(uri)).resultAs<ServerShareItem>()
    }
}
