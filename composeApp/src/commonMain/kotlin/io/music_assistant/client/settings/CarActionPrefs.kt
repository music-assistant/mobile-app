package io.music_assistant.client.settings

import io.music_assistant.client.data.model.client.ItemKind
import io.music_assistant.client.data.model.client.QueueOption

/**
 * The car surface a setting applies to. AA and CarPlay share one preference store
 * (see [SettingsRepository.carPlayableClickActions] / [SettingsRepository.carBrowsableBulkActions]);
 * the platform only gates which actions it can actually honor — see [isCarSupported].
 */
enum class CarPlatform { ANDROID_AUTO, CARPLAY }

/** Playable kinds that get a per-kind car tap action. */
val carPlayableKinds: List<ItemKind> = listOf(
    ItemKind.TRACK,
    ItemKind.RADIO,
    ItemKind.PODCAST_EPISODE,
    ItemKind.AUDIOBOOK,
)

/** Browsable container kinds that drill down in the car and show a configurable bulk-action list. */
val carBrowsableKinds: List<ItemKind> = listOf(
    ItemKind.ARTIST,
    ItemKind.ALBUM,
    ItemKind.PLAYLIST,
    ItemKind.PODCAST,
)

/** Today's hard-coded pair — the default bulk actions for a browsable kind with no stored config. */
val defaultCarBulkActions: List<DefaultClickAction> = listOf(
    DefaultClickAction.PLAY_NOW,
    DefaultClickAction.ADD_TO_QUEUE,
)

/**
 * Whether this action can be offered/honored on [platform] for [kind]. Intersects the kind
 * applicability ([DefaultClickAction.appliesTo]) with per-platform capability. Both the Settings
 * UI (offered options) and the car-side dispatch consult this, so an unsupported stored action is
 * silently skipped rather than mis-dispatched. The platform arms are identical today; the seam
 * exists so a future CarPlay/AA limitation lands in one place.
 */
fun DefaultClickAction.isCarSupported(platform: CarPlatform, kind: ItemKind): Boolean {
    if (!appliesTo(kind)) return false
    return when (platform) {
        CarPlatform.ANDROID_AUTO -> true
        CarPlatform.CARPLAY -> true
    }
}

/** The action a plain tap performs for [kind] in the car, falling back to today's PLAY_NOW. */
fun Map<ItemKind, DefaultClickAction>.carTapAction(kind: ItemKind): DefaultClickAction =
    this[kind] ?: DefaultClickAction.PLAY_NOW

/**
 * The ordered, platform-supported bulk buttons shown for browsable [kind] in [platform], defaulting
 * to [defaultCarBulkActions] when unconfigured. Always filtered through [isCarSupported].
 */
fun Map<ItemKind, List<DefaultClickAction>>.carBulkActions(
    kind: ItemKind,
    platform: CarPlatform,
): List<DefaultClickAction> =
    (this[kind] ?: defaultCarBulkActions).filter { it.isCarSupported(platform, kind) }

/** How a [DefaultClickAction] is dispatched to a player: queue option + radio-mode flag. */
data class CarDispatch(val option: QueueOption, val radioMode: Boolean)

/** The single source for turning a chosen action into a play_media dispatch (AA and CarPlay share it). */
fun DefaultClickAction.toCarDispatch(): CarDispatch = when (this) {
    DefaultClickAction.PLAY_NOW -> CarDispatch(QueueOption.REPLACE, radioMode = false)
    DefaultClickAction.INSERT_NEXT_AND_PLAY -> CarDispatch(QueueOption.PLAY, radioMode = false)
    DefaultClickAction.INSERT_NEXT -> CarDispatch(QueueOption.NEXT, radioMode = false)
    DefaultClickAction.ADD_TO_QUEUE -> CarDispatch(QueueOption.ADD, radioMode = false)
    DefaultClickAction.START_RADIO -> CarDispatch(QueueOption.REPLACE, radioMode = true)
}

/** The car surface the current platform exposes. */
expect fun currentCarPlatform(): CarPlatform
