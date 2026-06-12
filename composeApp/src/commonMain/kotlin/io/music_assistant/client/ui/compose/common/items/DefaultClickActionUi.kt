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
import io.music_assistant.client.data.model.client.items.Track
import io.music_assistant.client.settings.DefaultClickOption
import io.music_assistant.client.ui.compose.settings.DefaultClickActionsViewModel
import org.koin.compose.viewmodel.koinViewModel

/**
 * Screen-scoped click config: the current context (null = non-customizable screen) plus
 * the full (kind -> context -> action) preference table. Provided once per screen; read by
 * playable item leaves to resolve what a single tap does for their specific item type.
 */
data class ClickActionConfig(
    val context: ClickContext?,
    val prefs: Map<ItemKind, Map<ClickContext, DefaultClickOption>>,
) {
    fun actionFor(item: AppMediaItem): DefaultClickOption {
        val ctx = context ?: return DefaultClickOption.PLAY_NOW
        val kind = item.itemKind() ?: return DefaultClickOption.PLAY_NOW
        return prefs[kind]?.get(ctx) ?: DefaultClickOption.PLAY_NOW
    }

    /** The concrete action a tap performs for [item], or null when it isn't playable. */
    fun effectiveActionFor(item: AppMediaItem): ItemAction? = actionFor(item).effectiveFor(item)
}

val LocalClickActionConfig = compositionLocalOf { ClickActionConfig(null, emptyMap()) }

/** The saved preference table, loaded once from Koin near the app root (empty in tests/previews). */
val LocalClickActionPrefs =
    compositionLocalOf<Map<ItemKind, Map<ClickContext, DefaultClickOption>>> { emptyMap() }

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
fun DefaultClickOption.toItemAction(): ItemAction = when (this) {
    DefaultClickOption.PLAY_NOW -> ItemAction.Play(QueueOption.REPLACE)
    DefaultClickOption.INSERT_NEXT_AND_PLAY -> ItemAction.Play(QueueOption.PLAY)
    DefaultClickOption.INSERT_NEXT -> ItemAction.Play(QueueOption.NEXT)
    DefaultClickOption.ADD_TO_QUEUE -> ItemAction.Play(QueueOption.ADD)
    DefaultClickOption.START_RADIO -> ItemAction.StartRadio
    DefaultClickOption.PLAY_FROM_HERE -> ItemAction.PlayFromHere
}

/**
 * The action a click actually performs for a concrete item, or null when the item
 * isn't playable (click should open the menu instead). START_RADIO falls back to
 * Play Now when the item can't start a radio.
 */
fun DefaultClickOption.effectiveFor(item: AppMediaItem): ItemAction? {
    if (!item.isPlayable) return null
    return when (this) {
        DefaultClickOption.START_RADIO ->
            if (item.canStartRadio) ItemAction.StartRadio else ItemAction.Play(QueueOption.REPLACE)
        DefaultClickOption.PLAY_FROM_HERE ->
            if (item is Track) ItemAction.PlayFromHere else ItemAction.Play(QueueOption.REPLACE)
        else -> toItemAction()
    }
}
