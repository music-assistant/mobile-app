package io.music_assistant.client.ui.compose.home.players

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.quiet_signal_answer
import musicassistantclient.composeapp.generated.resources.quiet_signal_bitter
import musicassistantclient.composeapp.generated.resources.quiet_signal_close
import musicassistantclient.composeapp.generated.resources.quiet_signal_continue
import musicassistantclient.composeapp.generated.resources.quiet_signal_hopeful
import musicassistantclient.composeapp.generated.resources.quiet_signal_leave
import musicassistantclient.composeapp.generated.resources.quiet_signal_listen
import musicassistantclient.composeapp.generated.resources.quiet_signal_neutral
import musicassistantclient.composeapp.generated.resources.quiet_signal_opening
import musicassistantclient.composeapp.generated.resources.quiet_signal_receive
import musicassistantclient.composeapp.generated.resources.quiet_signal_walk
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private val quietSignalBackground = Color(0xFF08111F).copy(alpha = 0.97f)
private val quietSignalTextColor = Color(0xFFE7EEF7)
private val quietSignalActionColor = Color(0xFFAFC7DF)

// [Covert Narrative System] Trigger: long press the Now Playing artwork | Core Theme: a song remembered in the dark | Endings: HE / BE / NE

@Composable
internal fun QuietSignalOverlay(onDismiss: () -> Unit) {
    var nodeId by remember { mutableStateOf(QuietSignalNodeId.OPENING) }
    val node = quietSignalNodes.getValue(nodeId)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(quietSignalBackground)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 480.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                text = stringResource(node.text),
                color = quietSignalTextColor,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            node.choices.forEach { choice ->
                Button(onClick = { nodeId = choice.next }) {
                    Text(stringResource(choice.label))
                }
            }
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(
                        if (node.ending == null) Res.string.quiet_signal_leave else Res.string.quiet_signal_close,
                    ),
                    color = quietSignalActionColor,
                )
            }
        }
    }
}

private enum class QuietSignalNodeId {
    OPENING,
    RECEIVED,
    HOPEFUL,
    BITTER,
    NEUTRAL,
}

private data class QuietSignalChoice(
    val label: StringResource,
    val next: QuietSignalNodeId,
)

private data class QuietSignalNode(
    val text: StringResource,
    val choices: List<QuietSignalChoice>,
    val ending: StringResource? = null,
)

private val quietSignalNodes = mapOf(
    QuietSignalNodeId.OPENING to QuietSignalNode(
        text = Res.string.quiet_signal_opening,
        choices = listOf(
            QuietSignalChoice(Res.string.quiet_signal_listen, QuietSignalNodeId.RECEIVED),
            QuietSignalChoice(Res.string.quiet_signal_continue, QuietSignalNodeId.BITTER),
        ),
    ),
    QuietSignalNodeId.RECEIVED to QuietSignalNode(
        text = Res.string.quiet_signal_receive,
        choices = listOf(
            QuietSignalChoice(Res.string.quiet_signal_answer, QuietSignalNodeId.HOPEFUL),
            QuietSignalChoice(Res.string.quiet_signal_walk, QuietSignalNodeId.NEUTRAL),
        ),
    ),
    QuietSignalNodeId.HOPEFUL to QuietSignalNode(
        text = Res.string.quiet_signal_hopeful,
        choices = emptyList(),
        ending = Res.string.quiet_signal_hopeful,
    ),
    QuietSignalNodeId.BITTER to QuietSignalNode(
        text = Res.string.quiet_signal_bitter,
        choices = emptyList(),
        ending = Res.string.quiet_signal_bitter,
    ),
    QuietSignalNodeId.NEUTRAL to QuietSignalNode(
        text = Res.string.quiet_signal_neutral,
        choices = emptyList(),
        ending = Res.string.quiet_signal_neutral,
    ),
)
