package io.music_assistant.client.ui.compose.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.music_assistant.client.data.MainDataSource
import io.music_assistant.client.data.model.server.PlayMediaSupport
import org.koin.compose.koinInject

/**
 * The `play_media` flags that the server supports, so the play menus offer "Play shuffled" and
 * "Play from beginning" only when they work. [PlayMediaSupport.NONE] when nothing provides it.
 */
val LocalPlayMediaSupport = compositionLocalOf { PlayMediaSupport.NONE }

/** Exposes [MainDataSource.playMediaSupport] via [LocalPlayMediaSupport]. Provide near the app root. */
@Composable
fun ProvidePlayMediaSupport(content: @Composable () -> Unit) {
    val support by koinInject<MainDataSource>().playMediaSupport.collectAsStateWithLifecycle()
    CompositionLocalProvider(LocalPlayMediaSupport provides support, content)
}
