package io.music_assistant.client.data.play

import io.music_assistant.client.data.model.client.QueueOption

/**
 * The waiting list of one queue after [request] arrives, while [inProgress] is on the server.
 *
 * - A request that starts playback replaces a waiting request that also starts playback:
 *   only the newest tap matters, and the server cannot cancel the running one anyway.
 * - `ADD` and `NEXT` are never replaced and never reordered.
 * - A request equal to the one that would run just before it is a double tap and is dropped.
 */
internal fun coalesce(
    waiting: List<PlayRequest>,
    inProgress: PlayRequest?,
    request: PlayRequest,
): List<PlayRequest> {
    val base = waiting.takeUnless {
        request.option.startsPlayback && it.lastOrNull()?.option?.startsPlayback == true
    } ?: waiting.dropLast(1)
    val predecessor = base.lastOrNull() ?: inProgress
    return if (request == predecessor) base else base + request
}

private val QueueOption.startsPlayback: Boolean
    get() = when (this) {
        QueueOption.PLAY, QueueOption.REPLACE -> true
        QueueOption.NEXT, QueueOption.ADD -> false
    }
