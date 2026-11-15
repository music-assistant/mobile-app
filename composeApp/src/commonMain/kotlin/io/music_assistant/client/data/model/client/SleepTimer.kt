package io.music_assistant.client.data.model.client

import io.music_assistant.client.data.model.server.SleepTimerMode

/** The item or chapter end that the sleep timer waits for, null for a duration timer or no timer. */
val Player.sleepTimerEndMode: SleepTimerMode?
    get() = sleepTimerMode?.takeIf { it != SleepTimerMode.DURATION }

/**
 * The end-of-content modes to offer: only while the player plays an item with a known
 * duration. The server rejects the request without such an item. The chapter mode needs chapters.
 */
fun PlayerData.sleepTimerEndModes(): List<SleepTimerMode> {
    val item = queueInfo
        ?.takeUnless { it.ended }
        ?.takeIf { player.isPlaying }
        ?.currentItem
        ?.track
        ?.takeIf { (it.duration ?: 0.0) > 0.0 }
        ?: return emptyList()
    return listOfNotNull(
        SleepTimerMode.END_OF_ITEM,
        SleepTimerMode.END_OF_CHAPTER.takeIf { item.navigationChapters() != null },
    )
}
