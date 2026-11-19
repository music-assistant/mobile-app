// Compose layout values (icon sizes, widths) are visual design tokens.
@file:Suppress("MagicNumber")

package io.music_assistant.client.wear.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ScreenScaffold
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
    // The stepper fills the screen on purpose: its own buttons carry the rim margin, and the
    // level indicator is meant to ride the bezel.
    ScreenScaffold(contentPadding = PaddingValues(0.dp)) {
        Box(Modifier.fillMaxSize()) {
            Stepper(
                value = volume ?: 0,
                onValueChange = onSetVolume,
                valueProgression = VOLUME_PROGRESSION,
                enabled = canChange,
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
