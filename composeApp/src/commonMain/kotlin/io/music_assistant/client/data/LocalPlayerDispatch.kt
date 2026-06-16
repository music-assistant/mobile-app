package io.music_assistant.client.data

import io.music_assistant.client.api.Request
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.data.model.client.QueueOption

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
    val radioMode: Boolean = false,
    /** Server-side "start playback from this item id within [mediaUris]" (play-from-here). */
    val startItem: String? = null,
)

/** Returns null when prerequisites (a local player, at least one URI) are missing. */
fun planLocalPlayerDispatch(
    localPlayerId: String?,
    localPlayerSyncedTo: String?,
    mediaUris: List<String>,
    option: QueueOption,
    radioMode: Boolean = false,
    startItem: String? = null,
): LocalPlayerDispatchPlan? {
    if (localPlayerId == null || mediaUris.isEmpty()) return null
    return LocalPlayerDispatchPlan(
        playerId = localPlayerId,
        mediaUris = mediaUris,
        detachFrom = localPlayerSyncedTo,
        option = option,
        radioMode = radioMode,
        startItem = startItem,
    )
}

/**
 * Detach must land before play — MA otherwise promotes
 * `play(queueOrPlayerId=childId)` to the group queue. Failures go through
 * [onRpcFailure] rather than try/catch: `sendRequest` is contractually
 * no-throw, and a catch-all would only break structured concurrency.
 */
suspend fun executeLocalPlayerDispatch(
    serviceClient: ServiceClient,
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
    serviceClient.sendRequest(
        Request.Library.play(
            media = plan.mediaUris,
            queueOrPlayerId = plan.playerId,
            option = plan.option,
            radioMode = plan.radioMode,
            startItem = plan.startItem,
        ),
    ).onFailure { onRpcFailure("play(${plan.option})", it) }
}
