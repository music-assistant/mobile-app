package io.music_assistant.client.data.repository

import co.touchlab.kermit.Logger
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.data.model.client.Lyrics
import io.music_assistant.client.data.model.client.items.PodcastEpisode
import io.music_assistant.client.data.model.client.items.Track
import io.music_assistant.client.data.model.server.supportsEpisodeTranscript
import io.music_assistant.client.ui.compose.common.DataState
import io.music_assistant.client.utils.HasConnectionData
import io.music_assistant.client.utils.mapState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Shared track lyrics and episode transcripts for Item Details and Now Playing.
 * Loads run in the application [scope], surviving screen changes. Answers belong to a media type,
 * provider and item ID, so late responses never replace another item's content. Loading entries
 * deduplicate requests; up to [CACHE_SIZE] settled answers stay in insertion order, including absence.
 * Errors retry on the next load. Episodes retain their server and provider capability gates.
 */
class LyricsRepository(
    private val mediaItemRepository: MediaItemRepository,
    private val apiClient: ServiceClient,
    private val scope: CoroutineScope,
) {
    private val log = Logger.withTag("LyricsRepository")
    private data class Key(val type: String, val provider: String, val itemId: String)

    private val states = MutableStateFlow<Map<Key, DataState<Lyrics>>>(emptyMap())

    fun lyrics(track: Track): StateFlow<DataState<Lyrics>> = states.mapState {
        track.lyrics?.let { lyrics -> DataState.Data(lyrics) } ?: it[track.key] ?: DataState.NoData()
    }

    fun transcript(episode: PodcastEpisode): StateFlow<DataState<Lyrics>> =
        states.mapState { it[episode.key] ?: DataState.NoData() }

    fun load(track: Track) {
        if (track.lyrics != null) return
        load(track.key) { mediaItemRepository.fetchTrackLyrics(track) }
    }

    fun load(episode: PodcastEpisode) {
        val schema = (apiClient.sessionState.value as? HasConnectionData)?.serverInfo?.schemaVersion
        if (!supportsEpisodeTranscript(schema) || episode.metadata?.hasTranscript == false) return
        load(episode.key) { mediaItemRepository.fetchEpisodeTranscript(episode) }
    }

    private fun load(key: Key, fetch: suspend () -> Result<Lyrics?>) {
        var started = false
        states.update { current ->
            val state = current[key]
            started = state == null || state is DataState.Error
            if (started) current.appended(key, DataState.Loading()) else current
        }
        if (!started) return
        scope.launch {
            try {
                val state = fetch().fold(
                    onSuccess = { it?.let { lyrics -> DataState.Data(lyrics) } ?: DataState.NoData() },
                    onFailure = {
                        if (it is CancellationException) throw it
                        log.w(it) { "Failed to load lyrics or transcript for $key" }
                        DataState.Error()
                    },
                )
                currentCoroutineContext().ensureActive()
                states.update { it.appended(key, state) }
            } catch (cancelled: CancellationException) {
                states.update { it - key }
                throw cancelled
            }
        }
    }

    /** Keep insertion order, evicting only settled entries; in-flight requests remain deduplicated. */
    private fun Map<Key, DataState<Lyrics>>.appended(key: Key, state: DataState<Lyrics>) =
        ((this - key) + (key to state)).let { updated ->
            val settled = updated.keys.filter { updated[it] !is DataState.Loading }
            val overflow = (settled.size - CACHE_SIZE).coerceAtLeast(0)
            updated - settled.take(overflow).toSet()
        }

    private val Track.key: Key get() = Key("track", provider, itemId)
    private val PodcastEpisode.key: Key get() = Key("episode", provider, itemId)

    private companion object {
        const val CACHE_SIZE = 8
    }
}
