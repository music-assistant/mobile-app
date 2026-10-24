package io.music_assistant.client.data

import co.touchlab.kermit.Logger
import io.music_assistant.client.api.Request
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.data.model.client.QueueOption
import io.music_assistant.client.data.play.PlayDispatcher
import io.music_assistant.client.data.play.PlayRequest

/**
 * Forces playback onto a specific player, mandatorily detaching it from
 * any sync group first. For surfaces where mirroring to other players
 * cannot be the user's intent: they're holding (or sitting in) the device
 * the audio has to come out of.
 */

data class LocalPlayerDispatchPlan(
    val playerId: String,
    val mediaUris: List<String>,
    val detachFrom: String?,
    val option: QueueOption,
    val endlessMixMode: Boolean = false,
    /** Server-side "start playback from this item id within [mediaUris]" (play-from-here). */
    val startItem: String? = null,
    /** `shuffle=true` on `play_media` (a car "Play all shuffled" bulk action). */
    val shuffle: Boolean = false,
)

/** Returns null when prerequisites (a local player, at least one URI) are missing. */
fun planLocalPlayerDispatch(
    localPlayerId: String?,
    localPlayerSyncedTo: String?,
    mediaUris: List<String>,
    option: QueueOption,
    endlessMixMode: Boolean = false,
    startItem: String? = null,
    shuffle: Boolean = false,
): LocalPlayerDispatchPlan? {
    if (localPlayerId == null || mediaUris.isEmpty()) return null
    return LocalPlayerDispatchPlan(
        playerId = localPlayerId,
        mediaUris = mediaUris,
        detachFrom = localPlayerSyncedTo,
        option = option,
        endlessMixMode = endlessMixMode,
        startItem = startItem,
        shuffle = shuffle,
    )
}

/**
 * Detach must land before play — MA otherwise promotes
 * `play(queueOrPlayerId=childId)` to the group queue — so the play request reaches
 * [playDispatcher] only after the detach RPC has answered. A detach failure goes through
 * [onRpcFailure] rather than try/catch: `sendRequest` is contractually no-throw, and a
 * catch-all would only break structured concurrency. [playDispatcher] logs play failures.
 */
suspend fun executeLocalPlayerDispatch(
    serviceClient: ServiceClient,
    playDispatcher: PlayDispatcher,
    plan: LocalPlayerDispatchPlan,
    onRpcFailure: (label: String, error: Throwable) -> Unit = { _, _ -> },
) {
    plan.detachFrom?.let { syncedToId ->
        serviceClient.sendRequest(
            Request.Player.setGroupMembers(
                playerId = syncedToId,
                playersToAdd = null,
                playersToRemove = listOf(plan.playerId),
            ),
        ).onFailure { onRpcFailure("detach", it) }
    }
    Logger.withTag("PlayDispatch").i {
        "LocalPlayerDispatch (CarPlay/Siri): uris=${plan.mediaUris} option=${plan.option} " +
            "startItem=${plan.startItem} shuffle=${plan.shuffle} player=${plan.playerId} " +
            "detachFrom=${plan.detachFrom}"
    }
    playDispatcher.play(
        PlayRequest(
            queueOrPlayerId = plan.playerId,
            media = plan.mediaUris,
            option = plan.option,
            endlessMixMode = plan.endlessMixMode,
            startItem = plan.startItem,
            shuffle = plan.shuffle,
        ),
    )
}
