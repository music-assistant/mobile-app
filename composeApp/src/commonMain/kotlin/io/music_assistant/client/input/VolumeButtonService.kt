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

/** Why a volume key press changed the device volume and not the selected player. */
enum class UnroutedVolumeKey {
    /** The platform does not route the keys (iOS). */
    NOT_SUPPORTED,

    /** The setting that gives the keys to the selected player is off. */
    SETTING_OFF,

    /** A call or VoIP holds the audio mode. */
    IN_CALL,
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
