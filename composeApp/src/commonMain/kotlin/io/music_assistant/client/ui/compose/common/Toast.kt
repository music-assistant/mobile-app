package io.music_assistant.client.ui.compose.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

enum class ToastDuration(val millis: Long) {
    SHORT(2000L),
    LONG(3500L),
}

data class ToastData(
    val message: String,
    val duration: ToastDuration = ToastDuration.SHORT,
)

class ToastState {
    private val _currentToast = mutableStateOf<ToastData?>(null)
    val currentToast: State<ToastData?> = _currentToast

    fun showToast(message: String, duration: ToastDuration = ToastDuration.SHORT) {
        _currentToast.value = ToastData(message, duration)
    }

    fun hideToast() {
        _currentToast.value = null
    }
}

@Composable
fun rememberToastState(): ToastState {
    return remember { ToastState() }
}

@Composable
fun ToastHost(
    toastState: ToastState,
    modifier: Modifier = Modifier,
) {
    val currentToast by toastState.currentToast

    // Auto dismiss toast after duration
    LaunchedEffect(currentToast) {
        currentToast?.let { toast ->
            delay(toast.duration.millis)
            toastState.hideToast()
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        AnimatedVisibility(
            visible = currentToast != null,
            enter = slideInVertically(
                initialOffsetY = { it },
            ) + fadeIn(),
            exit = slideOutVertically(
                targetOffsetY = { it },
            ) + fadeOut(),
        ) {
            currentToast?.let { toast ->
                ToastItem(
                    message = toast.message,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}

@Composable
private fun ToastItem(
    message: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(
                color = Color.Black.copy(alpha = 0.8f),
                shape = RoundedCornerShape(8.dp),
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = message,
            color = Color.White,
        )
    }
}
