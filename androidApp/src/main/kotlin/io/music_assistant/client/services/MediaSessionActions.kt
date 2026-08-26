package io.music_assistant.client.services

/**
 * A custom action the media session can publish. Keep this Android-free so the
 * slot rule below stays unit-testable.
 */
internal enum class SessionAction {
    SWITCH_PLAYER,
    FAVORITE,
    SHUFFLE,
    REPEAT,
    SEEK_BACK,
    SEEK_FORWARD,
}

/** Notification and Android Auto render at most this many custom actions. */
private const val SLOT_COUNT = 2

/** Switch-player holds the leading slot, so the favorite toggle wins the last one. */
private val ANCHORED_PRIORITY =
    listOf(SessionAction.FAVORITE, SessionAction.SHUFFLE, SessionAction.REPEAT)

/** Both slots are free: shuffle keeps the lead it has always had. */
private val FREE_PRIORITY =
    listOf(SessionAction.SHUFFLE, SessionAction.FAVORITE, SessionAction.REPEAT)

/**
 * Picks the custom actions for [data], in render order.
 *
 * Only [SLOT_COUNT] slots exist and the list has no gaps, so a control that
 * disappears pulls everything on its right one slot left. A dynamic playlist
 * drops shuffle and repeat, which used to move the switch-player button under
 * the user's finger. Anchoring switch-player in the leading slot keeps all
 * variability in the tail slot, where a control can appear or disappear
 * without moving its neighbour.
 */
internal fun sessionActions(data: MediaNotificationData): List<SessionAction> = buildList {
    if (data.multiplePlayers) {
        add(SessionAction.SWITCH_PLAYER)
    }
    if (data.isLongFormContent) {
        // Audiobooks and podcasts: seek controls instead of the queue toggles.
        add(SessionAction.SEEK_BACK)
        if (!data.multiplePlayers) {
            add(SessionAction.SEEK_FORWARD)
        }
    } else {
        val priority = if (data.multiplePlayers) ANCHORED_PRIORITY else FREE_PRIORITY
        addAll(priority.filter { data.supports(it) })
    }
}.take(SLOT_COUNT)

private fun MediaNotificationData.supports(action: SessionAction) = when (action) {
    SessionAction.FAVORITE -> isFavoritableTrack
    SessionAction.SHUFFLE -> shuffleEnabled != null
    SessionAction.REPEAT -> repeatMode != null
    SessionAction.SWITCH_PLAYER -> multiplePlayers
    SessionAction.SEEK_BACK, SessionAction.SEEK_FORWARD -> isLongFormContent
}
