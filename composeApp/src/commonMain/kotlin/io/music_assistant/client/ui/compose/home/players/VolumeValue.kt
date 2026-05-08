package io.music_assistant.client.ui.compose.home.players

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign

@Composable
fun VolumeValue(
    volume: Int,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
) {
    Box(modifier = modifier, contentAlignment = Alignment.CenterEnd) {
        Text(
            text = "100",
            style = style,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.alpha(0f),
        )
        Text(
            text = volume.toString(),
            style = style,
            color = color,
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.End,
        )
    }
}
