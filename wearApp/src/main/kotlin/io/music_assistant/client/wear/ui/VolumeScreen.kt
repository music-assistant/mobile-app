// Compose layout values (icon sizes, widths) are visual design tokens.
@file:Suppress("MagicNumber")

package io.music_assistant.client.wear.ui

import android.view.HapticFeedbackConstants
import android.view.ViewConfiguration
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.rememberActiveFocusRequester
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.ScreenScaffoldDefaults
import androidx.wear.compose.material3.Stepper
import androidx.wear.compose.material3.StepperDefaults
import androidx.wear.compose.material3.StepperLevelIndicator
import androidx.wear.compose.material3.Text
import io.music_assistant.client.wear.R
import io.music_assistant.client.wear.data.WearPlayer

/**
 * The volume of the selected player: Wear's full-screen stepper, louder at the top, quieter
 * at the bottom, with the level drawn up the left rim.
 *
 * The middle slot names what is being made louder and opens the output picker, so the screen
 * answers both halves of the same question — how loud, and out of what — without the user
 * having to find a second control. A player that reports no volume, or a session that is
 * down, disables the stepper rather than hiding it: an empty screen where a control used to
 * be reads as a bug, and the output button still has to work.
 *
 * The bezel or crown moves the volume as well as the buttons do — on a watch that has one,
 * turning it is the fastest way to a volume, and a screen that ignores it feels broken.
 */
@Composable
fun VolumeScreen(
    state: WearUiState,
    onSetVolume: (Int) -> Unit,
    onOpenOutput: () -> Unit,
) {
    val player = state.selected
    val volume = player?.volume
    val canChange = state.isConnected && volume != null && player.canSetVolume
    // Wear's own answer to how much of the top of the screen the clock needs, and it tracks
    // the screen size, so it does not have to be re-measured for every watch.
    val clockBand = ScreenScaffoldDefaults.contentPadding.calculateTopPadding()
    // The stepper fills the screen on purpose: its own buttons carry the rim margin, and the
    // level indicator is meant to ride the bezel.
    ScreenScaffold(contentPadding = PaddingValues(0.dp)) {
        Box(Modifier.fillMaxSize()) {
            Stepper(
                value = volume ?: 0,
                onValueChange = onSetVolume,
                valueProgression = VOLUME_PROGRESSION,
                enabled = canChange,
                // The clock is drawn over the top of every screen, and the stepper would
                // otherwise put its increase button straight under it. Both ends are inset
                // by the same amount so the output button stays on the centre line and the
                // two stepper buttons stay the same distance from their own edge.
                modifier = Modifier
                    .padding(vertical = clockBand)
                    .rotaryVolume(volume, canChange, onSetVolume),
                decreaseIcon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_volume_down),
                        contentDescription = stringResource(R.string.cd_volume_down),
                        modifier = Modifier.size(StepperDefaults.IconSize),
                    )
                },
                increaseIcon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_volume_up),
                        contentDescription = stringResource(R.string.cd_volume_up),
                        modifier = Modifier.size(StepperDefaults.IconSize),
                    )
                },
            ) {
                OutputButton(
                    player = player,
                    enabled = state.isConnected,
                    onClick = onOpenOutput,
                )
            }
            // Not inset with the stepper: the level is meant to be read off the rim, and the
            // clock sits at the top of the screen rather than at its left.
            StepperLevelIndicator(
                value = { volume ?: 0 },
                valueProgression = VOLUME_PROGRESSION,
                enabled = canChange,
                modifier = Modifier.align(Alignment.CenterStart),
            )
        }
    }
}

/**
 * Turns rotary input — a rotating bezel, a crown, the side button — into volume steps.
 *
 * Rotary arrives as a scroll distance rather than as detents, so the distance is banked and
 * spent one [VOLUME_PROGRESSION] step at a time. The remainder is kept, which is what makes a
 * slow turn move the volume at all instead of rounding every event away.
 *
 * The step is taken from [commanded] rather than from [volume] whenever we are the ones who
 * last moved it. The repository updates its state as soon as a command is sent, but a turn
 * can still deliver several events inside one frame, and reading the composed value would
 * then apply every one of them to the same starting point and lose all but the last.
 * [commanded] is dropped as soon as the volume arrives from anywhere else — another client,
 * or a different player being picked — so the next turn starts from what the watch shows.
 */
@Composable
@Suppress("DEPRECATION")  // The replacement landed after Wear Compose 1.7, which this builds on.
private fun Modifier.rotaryVolume(
    volume: Int?,
    enabled: Boolean,
    onSetVolume: (Int) -> Unit,
): Modifier {
    val focusRequester = rememberActiveFocusRequester()
    val view = LocalView.current
    // One detent of a rotating bezel reports exactly this much scroll, so a detent is a step.
    // A crown reports a finer, continuous stream and so takes a proportional part of one.
    val stepDistance = remember(view) {
        ViewConfiguration.get(view.context).scaledVerticalScrollFactor
    }
    val latestVolume by rememberUpdatedState(volume)
    var commanded by remember { mutableStateOf<Int?>(null) }
    val banked = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(volume) {
        if (volume != commanded) commanded = null
    }
    return this
        .onRotaryScrollEvent { event ->
            if (!enabled) return@onRotaryScrollEvent false
            // Rotary arrives as the distance a list would travel: positive for the clockwise
            // turn that carries a list forward, which is also the turn that makes a volume
            // louder, so the sign carries straight over.
            banked.floatValue += event.verticalScrollPixels
            val steps = (banked.floatValue / stepDistance).toInt()
            if (steps != 0) {
                banked.floatValue -= steps * stepDistance
                val base = commanded ?: latestVolume ?: 0
                val next = (base + steps * VOLUME_PROGRESSION.step)
                    .coerceIn(VOLUME_PROGRESSION.first, VOLUME_PROGRESSION.last)
                if (next != base) {
                    commanded = next
                    onSetVolume(next)
                    // The same tick the watch gives for its own steppers. Without it a turn
                    // is read entirely off the screen, which is the wrong place to be looking
                    // while reaching for a volume.
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                }
            }
            true
        }
        .focusRequester(focusRequester)
        .focusable()
}

/**
 * The selected player, as the one filled control on the screen: it is both the label for the
 * volume around it and the way to the output picker.
 */
@Composable
private fun OutputButton(
    player: WearPlayer?,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(OUTPUT_BUTTON_WIDTH),
        colors = ButtonDefaults.buttonColors(),
        icon = {
            Icon(
                painter = painterResource(R.drawable.ic_speaker),
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize),
            )
        },
        label = {
            Text(
                text = player?.name ?: stringResource(R.string.no_players),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}

/** How much of the screen the output button takes, leaving the stepper's buttons reachable. */
private const val OUTPUT_BUTTON_WIDTH = 0.82f

/**
 * The volume steps the stepper offers: the server takes 0..100, and 5 points a press is a
 * noticeable change without turning the screen into twenty taps.
 */
private val VOLUME_PROGRESSION = 0..100 step 5
