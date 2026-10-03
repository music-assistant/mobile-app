package io.music_assistant.client.ui.compose.home.players

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import io.music_assistant.client.data.MainDataSource
import io.music_assistant.client.data.model.server.ServerAiRadioHost
import io.music_assistant.client.data.model.server.ServerAiRadioSession
import io.music_assistant.client.data.repository.AiRadioRepository
import kotlinx.coroutines.async
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
    val running: ServerAiRadioSession? = null,
)

/** What the queue DJ row of one queue's menu shows. */
sealed interface QueueDjMenu {
    /** A show owns the queue, so its DJ cannot be changed. */
    data object OnAir : QueueDjMenu

    /** Hosts sorted by name; [selectedHostId] is null when the DJ is off. */
    data class Choices(val hosts: List<ServerAiRadioHost>, val selectedHostId: String?) : QueueDjMenu
}

fun QueueDjState.menuFor(queueId: String): QueueDjMenu =
    running?.takeIf { it.isRunning && it.queueId == queueId }
        ?.let { QueueDjMenu.OnAir }
        ?: QueueDjMenu.Choices(hosts.sortedBy { it.name.lowercase() }, assignments[queueId])

/**
 * Backs the AI Radio DJ row of the player queue menu.
 *
 * The provider emits no event for a DJ change or for a show starting, so the menu renders the
 * cache and calls [refresh] each time it opens: a change shows on the next open. Server errors
 * already reach the user through [io.music_assistant.client.api.ErrorMessageBus].
 */
class QueueDjViewModel(
    private val repository: AiRadioRepository,
    dataSource: MainDataSource,
) : ViewModel() {
    private val available = dataSource.aiRadioQueueDjAvailable
    private val cache = MutableStateFlow(QueueDjState())

    // Bumped by every set, so a refresh that started before it cannot restore the old map.
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
                if (isAvailable) refresh() else cache.value = QueueDjState()
            }
        }
    }

    fun refresh() {
        if (!available.value) return
        viewModelScope.launch {
            val version = assignmentsVersion
            val hosts = async { repository.hosts() }
            val assignments = async { repository.queueDjStatus() }
            val running = async { repository.runningSession() }
            cache.update { cached ->
                cached.copy(
                    hosts = hosts.await().getOrLogged("hosts") ?: cached.hosts,
                    assignments = assignments.await().getOrLogged("queue DJ status")
                        ?.takeIf { version == assignmentsVersion }
                        ?: cached.assignments,
                    // Null is a real answer here (nothing on air), so only a failure keeps the cache.
                    running = running.await()
                        .onFailure { Logger.w(it) { "AI Radio: status refresh failed" } }
                        .getOrElse { cached.running },
                )
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

    private fun <T> Result<T>.getOrLogged(what: String): T? =
        onFailure { Logger.w(it) { "AI Radio: $what refresh failed" } }.getOrNull()
}
