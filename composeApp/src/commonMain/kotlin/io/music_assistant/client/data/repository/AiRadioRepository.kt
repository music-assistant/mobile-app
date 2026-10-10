package io.music_assistant.client.data.repository

import co.touchlab.kermit.Logger
import io.music_assistant.client.api.Answer
import io.music_assistant.client.api.Request
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.data.model.client.ImageType
import io.music_assistant.client.data.model.server.ServerAiRadioHost
import io.music_assistant.client.data.model.server.ServerAiRadioSession
import io.music_assistant.client.data.model.server.ServerAiRadioStation
import io.music_assistant.client.data.model.server.ServerAiRadioStatus
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Read/run access to the optional `ai_radio` plugin provider.
 *
 * Listing, running, and the queue DJ only: stations, sections and hosts are authored in the
 * web frontend, whose prompt editor this deliberately does not reproduce.
 *
 * Every call fails on a server without the plugin, and starting or stopping additionally
 * needs a scope that only `admin` holds. Gate the call sites on
 * [io.music_assistant.client.data.MainDataSource.aiRadioAvailable], or on
 * [io.music_assistant.client.data.MainDataSource.aiRadioQueueDjAvailable] for the queue DJ calls,
 * rather than calling blind.
 */
class AiRadioRepository(
    private val apiClient: ServiceClient,
    private val mediaItemRepository: MediaItemRepository,
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
     * server-worded messages worth showing to the user — which is why there is no client-side
     * guard against starting a station that is already on air.
     */
    suspend fun start(stationId: String, playerId: String): Result<ServerAiRadioSession> =
        apiClient.sendRequest(Request.AiRadio.start(stationId, playerId)).mapCatching { answer ->
            answer.resultAs<ServerAiRadioSession>()
                ?: error("Missing or undecodable AI Radio session payload")
        }

    /**
     * Stops whatever run [stationId] has on air, and clears the queue it was playing on.
     *
     * The clear is ours to do: the plugin's own stop only calls `player_queues.stop`, so the
     * show's tracks and its half-rendered host clips stay queued. Pressing play afterwards then
     * resumes a show whose session no longer exists, and a clip that never got rendered has
     * nothing left to render it.
     *
     * The queue comes from the stopped session itself, never from the current selection — the
     * user may have switched players since the run began, and clearing the wrong queue would
     * throw away something they are listening to.
     *
     * A failed clear does not fail the stop: the run did end, and reporting otherwise would
     * invite a retry that stops nothing.
     */
    suspend fun stop(stationId: String): Result<ServerAiRadioSession> =
        apiClient.sendRequest(Request.AiRadio.stop(stationId)).mapCatching { answer ->
            answer.resultAs<ServerAiRadioSession>()
                ?: error("Missing or undecodable AI Radio session payload")
        }.onSuccess { session ->
            // The answer describes the run the server actually stopped, so its queue is the
            // right one to clear even though we never named a session.
            val queueId = session.queueId ?: run {
                Logger.d { "AI Radio: station $stationId owned no queue, nothing to clear" }
                return@onSuccess
            }
            apiClient.sendRequest(Request.Queue.clear(queueId))
                .onFailure { Logger.w(it) { "AI Radio: could not clear queue $queueId" } }
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

    /** All hosts, for the queue DJ menu. Sorted by name server-side. */
    suspend fun hosts(): Result<List<ServerAiRadioHost>> =
        apiClient.sendRequest(Request.AiRadio.hosts()).mapCatching { answer ->
            answer.resultAs<List<ServerAiRadioHost>>()
                ?: error("Missing or undecodable AI Radio host list payload")
        }

    /**
     * The `queue_id -> host_id` map of every queue that has a DJ. The provider emits no event
     * when it changes, so this has to be re-read whenever the menu that shows it opens.
     */
    suspend fun queueDjStatus(): Result<Map<String, String>> =
        apiClient.sendRequest(Request.AiRadio.queueDjStatus()).mapCatching { it.decodeQueueDj() }

    /** Sets or, with a null [hostId], removes the DJ of [queueId]. Answers the new status map. */
    suspend fun setQueueDj(queueId: String, hostId: String?): Result<Map<String, String>> =
        apiClient.sendRequest(Request.AiRadio.setQueueDj(queueId, hostId))
            .mapCatching { it.decodeQueueDj() }

    private fun Answer.decodeQueueDj(): Map<String, String> =
        resultAs<Map<String, String>>() ?: error("Missing or undecodable AI Radio queue DJ payload")

    /**
     * Artwork URLs for [stations], keyed by station id.
     *
     * A station carries no image, so this borrows the cover of the playlist it plays from. The
     * lookups run concurrently and each is bounded on its own: a station whose playlist is gone,
     * has no artwork, or sits behind a slow streaming provider is simply absent from the map and
     * the caller draws its placeholder. The bound matters because the Android Auto browse tree
     * awaits this inline and one wedged provider must not stall the whole tab.
     */
    suspend fun artworkUrls(stations: List<ServerAiRadioStation>): Map<String, String> =
        coroutineScope {
            stations
                .filter { it.sourcePlaylistId.isNotBlank() }
                .map { station ->
                    async {
                        withTimeoutOrNull(ARTWORK_TIMEOUT_MS) {
                            mediaItemRepository.fetchMediaItem(
                                Request.Playlist.get(
                                    itemId = station.sourcePlaylistId,
                                    providerInstanceIdOrDomain = station.sourcePlaylistProvider,
                                ),
                            ).getOrNull()?.image(ImageType.THUMB)?.url
                        }?.let { station.id to it }
                    }
                }
                .awaitAll()
                .filterNotNull()
                .toMap()
        }

    private companion object {
        const val ARTWORK_TIMEOUT_MS = 5000L
    }
}
