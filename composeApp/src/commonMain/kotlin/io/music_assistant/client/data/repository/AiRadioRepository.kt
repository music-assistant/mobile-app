package io.music_assistant.client.data.repository

import io.music_assistant.client.api.Request
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.data.model.server.ServerAiRadioSession
import io.music_assistant.client.data.model.server.ServerAiRadioStation
import io.music_assistant.client.data.model.server.ServerAiRadioStatus

/**
 * Read/run access to the optional `ai_radio` plugin provider.
 *
 * Listing and running only: stations, sections and hosts are authored in the web frontend,
 * whose prompt editor this deliberately does not reproduce.
 *
 * Every call fails on a server without the plugin, and starting or stopping additionally
 * needs a scope that only `admin` holds. Gate the call sites on
 * [io.music_assistant.client.data.MainDataSource.aiRadioAvailable] rather than calling blind.
 */
class AiRadioRepository(
    private val apiClient: ServiceClient,
) {
    /** All configured stations, sorted by name server-side. Empty until one is authored. */
    suspend fun stations(): Result<List<ServerAiRadioStation>> =
        apiClient.sendRequest(Request.AiRadio.stations()).mapCatching { answer ->
            answer.resultAs<List<ServerAiRadioStation>>()
                ?: error("Missing or undecodable AI Radio station list payload")
        }

    /**
     * Starts [stationId] on [playerId], which must be a player id rather than a queue id.
     *
     * Fails when the user lacks the write scope, when a run is already active for this
     * station, or when the server's single concurrent-run slot is taken. All three arrive as
     * server-worded messages worth showing to the user.
     */
    suspend fun start(stationId: String, playerId: String): Result<ServerAiRadioSession> =
        apiClient.sendRequest(Request.AiRadio.start(stationId, playerId)).mapCatching { answer ->
            answer.resultAs<ServerAiRadioSession>()
                ?: error("Missing or undecodable AI Radio session payload")
        }

    suspend fun stop(sessionId: String): Result<ServerAiRadioSession> =
        apiClient.sendRequest(Request.AiRadio.stop(sessionId)).mapCatching { answer ->
            answer.resultAs<ServerAiRadioSession>()
                ?: error("Missing or undecodable AI Radio session payload")
        }

    /**
     * The currently running session, if any. The provider emits no events, so this is the
     * only way to learn a run's state and it has to be re-read after each start or stop.
     */
    suspend fun runningSession(): Result<ServerAiRadioSession?> =
        apiClient.sendRequest(Request.AiRadio.status()).mapCatching { answer ->
            val status = answer.resultAs<ServerAiRadioStatus>()
                ?: error("Missing or undecodable AI Radio status payload")
            status.sessions.firstOrNull { it.isRunning }
        }
}
