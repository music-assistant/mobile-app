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
 * Each waveform loads once, in the app [scope], because the pager composes the pages beside the
 * current one and a page comes back after a swipe. The last [CACHE_SIZE] answers stay.
 * [NoData][DataState.NoData] means that the server has no analysis of the track, or no waveform
 * command. A failed load stays [Error][DataState.Error] until the next [load].
 */
class WaveformRepository(
    private val apiClient: ServiceClient,
    private val scope: CoroutineScope,
) {
    private val log = Logger.withTag("WaveformRepository")

    private val states = MutableStateFlow<Map<WaveformKey, DataState<List<Float>>>>(emptyMap())

    /** The waveform of [key]; [NoData][DataState.NoData] until [load] is called. */
    fun waveform(key: WaveformKey): StateFlow<DataState<List<Float>>> =
        states.mapState { it[key] ?: DataState.NoData() }

    /** Loads the waveform of [key] once; a loaded, loading or absent waveform is not asked again. */
    fun load(key: WaveformKey) {
        if (!supportsWaveform(schemaVersion())) return
        var started = false
        states.update { current ->
            val state = current[key]
            started = state == null || state is DataState.Error
            if (started) current.appended(key, DataState.Loading(), CACHE_SIZE) else current
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
            states.update { it + (key to state) }
        }
    }

    private fun schemaVersion(): Int? =
        (apiClient.sessionState.value as? HasConnectionData)?.serverInfo?.schemaVersion

    private companion object {
        const val CACHE_SIZE = 8
    }
}
