package io.music_assistant.client.data

import co.touchlab.kermit.Logger
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.settings.CarDspAction
import io.music_assistant.client.settings.SettingsRepository
import io.music_assistant.client.settings.matches
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Applies the user's chosen DSP action to the local player when the phone (dis)connects from the
 * car. Listens on the reliable, platform-provided [CarConnectionMonitor], so one observer covers
 * both Android Auto and CarPlay. Eagerly created (Koin `createdAtStart`).
 *
 * A missing preset (deleted server-side) is a deliberate no-op here — the settings dialog is the
 * cleanup + error-reporting point, so the stored reference is left intact for it to detect.
 */
class CarDspApplier(
    private val serviceClient: ServiceClient,
    carConnection: CarConnectionMonitor,
    private val dataSource: MainDataSource,
    private val settings: SettingsRepository,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val logger = Logger.withTag("CarDspApplier")

    init {
        scope.launch {
            // Skip the initial emission only when it's `false` (app started outside the car — the
            // disconnect action must not fire at launch). A cold start caused BY the car binding
            // replays an initial `true`, which we DO act on, else we'd miss the connect edge.
            var first = true
            carConnection.connected // StateFlow already conflates duplicate values
                .collect { active ->
                    if (first) {
                        first = false
                        if (!active) return@collect
                    }
                    val action = if (active) {
                        settings.carDspConnectAction.value
                    } else {
                        settings.carDspDisconnectAction.value
                    }
                    apply(active, action)
                }
        }
    }

    private suspend fun apply(active: Boolean, action: CarDspAction) {
        if (action is CarDspAction.Nothing) return
        val edge = if (active) "connect" else "disconnect"
        // The car edge triggers an (auto-)connect; the DSP save needs a live, authenticated
        // session. Wait for readiness (bounded) rather than firing into a half-open socket.
        val ready = withTimeoutOrNull(READY_TIMEOUT_MS) {
            serviceClient.isReadyForCommands.first { it }
        }
        if (ready == null) {
            logger.i { "$edge: not ready within ${READY_TIMEOUT_MS}ms, skipping DSP apply" }
            return
        }
        val playerId = settings.sendspinClientId.value
        when (action) {
            is CarDspAction.Disable -> {
                val config = dataSource.getDspConfig(playerId) ?: return
                if (!config.enabled) return
                dataSource.saveDspConfig(playerId, config.copy(enabled = false))
                logger.i { "$edge: disabled DSP for local player" }
            }

            is CarDspAction.Preset -> {
                val preset = dataSource.getDspPresets().firstOrNull { it.matches(action) }
                if (preset == null) {
                    logger.i { "$edge: preset '${action.name}' not found on server, skipping" }
                    return
                }
                dataSource.saveDspConfig(playerId, preset.config.copy(enabled = true))
                logger.i { "$edge: applied DSP preset '${preset.name}'" }
            }

            CarDspAction.Nothing -> Unit
        }
    }

    private companion object {
        const val READY_TIMEOUT_MS = 15_000L
    }
}
