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

// Phone card keeps favorite ahead of shuffle so its 2-icon compact view is unchanged;
// Android Auto is always single-player (see SharedMediaSessionManager.sourcePlayerData)
// and never consumes this order.
private val QUEUE_ACTION_PRIORITY_SINGLE_PLAYER =
    listOf(SessionAction.SHUFFLE, SessionAction.FAVORITE, SessionAction.REPEAT)
private val QUEUE_ACTION_PRIORITY_MULTI_PLAYER =
    listOf(SessionAction.FAVORITE, SessionAction.SHUFFLE, SessionAction.REPEAT)

/**
 * Custom actions for [data], in render order. Publishes every supported action:
 * Android Auto overflows the extras, the phone card shows only the first two.
 *
 * Switch-player always leads so a disappearing toggle never moves it under the
 * user's finger. Multi-player puts favorite before shuffle so the phone keeps
 * Switch + Favorite; Android Auto is always single-player (see
 * SharedMediaSessionManager.sourcePlayerData) and never sees that order.
 */
internal fun sessionActions(data: MediaNotificationData): List<SessionAction> = buildList {
    if (data.multiplePlayers) {
        add(SessionAction.SWITCH_PLAYER)
    }
    if (data.isLongFormContent) {
        // Audiobooks and podcasts: seek controls instead of the queue toggles.
        add(SessionAction.SEEK_BACK)
        add(SessionAction.SEEK_FORWARD)
    } else {
        val priority = if (data.multiplePlayers) {
            QUEUE_ACTION_PRIORITY_MULTI_PLAYER
        } else {
            QUEUE_ACTION_PRIORITY_SINGLE_PLAYER
        }
        addAll(priority.filter { data.supports(it) })
    }
}

internal fun MediaNotificationData.supports(action: SessionAction) = when (action) {
    SessionAction.FAVORITE -> isFavoritableTrack || isFavoritableStream
    SessionAction.SHUFFLE -> shuffleEnabled != null
    SessionAction.REPEAT -> repeatMode != null
    SessionAction.SWITCH_PLAYER -> multiplePlayers
    SessionAction.SEEK_BACK, SessionAction.SEEK_FORWARD -> isLongFormContent
}
