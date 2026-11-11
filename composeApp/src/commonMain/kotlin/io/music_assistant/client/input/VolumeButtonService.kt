package io.music_assistant.client.input

import co.touchlab.kermit.Logger
import io.music_assistant.client.data.RemoteVolumeTarget
import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.settings.SettingsRepository
import io.music_assistant.client.ui.compose.common.action.PlayerAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class VolumeButtonService(
    private val target: RemoteVolumeTarget,
    private val settings: SettingsRepository,
    scope: CoroutineScope,
) {
    private val log = Logger.withTag("VolumeButtonService")

    private val _buttonPresses = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
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

    // Conflated: one step is in flight and only the latest press waits, so a held key steps once
    // per server round trip and a reversal replaces the waiting step. The server applies its own
    // volume step, like the taps beside the slider.
    private val steps = Channel<Pair<PlayerData, PlayerAction>>(Channel.CONFLATED)

    init {
        scope.launch {
            for ((player, action) in steps) {
                // A failed step must not end the loop, or every later press is taken and lost.
                runCatching { target.sendRemotePlayerAction(player, action) }
                    .onFailure { log.e(it) { "Volume step $action for ${player.player.name} failed" } }
            }
        }
    }

    fun onPlatformVolumeButtonPressed() {
        _buttonPresses.tryEmit(Unit)
    }

    /**
     * Gives a volume key press to the selected remote player while the user has opted in.
     * Returns true when the app takes the press, so the platform must not change the device
     * volume. With the setting on and a remote player selected the app always takes it: a
     * press that the player cannot act on emits a [VolumeKeyRefusal] instead of a step, once
     * for each press and not for its repeats. Returns false for the local player and while the
     * setting is off: the device volume changes as usual.
     */
    fun onVolumeKey(up: Boolean, isRepeat: Boolean): Boolean {
        val player = target.selectedPlayer
            ?.takeIf { settings.volumeKeysControlSelectedPlayer.value && !it.isLocal }
            ?: return false
        when (val reason = refusalFor(player)) {
            null -> {
                steps.trySend(player to volumeStep(player, up))
                _remoteAdjust.tryEmit(player.playerId)
            }

            else -> if (!isRepeat) _refusals.tryEmit(VolumeKeyRefusal(reason, player.player.name))
        }
        return true
    }

    private fun refusalFor(player: PlayerData): VolumeKeyRefusal.Reason? = when {
        !target.isReadyForCommands.value -> VolumeKeyRefusal.Reason.NOT_CONNECTED
        player.player.isPoweredOff -> VolumeKeyRefusal.Reason.POWERED_OFF
        !player.player.isVolumeSliderAccessible -> VolumeKeyRefusal.Reason.NO_VOLUME_CONTROL
        else -> null
    }
}

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
