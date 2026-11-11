package io.music_assistant.client.data.repository

import co.touchlab.kermit.Logger
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.data.model.client.Lyrics
import io.music_assistant.client.data.model.client.items.PodcastEpisode
import io.music_assistant.client.data.model.server.supportsEpisodeTranscript
import io.music_assistant.client.ui.compose.common.DataState
import io.music_assistant.client.utils.HasConnectionData
import io.music_assistant.client.utils.mapState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The transcripts of podcast episodes (`music/podcasts/podcast_episode_transcript`, server 2.11).
 *
 * The episode page and the player ask for the same episode, so each transcript loads once, in the
 * app [scope], and the last [CACHE_SIZE] answers stay. [NoData][DataState.NoData] means that the
 * server has no transcript for the episode, that the provider reports none, or that the server has
 * no transcript command. A failed load stays [Error][DataState.Error] until the next [load].
 */
class TranscriptRepository(
    private val mediaItemRepository: MediaItemRepository,
    private val apiClient: ServiceClient,
    private val scope: CoroutineScope,
) {
    private val log = Logger.withTag("TranscriptRepository")

    private val states = MutableStateFlow<Map<String, DataState<Lyrics>>>(emptyMap())

    /** The transcript of [episode]; [NoData][DataState.NoData] until [load] is called. */
    fun transcript(episode: PodcastEpisode): StateFlow<DataState<Lyrics>> =
        states.mapState { it[episode.key] ?: DataState.NoData() }

    /** Loads the transcript of [episode] once; a loaded, loading or absent transcript is not asked again. */
    fun load(episode: PodcastEpisode) {
        if (!supportsEpisodeTranscript(schemaVersion()) || episode.metadata?.hasTranscript == false) return
        val key = episode.key
        var started = false
        states.update { current ->
            val state = current[key]
            started = state == null || state is DataState.Error
            if (started) current.appended(key, DataState.Loading()) else current
        }
        if (!started) return
        scope.launch {
            val state = mediaItemRepository.fetchEpisodeTranscript(episode).fold(
                onSuccess = { it?.let { lyrics -> DataState.Data(lyrics) } ?: DataState.NoData() },
                onFailure = {
                    log.w(it) { "Failed to load the transcript of $key" }
                    DataState.Error()
                },
            )
            states.update { it + (key to state) }
        }
    }

    /** [this] with [key] as the newest entry; the oldest settled entries go when the map is full. */
    private fun Map<String, DataState<Lyrics>>.appended(key: String, state: DataState<Lyrics>) =
        (this - key).let { rest ->
            val settled = rest.keys.filter { rest[it] !is DataState.Loading }
            val overflow = (rest.size + 1 - CACHE_SIZE).coerceAtLeast(0)
            rest - settled.take(overflow).toSet() + (key to state)
        }

    private fun schemaVersion(): Int? =
        (apiClient.sessionState.value as? HasConnectionData)?.serverInfo?.schemaVersion

    private val PodcastEpisode.key: String get() = "$provider/$itemId"

    private companion object {
        const val CACHE_SIZE = 8
    }
}
