package io.music_assistant.client.ui.compose.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun FloatingBar(modifier: Modifier = Modifier, onClick: () -> Unit = {}, content: @Composable () -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(FloatingBarDefaults.padding)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable { onClick() }
    ) {
        content()
    }
}

object FloatingBarDefaults {
    val padding = 8.dp
}

@Preview
@Composable
private fun PreviewFloatingBarRow() {
    FloatingBar {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Left")
            Text("Right")
        }
    }
}

@Preview
@Composable
private fun PreviewFloatingBarColumn() {
    FloatingBar {
        Column {
            Text("Top")
            Text("Bottom")
        }
    }
}

