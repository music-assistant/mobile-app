package io.music_assistant.client.data.repository

import co.touchlab.kermit.Logger
import io.music_assistant.client.api.Request
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.utils.resultAs

class ShareRepository(private val apiClient: ServiceClient) {
    private val log = Logger.withTag("ShareRepository")

    val sessionState = apiClient.sessionState
    val isReady = apiClient.isReadyForCommands

    suspend fun generateLink(uri: String): String? =
        apiClient.sendRequest(Request.Share.generate(uri))
            .onFailure { log.w { "Share Links plugin failed to generate a link" } }
            .resultAs<String>()?.takeIf { it.isNotBlank() }
}
