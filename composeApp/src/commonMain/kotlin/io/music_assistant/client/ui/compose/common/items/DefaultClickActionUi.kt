package io.music_assistant.client.ui.compose.common.items

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.music_assistant.client.data.model.client.ClickContext
import io.music_assistant.client.data.model.client.ItemKind
import io.music_assistant.client.data.model.client.QueueOption
import io.music_assistant.client.data.model.client.itemKind
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.settings.DefaultClickAction
import io.music_assistant.client.ui.compose.settings.DefaultClickActionsViewModel
import org.koin.compose.viewmodel.koinViewModel

/**
 * Screen-scoped click config: the current context (null = non-customizable screen) plus
 * the full (kind -> context -> action) preference table. Provided once per screen; read by
 * playable item leaves to resolve what a single tap does for their specific item type.
 */
data class ClickActionConfig(
    val context: ClickContext?,
    val prefs: Map<ItemKind, Map<ClickContext, DefaultClickAction>>,
) {
    fun actionFor(item: AppMediaItem): DefaultClickAction {
        val ctx = context ?: return DefaultClickAction.PLAY_NOW
        val kind = item.itemKind() ?: return DefaultClickAction.PLAY_NOW
        return prefs[kind]?.get(ctx) ?: DefaultClickAction.PLAY_NOW
    }

    /** The concrete action a tap performs for [item], or null when it isn't playable. */
    fun effectiveActionFor(item: AppMediaItem): ItemAction? = actionFor(item).effectiveFor(item)
}

val LocalClickActionConfig = compositionLocalOf { ClickActionConfig(null, emptyMap()) }

/** The saved preference table, loaded once from Koin near the app root (empty in tests/previews). */
val LocalClickActionPrefs =
    compositionLocalOf<Map<ItemKind, Map<ClickContext, DefaultClickAction>>> { emptyMap() }

/**
 * Reads the saved tables once from Koin and exposes them via [LocalClickActionPrefs].
 * Provide near the app root so context scoping below stays Koin-free (and previewable/testable).
 */
@Composable
fun ProvideClickActionPrefs(content: @Composable () -> Unit) {
    val prefs by koinViewModel<DefaultClickActionsViewModel>().actions.collectAsStateWithLifecycle()
    CompositionLocalProvider(LocalClickActionPrefs provides prefs, content)
}

/**
 * Scopes the click config to [context] (the surface the wrapped subtree renders in; null =
 * non-customizable → PLAY_NOW) using the already-loaded prefs. Single, Koin-free entry point
 * for every customizable screen and the detail-page play button.
 */
@Composable
fun ProvideClickActions(context: ClickContext?, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalClickActionConfig provides ClickActionConfig(context, LocalClickActionPrefs.current),
        content,
    )
}

/** Matrix-row display mapping (no concrete item — reuses ItemAction.title()/icon()). */
fun DefaultClickAction.toItemAction(): ItemAction = when (this) {
    DefaultClickAction.PLAY_NOW -> ItemAction.Play(QueueOption.REPLACE)
    DefaultClickAction.INSERT_NEXT_AND_PLAY -> ItemAction.Play(QueueOption.PLAY)
    DefaultClickAction.INSERT_NEXT -> ItemAction.Play(QueueOption.NEXT)
    DefaultClickAction.ADD_TO_QUEUE -> ItemAction.Play(QueueOption.ADD)
    DefaultClickAction.START_RADIO -> ItemAction.StartRadio
}

/**
 * The action a click actually performs for a concrete item, or null when the item
 * isn't playable (click should open the menu instead). START_RADIO falls back to
 * Play Now when the item can't start a radio.
 */
fun DefaultClickAction.effectiveFor(item: AppMediaItem): ItemAction? {
    if (!item.isPlayable) return null
    return when (this) {
        DefaultClickAction.START_RADIO ->
            if (item.canStartRadio) ItemAction.StartRadio else ItemAction.Play(QueueOption.REPLACE)
        else -> toItemAction()
    }
}
