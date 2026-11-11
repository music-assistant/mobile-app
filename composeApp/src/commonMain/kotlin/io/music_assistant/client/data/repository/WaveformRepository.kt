package io.music_assistant.client.data.repository

import co.touchlab.kermit.Logger
import io.music_assistant.client.api.Request
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.data.model.client.WaveformKey
import io.music_assistant.client.data.model.server.supportsWaveform
import io.music_assistant.client.ui.compose.common.DataState
import io.music_assistant.client.utils.HasConnectionData
import io.music_assistant.client.utils.mapState
import io.music_assistant.client.utils.resultAs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The seek bar waveforms of tracks (`audio_analysis/wave_form`): 1800 RMS bins of 0.0 to 1.0
 * that cover the whole track.
 *
 * Each queue holds one waveform: the one of its current track. A load for a different track of
 * the queue replaces it, so the waveform of a playing track never goes, and an answer that comes
 * after the queue moved on is dropped. The load runs in the app [scope], because the pager
 * composes the pages beside the current one and a page comes back after a swipe.
 * [NoData][DataState.NoData] means that the server has no analysis of the track, or no waveform
 * command. A failed load stays [Error][DataState.Error] until the next [load].
 */
class WaveformRepository(
    private val apiClient: ServiceClient,
    private val scope: CoroutineScope,
) {
    private val log = Logger.withTag("WaveformRepository")

    private data class Slot(val key: WaveformKey, val state: DataState<List<Float>>)

    private val slots = MutableStateFlow<Map<String, Slot>>(emptyMap())

    /** The waveform of [key] on [queueId]; [NoData][DataState.NoData] until [load] is called. */
    fun waveform(queueId: String, key: WaveformKey): StateFlow<DataState<List<Float>>> =
        slots.mapState { it[queueId]?.takeIf { slot -> slot.key == key }?.state ?: DataState.NoData() }

    /** Loads the waveform of [key], the current track of [queueId]; a held waveform is not asked again. */
    fun load(queueId: String, key: WaveformKey) {
        if (!supportsWaveform(schemaVersion())) return
        var started = false
        slots.update { current ->
            val slot = current[queueId]
            started = slot?.key != key || slot.state is DataState.Error
            if (started) current + (queueId to Slot(key, DataState.Loading())) else current
        }
        if (!started) return
        scope.launch {
            val request = Request.AudioAnalysis.waveForm(key.itemId, key.provider)
            val state = apiClient.sendRequest(request).fold(
                onSuccess = { answer ->
                    answer.resultAs<List<Float>>()?.takeIf { it.isNotEmpty() }
                        ?.let { DataState.Data(it) } ?: DataState.NoData()
                },
                onFailure = {
                    log.w(it) { "Failed to load the waveform of $key" }
                    DataState.Error()
                },
            )
            slots.update { current ->
                current.takeIf { it[queueId]?.key == key }?.plus(queueId to Slot(key, state)) ?: current
            }
        }
    }

    private fun schemaVersion(): Int? =
        (apiClient.sessionState.value as? HasConnectionData)?.serverInfo?.schemaVersion
}
