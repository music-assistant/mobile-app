package io.music_assistant.client.data.repository

import io.music_assistant.client.api.Answer
import io.music_assistant.client.api.Request
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.data.model.client.ImageType
import io.music_assistant.client.data.model.server.AI_RADIO_DOMAIN
import io.music_assistant.client.data.model.server.ServerAiRadioHost
import io.music_assistant.client.data.model.server.ServerAiRadioQueueDj
import io.music_assistant.client.data.model.server.ServerAiRadioStation
import io.music_assistant.client.data.model.server.events.ProviderEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonPrimitive

/**
 * Access to the optional `ai_radio` plugin provider.
 *
 * Listing and the queue DJ only: stations, sections and hosts are authored in the web frontend,
 * whose prompt editor this deliberately does not reproduce. A station plays as a Radio item
 * ([ServerAiRadioStation.uri]) through [io.music_assistant.client.data.play.PlayDispatcher], so
 * there is no run to start or stop here.
 *
 * [changes] says what to re-read: the plugin announces its changes as refetch hints.
 *
 * Every call fails on a server without the plugin. Gate the call sites on
 * [io.music_assistant.client.data.MainDataSource.aiRadioAvailable], or on
 * [io.music_assistant.client.data.MainDataSource.aiRadioQueueDjAvailable] for the queue DJ calls,
 * rather than calling blind.
 */
class AiRadioRepository(
    private val apiClient: ServiceClient,
    private val mediaItemRepository: MediaItemRepository,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    /**
     * Hot stream of what to refetch. Merges the plugin's events with [AiRadioChange.RESYNC] on
     * each return of the command session (the first value is the current state, not a return).
     * Replay is zero: a late subscriber loads its own state first and then only sees new changes.
     */
    val changes: SharedFlow<AiRadioChange> = merge(
        apiClient.events
            .filterIsInstance<ProviderEvent>()
            // `object_id` is the instance id; the plugin is single-instance, so it equals the domain.
            .filter { it.objectId == AI_RADIO_DOMAIN }
            .mapNotNull { event ->
                AiRadioChange.fromEventName((event.data?.get("event") as? JsonPrimitive)?.content)
            },
        apiClient.isReadyForCommands
            .drop(1)
            .filter { it }
            .map { AiRadioChange.RESYNC },
    ).shareIn(scope, SharingStarted.WhileSubscribed(replayExpirationMillis = 0), replay = 0)

    /** All configured stations, sorted by name server-side. Empty until one is authored. */
    suspend fun stations(): Result<List<ServerAiRadioStation>> =
        apiClient.sendRequest(Request.AiRadio.stations()).mapCatching { answer ->
            answer.resultAs<List<ServerAiRadioStation>>()
                ?: error("Missing or undecodable AI Radio station list payload")
        }

    /** All hosts, for the queue DJ menu. Sorted by name server-side. */
    suspend fun hosts(): Result<List<ServerAiRadioHost>> =
        apiClient.sendRequest(Request.AiRadio.hosts()).mapCatching { answer ->
            answer.resultAs<List<ServerAiRadioHost>>()
                ?: error("Missing or undecodable AI Radio host list payload")
        }

    /** The `queue_id -> host_id` map of every queue that has a DJ. Re-read on [AiRadioChange.QUEUE_DJ]. */
    suspend fun queueDjStatus(): Result<Map<String, String>> =
        apiClient.sendRequest(Request.AiRadio.queueDjStatus()).mapCatching { it.decodeQueueDj() }

    /** Sets or, with a null [hostId], removes the DJ of [queueId]. Answers the new status map. */
    suspend fun setQueueDj(queueId: String, hostId: String?): Result<Map<String, String>> =
        apiClient.sendRequest(Request.AiRadio.setQueueDj(queueId, hostId))
            .mapCatching { it.decodeQueueDj() }

    // The show binding of each entry is dropped: the menu shows the host only.
    private fun Answer.decodeQueueDj(): Map<String, String> =
        resultAs<Map<String, ServerAiRadioQueueDj>>()?.mapValues { it.value.hostId }
            ?: error("Missing or undecodable AI Radio queue DJ payload")

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
