package io.music_assistant.client.data.repository

import co.touchlab.kermit.Logger
import io.music_assistant.client.api.Request
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.data.model.server.ServerProviderInstance
import io.music_assistant.client.data.model.server.ServerUserSummary
import io.music_assistant.client.data.model.server.events.ProvidersUpdatedEvent
import io.music_assistant.client.ui.compose.common.DataState
import io.music_assistant.client.utils.resultAs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn

/**
 * The single owner of the `providers` list: the provider instances that the signed-in user may
 * see. Read [providers] instead of sending the command.
 */
class ProviderRepository(
    private val apiClient: ServiceClient,
    scope: CoroutineScope,
) {
    private val log = Logger.withTag("ProviderRepository")

    /**
     * [DataState.NoData] while no session is ready, then [DataState.Loading] until the first answer
     * of the session. Loads again on every `providers_updated`, and the newest event wins. A failed
     * first fetch gives [DataState.Error]. A failed later fetch keeps the last list.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val providers: StateFlow<DataState<List<ServerProviderInstance>>> = apiClient.isReadyForCommands
        .flatMapLatest { ready -> if (ready) sessionProviders() else flowOf(DataState.NoData()) }
        .stateIn(scope, SharingStarted.Eagerly, DataState.NoData())

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun sessionProviders(): Flow<DataState<List<ServerProviderInstance>>> = apiClient.events
        .filterIsInstance<ProvidersUpdatedEvent>()
        .map { }
        .onStart { emit(Unit) }
        .mapLatest { fetch() }
        .scan<List<ServerProviderInstance>?, DataState<List<ServerProviderInstance>>>(
            DataState.Loading(),
        ) { state, fetched ->
            fetched?.let { DataState.Data(it) }
                ?: state.takeIf { it is DataState.Data }
                ?: DataState.Error()
        }

    /**
     * The users a playlist can be shared with, the caller included. Server 2.11, for a user with
     * `library.write` or `config.providers.own`. Null when the server refuses or fails.
     */
    suspend fun shareCandidates(): List<ServerUserSummary>? =
        apiClient.sendRequest(Request.Provider.shareCandidates())
            .resultAs<List<ServerUserSummary>>()

    private suspend fun fetch(): List<ServerProviderInstance>? =
        apiClient.sendRequest(Request.Provider.all())
            .resultAs<List<ServerProviderInstance>>()
            ?: run {
                log.w { "Failed to fetch the provider list" }
                null
            }
}
