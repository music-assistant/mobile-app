package io.music_assistant.client.ui.compose.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.music_assistant.client.data.MainDataSource
import org.koin.compose.koinInject

/**
 * Whether the server keeps a dislike, so the favorite menus offer it. Every favorite menu reads
 * it, so the gate is not threaded through each screen. False when nothing provides it.
 */
val LocalDislikeSupported = compositionLocalOf { false }

/** Exposes [MainDataSource.dislikeSupported] via [LocalDislikeSupported]. Provide near the app root. */
@Composable
fun ProvideDislikeSupport(content: @Composable () -> Unit) {
    val supported by koinInject<MainDataSource>().dislikeSupported.collectAsStateWithLifecycle()
    CompositionLocalProvider(LocalDislikeSupported provides supported, content)
}
