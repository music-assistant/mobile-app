package io.music_assistant.client.input

import co.touchlab.kermit.Logger
import io.music_assistant.client.data.RemoteVolumeTarget
import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.settings.SettingsRepository
import io.music_assistant.client.ui.compose.common.action.PlayerAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class VolumeButtonService(
    private val target: RemoteVolumeTarget,
    private val settings: SettingsRepository,
    private val scope: CoroutineScope,
) {
    private val log = Logger.withTag("VolumeButtonService")

    private val _buttonPresses = MutableSharedFlow<UnroutedVolumeKey>(extraBufferCapacity = 1)

    /** Each press that changed the device volume, with the reason it did not go to the player. */
    val buttonPresses = _buttonPresses.asSharedFlow()

    private val _remoteAdjust = MutableSharedFlow<String>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** The player id of each volume key press that went to the selected remote player. */
    val remoteAdjust = _remoteAdjust.asSharedFlow()

    private val _refusals = MutableSharedFlow<VolumeKeyRefusal>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** Each press that the selected remote player cannot act on, with the reason. */
    val refusals = _refusals.asSharedFlow()

    private val _pendingVolumes = MutableStateFlow<Map<String, PendingVolume>>(emptyMap())

    /**
     * The level that the steps sent in the last [PENDING_VOLUME_MS] take each remote player to,
     * by player id. The player reports its new level only after the server reached it, so the
     * app shows this level until then; it uses the server's default steps ([nextVolume]).
     */
    val pendingVolumes: StateFlow<Map<String, PendingVolume>> = _pendingVolumes.asStateFlow()

    // Only the send loop touches these, one step at a time.
    private val pendingExpiries = mutableMapOf<String, Job>()
    private var sentSteps = 0L

    // Conflated: one step is in flight and only the latest press waits, so a held key steps once
    // per server round trip and a reversal replaces the waiting step. The server applies its own
    // volume step, like the taps beside the slider.
    private val steps = Channel<Step>(Channel.CONFLATED)

    private class Step(val player: PlayerData, val up: Boolean)

    init {
        scope.launch {
            for (step in steps) {
                val action = volumeStep(step.player, step.up)
                // The shown level follows the steps that leave, not the presses: a press that a
                // later one replaced in the channel never reaches the server.
                val shown = showPendingVolume(step.player, step.up)
                // A failed step must not end the loop, or every later press is taken and lost.
                val taken = runCatching { target.sendRemotePlayerAction(step.player, action) }
                    .onFailure { log.e(it) { "Volume step $action for ${step.player.player.name} failed" } }
                    .getOrDefault(false)
                when {
                    shown == null -> Unit
                    // The server took it: hold the level from its answer, so a slow round trip
                    // (Remote Access) can't let it expire while the step is still in flight.
                    taken -> expirePendingVolume(step.player.playerId, shown)
                    // The server never got it: show the reported level again at once.
                    else -> dropPendingVolume(step.player.playerId)
                }
            }
        }
    }

    /** A press on a platform that never gives the keys to a remote player (iOS). */
    fun onPlatformVolumeButtonPressed() {
        _buttonPresses.tryEmit(UnroutedVolumeKey.NOT_SUPPORTED)
    }

    /**
     * Gives a volume key press to the selected remote player while the user has opted in.
     * Returns true when the app takes the press, so the platform must not change the device
     * volume. With the setting on and a remote player selected the app always takes it: a
     * press that the player cannot act on emits a [VolumeKeyRefusal] instead of a step, once
     * for each press and not for its repeats.
     *
     * Returns false during a call or VoIP ([inCall]: the key changes the call volume), while the
     * setting is off, and for the local player or no selection: the device volume changes as
     * usual. The first two emit the reason on [buttonPresses], once for each press.
     */
    fun onVolumeKey(up: Boolean, isRepeat: Boolean, inCall: Boolean = false): Boolean {
        val selected = target.selectedPlayer
        val player = selected?.takeIf { !it.isLocal }
        val unrouted = when {
            inCall -> UnroutedVolumeKey.IN_CALL
            !settings.volumeKeysControlSelectedPlayer.value -> UnroutedVolumeKey.SETTING_OFF
            player == null -> null
            else -> return route(player, up, isRepeat)
        }
        if (!isRepeat) {
            val message = "Volume key left to the device: ${unrouted ?: "no remote player"}, " +
                "selected=${selected?.playerId} local=${selected?.isLocal}"
            // An ordinary press for this device is no news: info only when a remote player is selected.
            if (player != null) log.i { message } else log.d { message }
            // No toast without a remote player: the user isn't looking at one, or the UI is a moment ahead.
            unrouted?.let { _buttonPresses.tryEmit(it) }
        }
        return false
    }

    private fun route(player: PlayerData, up: Boolean, isRepeat: Boolean): Boolean {
        when (val reason = refusalFor(player)) {
            null -> {
                steps.trySend(Step(player, up))
                _remoteAdjust.tryEmit(player.playerId)
            }

            else -> if (!isRepeat) _refusals.tryEmit(VolumeKeyRefusal(reason, player.player.name))
        }
        return true
    }

    /** Shows the level [up] steps to, and returns its step number; null without a known level. */
    private fun showPendingVolume(player: PlayerData, up: Boolean): Long? {
        val id = player.playerId
        val base = _pendingVolumes.value[id]?.level ?: player.player.currentVolume ?: return null
        val step = ++sentSteps
        _pendingVolumes.update { it + (id to PendingVolume(nextVolume(base, up), step)) }
        // No expiry while the step is in flight.
        pendingExpiries.remove(id)?.cancel()
        return step
    }

    private fun expirePendingVolume(id: String, step: Long) {
        pendingExpiries.remove(id)?.cancel()
        pendingExpiries[id] = scope.launch {
            delay(PENDING_VOLUME_MS)
            // cancel() can't stop a job that is already past its delay, so remove only this
            // step's level, never the one a newer step just wrote.
            _pendingVolumes.update { if (it[id]?.step == step) it - id else it }
        }
    }

    private fun dropPendingVolume(id: String) {
        pendingExpiries.remove(id)?.cancel()
        _pendingVolumes.update { it - id }
    }

    private fun refusalFor(player: PlayerData): VolumeKeyRefusal.Reason? = when {
        !target.isReadyForCommands.value -> VolumeKeyRefusal.Reason.NOT_CONNECTED
        player.player.isPoweredOff -> VolumeKeyRefusal.Reason.POWERED_OFF
        !player.player.isVolumeSliderAccessible -> VolumeKeyRefusal.Reason.NO_VOLUME_CONTROL
        else -> null
    }
}

