// Compose layout values (sizes, spacings) are visual design tokens.
@file:Suppress("MagicNumber")

package io.music_assistant.client.ui.compose.home.players

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.ui.alphaOn
import io.music_assistant.client.ui.compose.common.icons.CrossfadeIcon
import io.music_assistant.client.ui.contentColorByLuminance
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.cd_autoplay_off
import musicassistantclient.composeapp.generated.resources.cd_autoplay_on
import musicassistantclient.composeapp.generated.resources.cd_crossfade_off
import musicassistantclient.composeapp.generated.resources.cd_crossfade_on
import org.jetbrains.compose.resources.stringResource

/** Fixed so badges appearing and disappearing never reflow the player below. */
private val BADGE_ROW_HEIGHT = 28.dp

internal val BADGE_ICON_SIZE = 16.dp

/** Fully rounded: the pill reads as a chip regardless of how wide its content is. */
private val BADGE_PILL_SHAPE = CircleShape

/**
 * Alpha of the *inactive* pill. The badges sit on the artwork-tinted player gradient, so
 * the pill cannot use a fixed palette color; `scrim` is the M3 token for a darkening
 * overlay and is black in both themes, keeping the pill "darker than whatever is behind
 * it". The active pill inverts to the content tint instead, so it needs no alpha.
 */
private const val BADGE_PILL_ALPHA = 0.2f

/**
 * Centered row of player status badges: sleep timer, autoplay, then crossfade.
 *
 * Always emitted, never conditionally skipped — the row holds [BADGE_ROW_HEIGHT] even
 * with nothing to show, so a badge switching on does not shift the artwork below it.
 */
@Composable
fun PlayerBadgesRow(
    player: PlayerData,
    tint: Color,
    onSleepTimerClick: (() -> Unit)?,
    onToggleAutoplay: (current: Boolean) -> Unit,
    onToggleCrossfade: (current: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(BADGE_ROW_HEIGHT),
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Shown for the whole time the feature is supported, so the row does not shuffle
        // as a timer starts and stops; "no timer" is a state of the badge, not its absence.
        val sleepRemaining = rememberSleepTimerRemaining(player.player.sleepTimerExpiresAt)
        if (onSleepTimerClick != null) {
            SleepTimerBadge(
                remaining = sleepRemaining,
                tint = tint,
                onClick = onSleepTimerClick,
            )
        }

        val queueInfo = player.queueInfo
        // Null means the server has no autoplay support at all — nothing to show. Any other
        // state shows the badge, on or off, enabled or not.
        val autoplayEnabled = queueInfo?.autoPlayEnabled
        if (autoplayEnabled != null) {
            // A dynamic queue auto-fills from its source, so autoplay is forced on and the
            // command is a no-op. `currentItem` (not the lazily-loaded `items` list the
            // overflow menu gates on) is what tells an empty queue from a loaded one — the
            // list is NoData until the user opens the queue, which would leave the badge
            // stuck disabled.
            val isDynamic = queueInfo.isDynamicPlaylist
            val hasSomethingToPlay = queueInfo.currentItem != null
            val on = autoplayEnabled || isDynamic
            AutoplayBadge(
                tint = tint,
                on = on,
                onClick = { onToggleAutoplay(on) }.takeIf { !isDynamic && hasSomethingToPlay },
            )
        }

        // Null means the server predates the feature. Note there is deliberately no
        // `isDynamic` gate here: the server rejects shuffle and repeat on a dynamic queue
        // but accepts crossfade, so dimming it would forbid a call the server allows.
        val crossfadeEnabled = queueInfo?.crossfadeEnabled
        if (crossfadeEnabled != null) {
            CrossfadeBadge(
                tint = tint,
                on = crossfadeEnabled,
                onClick = {
                    onToggleCrossfade(crossfadeEnabled)
                }.takeIf { queueInfo.currentItem != null },
            )
        }
    }
}

@Composable
private fun CrossfadeBadge(tint: Color, on: Boolean, onClick: (() -> Unit)?) {
    BadgePill(
        modifier = Modifier.width(36.dp),
        contentDescription = stringResource(
            if (on) Res.string.cd_crossfade_on else Res.string.cd_crossfade_off,
        ),
        tint = tint,
        on = on,
        onClick = onClick,
    ) {
        // Colors come from the pill via LocalContentColor.
        Icon(
            imageVector = CrossfadeIcon,
            contentDescription = null,
            modifier = Modifier.size(BADGE_ICON_SIZE),
        )
    }
}

@Composable
private fun AutoplayBadge(tint: Color, on: Boolean, onClick: (() -> Unit)?) {
    BadgePill(
        modifier = Modifier.width(36.dp),
        contentDescription = stringResource(
            if (on) Res.string.cd_autoplay_on else Res.string.cd_autoplay_off,
        ),
        tint = tint,
        on = on,
        onClick = onClick,
    ) {
        // Colors come from the pill via LocalContentColor, so the badge cannot drift
        // out of sync with its own container.
        Icon(
            imageVector = Icons.Default.AllInclusive,
            contentDescription = null,
            modifier = Modifier.size(BADGE_ICON_SIZE),
        )
    }
}

/**
 * Shared container for every player badge: a rounded pill whose colors invert when [on].
 *
 * Active fills the pill with [tint] and flips the content to whichever of black/white stays
 * legible on it; inactive keeps the dark scrim pill with [tint] content. Inverting the whole
 * pill is what makes "on" unmistakable — an earlier version tried outlining the *off* pill
 * instead, but a tinted outline adds visual weight, so off read louder than on.
 *
 * A null [onClick] is "disabled": it dims the whole badge, pill included, AND attaches no
 * click action, so it is inert to touch and to accessibility rather than merely looking
 * unavailable. Alpha is a separate axis from the inversion, so the two never collide.
 *
 * Content colors are published through [LocalContentColor] rather than passed down, so a
 * badge's icon and text cannot drift out of sync with the container behind them.
 *
 * Merges its descendants' semantics under [contentDescription] so the badge is one node
 * carrying both the description and the click action — the icon inside stays undescribed.
 */
@Composable
internal fun BadgePill(
    contentDescription: String,
    tint: Color,
    on: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val container = if (on) {
        tint
    } else {
        MaterialTheme.colorScheme.scrim.copy(alpha = BADGE_PILL_ALPHA)
    }
    val contentColor = if (on) tint.contentColorByLuminance() else tint

    Row(
        modifier = modifier
            .alphaOn(onClick != null)
            // Clip first so the ripple is clipped to the pill too.
            .clip(BADGE_PILL_SHAPE)
            .background(container)
            .then(onClick?.let { Modifier.clickable(onClick = it) } ?: Modifier)
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .semantics(mergeDescendants = true) { this.contentDescription = contentDescription },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            content()
        }
    }
}
