package io.music_assistant.client.services

/**
 * A custom action the media session can publish. Keep this Android-free so the
 * priority order below stays unit-testable.
 */
internal enum class SessionAction {
    SWITCH_PLAYER,
    FAVORITE,
    SHUFFLE,
    REPEAT,
    SEEK_BACK,
    SEEK_FORWARD,
}

/**
 * Render-order priority for the queue-toggle actions. Not a truncation budget -- every action
 * [MediaNotificationData.supports] is included in the published list, in this order. A host
 * that can't fit them all is responsible for its own overflow: Android Auto pushes extras that
 * don't fit the transport row into its native "..." overflow menu rather than dropping them
 * (https://developer.android.com/training/cars/media/enable-playback), and the phone
 * notification's compact view is chosen by the OS from whatever custom actions the session
 * advertises.
 *
 * A previous version of this function capped the published list at 2 "slots" and silently
 * discarded whichever actions didn't fit that budget -- which meant shuffle never reached
 * Android Auto at all (not overflowed, just never sent) whenever a favoritable track was
 * playing with more than one player configured, since favorite ranked ahead of it for that
 * leftover slot. Advertising everything applicable and trusting each host's own overflow
 * handling is the platform-documented pattern, and fixes that class of bug entirely instead of
 * re-litigating which single action gets to survive a fixed budget.
 */
private val QUEUE_ACTION_PRIORITY =
    listOf(SessionAction.SHUFFLE, SessionAction.FAVORITE, SessionAction.REPEAT)

/** Picks the custom actions for [data], in render order (most useful first). */
internal fun sessionActions(data: MediaNotificationData): List<SessionAction> = buildList {
    if (data.multiplePlayers) {
        add(SessionAction.SWITCH_PLAYER)
    }
    if (data.isLongFormContent) {
        // Audiobooks and podcasts: seek controls instead of the queue toggles.
        add(SessionAction.SEEK_BACK)
        add(SessionAction.SEEK_FORWARD)
    } else {
        addAll(QUEUE_ACTION_PRIORITY.filter { data.supports(it) })
    }
}

private fun MediaNotificationData.supports(action: SessionAction) = when (action) {
    SessionAction.FAVORITE -> isFavoritableTrack
    SessionAction.SHUFFLE -> shuffleEnabled != null
    SessionAction.REPEAT -> repeatMode != null
    SessionAction.SWITCH_PLAYER -> multiplePlayers
    SessionAction.SEEK_BACK, SessionAction.SEEK_FORWARD -> isLongFormContent
}