/** Why a volume key press changed the device volume and not the selected player. */
enum class UnroutedVolumeKey {
    /** The platform does not route the keys (iOS). */
    NOT_SUPPORTED,

    /** The setting that gives the keys to the selected player is off. */
    SETTING_OFF,

    /** A call or VoIP holds the audio mode. */
    IN_CALL,
}

/** A level that a sent volume step goes to; [step] tells the steps apart. */
data class PendingVolume(val level: Float, val step: Long)

/**
 * How long a pressed level shows after the last press. The server steps from the level it
 * last commanded for 2 s, so after that the player's own level is the one to show.
 */
internal const val PENDING_VOLUME_MS = 2_000L

/**
 * The level one volume step goes to from [current], with the server's default steps: 1 below
 * 10 and above 90, 2 below 30 and above 70, 3 in between. A player configured with its own
 * step shows that step once the server confirms it.
 */
internal fun nextVolume(current: Float, up: Boolean): Float {
    val level = current.roundToInt()
    val step = when {
        level < FINE_BELOW || level > 100 - FINE_BELOW -> 1
        level < MEDIUM_BELOW || level > 100 - MEDIUM_BELOW -> 2
        else -> COARSE_STEP
    }
    return (if (up) level + step else level - step).coerceIn(0, 100).toFloat()
}

private const val FINE_BELOW = 10
private const val MEDIUM_BELOW = 30
private const val COARSE_STEP = 3

/** Why a volume key press for the selected remote player changed nothing. */
data class VolumeKeyRefusal(val reason: Reason, val playerName: String) {
    enum class Reason { NOT_CONNECTED, POWERED_OFF, NO_VOLUME_CONTROL }
}

/** The same command the taps beside the slider send. */
internal fun volumeStep(player: PlayerData, up: Boolean): PlayerAction = when {
    player.usesGroupVolume && up -> PlayerAction.GroupVolumeUp
    player.usesGroupVolume -> PlayerAction.GroupVolumeDown
    up -> PlayerAction.VolumeUp
    else -> PlayerAction.VolumeDown
}
