package io.music_assistant.client.ui.compose.home.players

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import io.music_assistant.client.data.MainDataSource
import io.music_assistant.client.data.model.server.ServerAiRadioHost
import io.music_assistant.client.data.repository.AiRadioChange
import io.music_assistant.client.data.repository.AiRadioRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Cached server state behind the queue DJ menu. Kept raw, so a part that failed to refresh
 * keeps its last value and [menuFor] still resolves against the rest.
 */
data class QueueDjState(
    val hosts: List<ServerAiRadioHost> = emptyList(),
    val assignments: Map<String, String> = emptyMap(),
)

/**
 * What the queue DJ row of one queue's menu shows: hosts sorted by name, and [selectedHostId]
 * null when the DJ is off. A show that plays on the queue does not lock the DJ: the server
 * keeps the show binding through a host switch.
 */
data class QueueDjMenu(val hosts: List<ServerAiRadioHost>, val selectedHostId: String?)

fun QueueDjState.menuFor(queueId: String): QueueDjMenu =
    QueueDjMenu(hosts.sortedBy { it.name.lowercase() }, assignments[queueId])

/**
 * Backs the AI Radio DJ row of the player queue menu.
 *
 * Event-driven: the gate opens only on servers that announce AI Radio changes, so the menu renders
 * the cache and each [AiRadioChange] re-reads just the part it names. Server errors already reach
 * the user through [io.music_assistant.client.api.ErrorMessageBus].
 */
class QueueDjViewModel(
    private val repository: AiRadioRepository,
    /** [MainDataSource.aiRadioQueueDjAvailable]. */
    private val available: StateFlow<Boolean>,
) : ViewModel() {
    private val cache = MutableStateFlow(QueueDjState())

    // One in-flight refetch per part: a burst of hints keeps only the latest.
    private val refetches = mutableMapOf<AiRadioChange, Job>()

    // Bumped by every set, so a refetch that started before it cannot restore the old map.
    private var assignmentsVersion = 0

    /** Null while the gate is closed, which hides the row. */
    val state: StateFlow<QueueDjState?> = combine(available, cache) { isAvailable, cached ->
        cached.takeIf { isAvailable }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        viewModelScope.launch {
            // The cache belongs to one server and role: drop it when the gate closes, and
            // prefetch when it opens so the first menu does not show an empty host list.
            available.collect { isAvailable ->
                if (isAvailable) {
                    refetchAll()
                } else {
                    refetches.values.forEach { it.cancel() }
                    cache.value = QueueDjState()
                }
            }
        }
        viewModelScope.launch {
            repository.changes.collect { change ->
                if (!available.value) return@collect
                when (change) {
                    AiRadioChange.HOSTS, AiRadioChange.QUEUE_DJ -> refetch(change)
                    AiRadioChange.RESYNC -> refetchAll()
                    AiRadioChange.STATIONS -> Unit
                }
            }
        }
    }

    /** Sets [hostId] as the DJ of [queueId], or turns the DJ off when [hostId] is null. */
    fun select(queueId: String, hostId: String?) {
        viewModelScope.launch {
            repository.setQueueDj(queueId, hostId)
                .onSuccess { assignments ->
                    assignmentsVersion++
                    cache.update { it.copy(assignments = assignments) }
                }
                .onFailure { Logger.w(it) { "AI Radio: queue DJ change failed for $queueId" } }
        }
    }

    private fun refetchAll() {
        refetch(AiRadioChange.HOSTS)
        refetch(AiRadioChange.QUEUE_DJ)
    }

    /** A failed refetch keeps the cached part, so [menuFor] still resolves against the rest. */
    private fun refetch(part: AiRadioChange) {
        refetches[part]?.cancel()
        refetches[part] = viewModelScope.launch {
            when (part) {
                AiRadioChange.HOSTS -> repository.hosts().getOrLogged("hosts")
                    ?.let { hosts -> cache.update { it.copy(hosts = hosts) } }
                AiRadioChange.QUEUE_DJ -> {
                    val version = assignmentsVersion
                    repository.queueDjStatus().getOrLogged("queue DJ status")
                        ?.takeIf { version == assignmentsVersion }
                        ?.let { assignments -> cache.update { it.copy(assignments = assignments) } }
                }
                AiRadioChange.STATIONS, AiRadioChange.RESYNC -> Unit
            }
        }
    }

    private fun <T> Result<T>.getOrLogged(what: String): T? =
        onFailure { Logger.w(it) { "AI Radio: $what refresh failed" } }.getOrNull()
}
