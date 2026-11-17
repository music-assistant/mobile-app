package io.music_assistant.client.data.repository

import co.touchlab.kermit.Logger
import io.music_assistant.client.api.Request
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.data.model.client.PlayerDelay
import io.music_assistant.client.data.model.server.GrantedScopes
import io.music_assistant.client.data.model.server.ServerPlayerConfig
import io.music_assistant.client.settings.SettingsRepository
import io.music_assistant.client.utils.resultAs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The static playback delay of the Sendspin players. The app's own player keeps it in
 * [SettingsRepository.sendspinStaticDelayMs]: it advertises no commands, so the server offers it
 * no entry. The other Sendspin players keep it on the server, in the player config entry
 * `sendspin_static_delay`. The server offers that entry only for a player that accepts the output
 * delay command, so a Sendspin device that does not is simply absent from [delays]. A device with
 * output protocols carries the entry under the prefixed key of its Sendspin protocol player
 * ([ServerPlayerConfig.outputEntry]), and the save goes to the parent under that same key.
 *
 * A save applies at once and restarts nothing, so each tap saves. The saves of one burst go out
 * one after the other, and a save whose value a later tap already replaced is skipped. The
 * optimistic value stays until the server contradicts it: a failed save loads the server value
 * again. The server sends no event the client models, so the map is only as fresh as the last
 * [load].
 *
 * Fails closed on the scopes: the save needs `config.players.write`, which implies the read.
 */
class SendspinDelayRepository(
    private val apiClient: ServiceClient,
    private val grantedScopes: StateFlow<GrantedScopes>,
    private val settings: SettingsRepository,
    private val scope: CoroutineScope,
) {
    private val log = Logger.withTag("SendspinDelay")

    /** The delay of one player, with the full key of its config entry. */
    private data class Loaded(val key: String, val delay: PlayerDelay)

    private val _entries = MutableStateFlow<Map<String, Loaded>>(emptyMap())

    /** The delay of each player that has one, by player id. The local player is always in it. */
    val delays: StateFlow<Map<String, PlayerDelay>> = combine(
        _entries,
        settings.sendspinStaticDelayMs,
        settings.sendspinEffectivePlayerId,
    ) { entries, localMs, localId ->
        entries.mapValues { it.value.delay } +
            (localId to PlayerDelay(localMs, SettingsRepository.SENDSPIN_STATIC_DELAY_RANGE))
    }.stateIn(scope, SharingStarted.Eagerly, emptyMap())

    private val localPlayerId: String get() = settings.sendspinEffectivePlayerId.value

    private val saves = Channel<Pair<String, Int>>(Channel.UNLIMITED)

    init {
        scope.launch {
            for ((playerId, target) in saves) {
                // A later tap replaced this value; its own save follows in the channel.
                val loaded = _entries.value[playerId]?.takeIf { it.delay.valueMs == target } ?: continue
                apiClient.sendRequest(Request.Player.saveConfigValue(playerId, loaded.key, target))
                    .onFailure {
                        log.e(it) { "Failed to save the delay of $playerId" }
                        fetch(playerId)
                    }
            }
        }
    }

    /**
     * Loads the delay of each of [playerIds] from the server. A player without one leaves the map.
     * The local player needs no load.
     */
    fun load(playerIds: Collection<String>) {
        if (!grantedScopes.value.canConfigurePlayers) return
        (playerIds - localPlayerId).forEach { playerId -> scope.launch { fetch(playerId) } }
    }

    /** Moves the delay of [playerId] by [deltaMs], within its range. Unknown players are ignored. */
    fun adjust(playerId: String, deltaMs: Int) {
        if (playerId == localPlayerId) {
            settings.setSendspinStaticDelayMs(settings.sendspinStaticDelayMs.value + deltaMs)
            return
        }
        val current = _entries.value[playerId] ?: return
        val target = (current.delay.valueMs + deltaMs).coerceIn(current.delay.range)
        if (target == current.delay.valueMs) return
        _entries.update { it + (playerId to current.copy(delay = current.delay.copy(valueMs = target))) }
        saves.trySend(playerId to target)
    }

    private suspend fun fetch(playerId: String) {
        val loaded = apiClient.sendRequest(Request.Player.config(playerId))
            .mapCatching { answer -> answer.resultAs<ServerPlayerConfig>()?.outputEntry(KEY) }
            .getOrElse {
                log.e(it) { "Failed to load the config of $playerId" }
                return
            }
            ?.let { (key, entry) ->
                val value = entry.intValue ?: return
                Loaded(key, PlayerDelay(valueMs = value, range = entry.intRange ?: DEFAULT_RANGE))
            }
        _entries.update { entries -> loaded?.let { entries + (playerId to it) } ?: (entries - playerId) }
    }

    companion object {
        const val KEY = "sendspin_static_delay"

        /** The range of the server entry, for a server that sends none. */
        val DEFAULT_RANGE = 0..5000
    }
}
