// Compose layout values (sizes, alphas, animation durations) are visual design tokens.
@file:Suppress("MagicNumber")

package io.music_assistant.client.ui.compose.home.players

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Forward30
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.data.model.client.PlayerDataFixtures
import io.music_assistant.client.data.model.client.RepeatMode
import io.music_assistant.client.data.model.client.items.AudioSource
import io.music_assistant.client.data.model.client.items.Audiobook
import io.music_assistant.client.data.model.client.items.LongFormSeekDefaults
import io.music_assistant.client.data.model.client.items.isLongFormSpokenContent
import io.music_assistant.client.data.model.client.navigationChapters
import io.music_assistant.client.ui.alphaOn
import io.music_assistant.client.ui.compose.common.TvFocusFlow
import io.music_assistant.client.ui.compose.common.action.PlayerAction
import io.music_assistant.client.ui.compose.common.icons.PauseIcon
import io.music_assistant.client.ui.compose.common.icons.PlayIcon
import io.music_assistant.client.ui.compose.common.icons.RepeatOffIcon
import io.music_assistant.client.ui.compose.common.icons.RepeatOnIcon
import io.music_assistant.client.ui.compose.common.icons.RepeatOneIcon
import io.music_assistant.client.ui.compose.common.icons.ShuffleOffIcon
import io.music_assistant.client.ui.compose.common.icons.ShuffleOnIcon
import io.music_assistant.client.ui.compose.common.icons.SkipBackIcon
import io.music_assistant.client.ui.compose.common.icons.SkipForwardIcon
import io.music_assistant.client.ui.compose.common.icons.SmartShuffleIcon
import io.music_assistant.client.ui.compose.common.icons.StopIcon
import io.music_assistant.client.ui.compose.common.tvFocus
import io.music_assistant.client.ui.compose.common.tvFocusRing
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.action_pause
import musicassistantclient.composeapp.generated.resources.action_play
import musicassistantclient.composeapp.generated.resources.action_stop
import musicassistantclient.composeapp.generated.resources.cd_shuffle_off
import musicassistantclient.composeapp.generated.resources.cd_shuffle_on
import musicassistantclient.composeapp.generated.resources.cd_shuffle_smart
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun PlayerControls(
    modifier: Modifier = Modifier,
    playerData: PlayerData,
    playerAction: (PlayerData, PlayerAction) -> Unit,
    showAdditionalButtons: Boolean = true,
    mainButtonSize: Dp,
    showSkip: Boolean = true,
    showSkipBack: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.primary,
    tvFocusFlow: TvFocusFlow? = null,
    playUpTarget: String = "slider",
    // Android TV: what LEFT from the leftmost button / RIGHT from the rightmost button should
    // reach — a sibling control outside this row (e.g. "group" or "playerSelect"). Null means
    // there is no such sibling, so that edge links to itself: without an explicit link Compose's
    // geometric fallback can jump straight into the next/previous HorizontalPager page (the
    // neighbouring player), which moves focus AND flips the current player on a single press.
    leftBoundaryTarget: String? = null,
    rightBoundaryTarget: String? = null,
    // Android TV: when a caller needs to land external D-pad focus directly on this row (e.g. "DOWN
    // from the sidebar should reach the player"), it attaches its own requester here and this row
    // hands it to whichever button actually renders first, so requesting focus lands on a real,
    // visibly-indicated control instead of a non-focusable or hidden one.
    entryFocusRequester: FocusRequester? = null,
    // Server audiobook_chapter_progress pref; gates chapter-based Next enablement.
    chapterProgressEnabled: Boolean = true,
) {
    val player = playerData.player
    val queue = playerData.queueInfo
    val transport = playerData.transportState(chapterProgressEnabled)
    val playerEnabled = transport.playerEnabled
    val buttonsEnabled = transport.buttonsEnabled
    // Audiobooks / podcast episodes swap shuffle & repeat for skip-back / skip-forward seek.
    val isLongForm = queue?.currentItem?.track.isLongFormSpokenContent
    // A live input never ends, so shuffle and repeat have nothing to act on.
    val isLiveInput = queue?.currentItem?.track is AudioSource
    val skipForwardEnabled = transport.skipForwardEnabled
    val smallButtonSize = (mainButtonSize.value * 0.6).dp
    // Explicit D-pad links for the transport row (Android TV): geometric focus search between
    // sibling IconButtons skips nodes, so the row is wired left-right through the focus graph.
    // Links are built from the buttons actually rendered (which vary with showSkip/showSkipBack/
    // showAdditionalButtons), so a missing button never leaves a dead directional link. The
    // play/pause main button links up to the seek slider (declared by the hosting screen).
    val focusLinks = remember(
        tvFocusFlow,
        showAdditionalButtons,
        showSkip,
        showSkipBack,
        playUpTarget,
        leftBoundaryTarget,
        rightBoundaryTarget,
    ) {
        val sequence = playerControlsSequence(showAdditionalButtons, showSkip, showSkipBack)
        buildMap {
            for (i in sequence.indices) {
                val left = if (i > 0) sequence[i - 1] else leftBoundaryTarget ?: sequence[i]
                val right = if (i < sequence.lastIndex) sequence[i + 1] else rightBoundaryTarget ?: sequence[i]
                put(
                    sequence[i],
                    TvFocusFlow.Links(
                        left = left,
                        right = right,
                        up = if (sequence[i] == "play") playUpTarget else null,
                    ),
                )
            }
        }
    }
    val firstControlId = remember(showAdditionalButtons, showSkip, showSkipBack) {
        playerControlsSequence(showAdditionalButtons, showSkip, showSkipBack).firstOrNull()
    }
    fun Modifier.entryFocusIfFirst(id: String): Modifier =
        if (entryFocusRequester != null && id == firstControlId) {
            focusRequester(entryFocusRequester)
        } else {
            this
        }
    Row(
        modifier = modifier
            .wrapContentSize(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showAdditionalButtons) {
            queue?.let {
                if (isLongForm) {
                    ActionButton(
                        icon = Icons.Rounded.Replay10,
                        tint = tint,
                        size = smallButtonSize,
                        enabled = playerEnabled && buttonsEnabled,
                        modifier = Modifier.tvFocus(tvFocusFlow, focusLinks, "shuffle").entryFocusIfFirst("shuffle"),
                    ) { playerAction(playerData, PlayerAction.SeekBy(-LongFormSeekDefaults.BACK_SECONDS)) }
                } else {
                    // Like the web: smart shuffle shows only while shuffle is on.
                    val smart = it.shuffleEnabled && it.smartShuffleActive
                    ActionButton(
                        icon = when {
                            smart -> SmartShuffleIcon
                            it.shuffleEnabled -> ShuffleOnIcon
                            else -> ShuffleOffIcon
                        },
                        tint = tint,
                        size = smallButtonSize,
                        enabled = playerEnabled && buttonsEnabled && !it.isDynamicPlaylist && !isLiveInput,
                        modifier = Modifier.tvFocus(tvFocusFlow, focusLinks, "shuffle").entryFocusIfFirst("shuffle"),
                        contentDescription = stringResource(
                            when {
                                smart -> Res.string.cd_shuffle_smart
                                it.shuffleEnabled -> Res.string.cd_shuffle_on
                                else -> Res.string.cd_shuffle_off
                            },
                        ),
                    ) {
                        playerAction(
                            playerData,
                            PlayerAction.ToggleShuffle(current = it.shuffleEnabled),
                        )
                    }
                }
            }
        }

        if (showSkipBack || showAdditionalButtons) {
            ActionButton(
                icon = SkipBackIcon,
                tint = tint,
                size = smallButtonSize,
                enabled = playerEnabled && buttonsEnabled,
                modifier = Modifier.tvFocus(tvFocusFlow, focusLinks, "prev").entryFocusIfFirst("prev"),
            ) { playerAction(playerData, PlayerAction.Previous) }
        }

        if (playerData.pendingPlay && !player.isPlaying) {
            IconButton(
                modifier = Modifier
                    .tvFocus(tvFocusFlow, focusLinks, "play")
                    .entryFocusIfFirst("play")
                    .tvFocusRing(shape = CircleShape)
                    .size(mainButtonSize),
                onClick = { playerAction(playerData, PlayerAction.Pause) },
                enabled = playerEnabled && buttonsEnabled,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size((mainButtonSize.value * 0.6).dp),
                    color = tint,
                    strokeWidth = 2.dp,
                )
            }
        } else {
            // The ring is a sibling, not part of the button: the button is dimmed while the
            // queue is empty, which is exactly when a first play request is in flight.
            Box(contentAlignment = Alignment.Center) {
                val main = playerData.mainTransport()
                ActionButton(
                    icon = main.button.icon,
                    tint = tint,
                    size = mainButtonSize,
                    enabled = main.enabled,
                    contentDescription = stringResource(main.button.label),
                    modifier = Modifier.tvFocus(tvFocusFlow, focusLinks, "play")
                        .entryFocusIfFirst("play")
                        .tvFocusRing(shape = CircleShape),
                ) { playerAction(playerData, main.action) }
                if (playerData.playRequestPending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(mainButtonSize),
                        color = tint,
                        strokeWidth = 2.dp,
                    )
                }
            }
        }

        if (showSkip) {
            ActionButton(
                icon = SkipForwardIcon,
                tint = tint,
                size = smallButtonSize,
                enabled = playerEnabled && buttonsEnabled && skipForwardEnabled,
                modifier = Modifier.tvFocus(tvFocusFlow, focusLinks, "next").entryFocusIfFirst("next"),
            ) { playerAction(playerData, PlayerAction.Next) }
        }

        if (showAdditionalButtons) {
            queue?.let {
                if (isLongForm) {
                    ActionButton(
                        icon = Icons.Rounded.Forward30,
                        tint = tint,
                        size = smallButtonSize,
                        enabled = playerEnabled && buttonsEnabled,
                        modifier = Modifier.tvFocus(tvFocusFlow, focusLinks, "repeat").entryFocusIfFirst("repeat"),
                    ) { playerAction(playerData, PlayerAction.SeekBy(LongFormSeekDefaults.FORWARD_SECONDS)) }
                } else {
                    val repeatMode = it.repeatMode
                    ActionButton(
                        icon = when (repeatMode) {
                            RepeatMode.ONE -> RepeatOneIcon
                            RepeatMode.ALL -> RepeatOnIcon
                            RepeatMode.OFF,
                            null,
                                -> RepeatOffIcon
                        },
                        tint = tint,
                        size = smallButtonSize,
                        enabled = playerEnabled && buttonsEnabled && repeatMode != null && !it.isDynamicPlaylist && !isLiveInput,
                        modifier = Modifier.tvFocus(tvFocusFlow, focusLinks, "repeat").entryFocusIfFirst("repeat"),
                    ) {
                        repeatMode?.let {
                            playerAction(
                                playerData,
                                PlayerAction.ToggleRepeatMode(current = repeatMode),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The transport row's buttons, left to right, for the combination of flags actually rendered.
 * Shared with the hosting screen (e.g. [CompactPlayerItem]) so it can compute which of its own
 * sibling controls (group button, player-select chip) the row's outer edges should link to.
 */
internal fun playerControlsSequence(
    showAdditionalButtons: Boolean,
    showSkip: Boolean,
    showSkipBack: Boolean,
): List<String> = buildList {
    if (showAdditionalButtons) add("shuffle")
    if (showSkipBack || showAdditionalButtons) add("prev")
    add("play")
    if (showSkip) add("next")
    if (showAdditionalButtons) add("repeat")
}

@Composable
private fun ActionButton(
    icon: ImageVector,
    size: Dp,
    tint: Color = MaterialTheme.colorScheme.primary,
    enabled: Boolean = true,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    IconButton(
        modifier = modifier
            .tvFocusRing(shape = CircleShape)
            .alphaOn(enabled)
            .size(size),
        onClick = onClick,
        enabled = enabled,
    ) {
        Icon(
            modifier = Modifier.size(size - 12.dp),
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
        )
    }
}

@Preview
@Composable
private fun Preview(
    showAdditionButtons: Boolean = true,
    showSkip: Boolean = true,
    showSkipBack: Boolean = true,
) {
    MaterialTheme {
        PlayerControls(
            playerData = PlayerDataFixtures.playerData(),
            playerAction = { _, _ -> },
            showSkip = showSkip,
            showSkipBack = showSkipBack,
            mainButtonSize = 60.dp,
            showAdditionalButtons = showAdditionButtons,
        )
    }
}

@Preview
@Composable
private fun PreviewNoAdditional() {
    Preview(showAdditionButtons = false)
}

@Preview
@Composable
private fun PreviewNoSkipNoAdditional() {
    Preview(showSkip = false, showAdditionButtons = false, showSkipBack = false)
}

/** What the transport buttons may do for a player. Shared by the player and the vertical bar. */
internal class TransportState(
    val playerEnabled: Boolean,
    val buttonsEnabled: Boolean,
    // Like the web app, Stop stays available while playing even if the item is not playable.
    val showStop: Boolean,
    val skipForwardEnabled: Boolean,
)

internal fun PlayerData.transportState(chapterProgressEnabled: Boolean): TransportState {
    val queue = queueInfo
    val itemsCount = queueItems?.size ?: 0
    // Next is a chapter jump when navigation applies (PlayerRequestFactory), so it stays
    // enabled while a chapter starts after the (event-driven, close-enough) position.
    val elapsedSec = queue?.elapsedTime ?: 0.0
    val hasUpcomingChapter = chapterProgressEnabled &&
        queue?.currentItem?.track.navigationChapters()?.any { it.start > elapsedSec } == true
    val skipForwardEnabled = when {
        queue?.currentIndex?.let { it < itemsCount - 1 } == true -> true
        hasUpcomingChapter -> true
        // Chapterless/last-chapter fallback: an in-progress audiobook still takes a plain next.
        (queue?.currentItem?.track as? Audiobook)?.let { it.fullyPlayed == false } == true -> true
        else -> false
    }
    return TransportState(
        playerEnabled = player.canPlay && !player.isAnnouncing,
        buttonsEnabled = queue?.currentItem?.isPlayable == true,
        showStop = player.isPlaying && !canPauseContent,
        skipForwardEnabled = skipForwardEnabled,
    )
}

/** The look of the main transport button. */
internal enum class MainButton(val icon: ImageVector, val label: StringResource) {
    PLAY(PlayIcon, Res.string.action_play),
    PAUSE(PauseIcon, Res.string.action_pause),
    STOP(StopIcon, Res.string.action_stop),
}

/** The main transport button of a player: what it shows, what it sends, and whether it can act. */
internal class MainTransport(val button: MainButton, val action: PlayerAction, val enabled: Boolean)

/**
 * The one rule for the main transport button on every surface: Stop for content that cannot pause,
 * and Pause (a cancel) while a play request is pending.
 */
internal fun PlayerData.mainTransport(): MainTransport {
    // The main button does not read skipForwardEnabled, the only value that the flag changes.
    val transport = transportState(chapterProgressEnabled = false)
    val pending = pendingPlay && !player.isPlaying
    return MainTransport(
        button = when {
            transport.showStop -> MainButton.STOP
            player.isPlaying || pending -> MainButton.PAUSE
            else -> MainButton.PLAY
        },
        action = when {
            pending -> PlayerAction.Pause
            transport.showStop -> PlayerAction.Stop
            else -> PlayerAction.TogglePlayPause
        },
        enabled = transport.playerEnabled && (transport.buttonsEnabled || transport.showStop),
    )
}
