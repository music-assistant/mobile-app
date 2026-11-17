package io.music_assistant.client.data.repository

import co.touchlab.kermit.Logger
import io.music_assistant.client.data.model.client.Lyrics
import io.music_assistant.client.data.model.client.items.Track
import io.music_assistant.client.ui.compose.common.DataState
import io.music_assistant.client.utils.mapState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Lyrics for Now Playing, shared by the button and the lyrics panel.
 * Requests survive pager changes in the owning ViewModel's [scope]. Each answer belongs only to
 * its provider/track pair, so a late answer cannot replace another track's lyrics. Loading entries
 * deduplicate requests; [CACHE_SIZE] recently requested settled answers (including absent lyrics) stay cached.
 */
internal class TrackLyricsRepository(
    private val mediaItemRepository: MediaItemRepository,
    private val scope: CoroutineScope,
) {
    private data class Entry(val state: DataState<Lyrics>, val access: Long)

    private val states = MutableStateFlow<Map<Pair<String, String>, Entry>>(emptyMap())

    fun lyrics(track: Track): StateFlow<DataState<Lyrics>> = states.mapState {
        track.lyrics?.let { lyrics -> DataState.Data(lyrics) } ?: it[track.key]?.state ?: DataState.NoData()
    }

    /** Prefer embedded lyrics; retry a failed request only when [load] is called again. */
    fun load(track: Track) {
        if (track.lyrics != null) return
        val key = track.key
        var started = false
        states.update { current ->
            val entry = current[key]
            started = entry == null || entry.state is DataState.Error
            val access = (current.values.maxOfOrNull { it.access } ?: 0) + 1
            // Returning to a cached track keeps it ahead of older requests that are still in flight.
            current + (key to Entry(if (started) DataState.Loading() else entry!!.state, access))
        }
        if (!started) return
        scope.launch {
            val state: DataState<Lyrics> = try {
                mediaItemRepository.fetchTrackLyrics(track).getOrThrow()
                    ?.let { DataState.Data(it) } ?: DataState.NoData()
            } catch (cancelled: CancellationException) {
                states.update { it - key }
                throw cancelled
            } catch (error: Exception) {
                Logger.w(error) { "Failed to load track lyrics" }
                DataState.Error()
            }
            states.update { current ->
                val entry = current[key] ?: return@update current
                val updated = current + (key to entry.copy(state = state))
                val settled = updated.entries.filter { it.value.state !is DataState.Loading }.sortedBy { it.value.access }
                val overflow = (settled.size - CACHE_SIZE).coerceAtLeast(0)
                updated - settled.take(overflow).map { it.key }.toSet()
            }
        }
    }

    private val Track.key: Pair<String, String> get() = provider to itemId

    private companion object {
        const val CACHE_SIZE = 8
    }
}
