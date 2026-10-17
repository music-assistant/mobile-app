package io.music_assistant.client.ui.compose.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.music_assistant.client.data.MainDataSource
import io.music_assistant.client.data.model.server.GrantedScopes
import org.koin.compose.koinInject

/**
 * What the signed-in user may do. Every surface that offers a write action reads it, so the
 * gate is not threaded through each screen. Fails closed when nothing provides it.
 */
val LocalGrantedScopes = compositionLocalOf { GrantedScopes.NONE }

/** Exposes [MainDataSource.grantedScopes] via [LocalGrantedScopes]. Provide near the app root. */
@Composable
fun ProvideGrantedScopes(content: @Composable () -> Unit) {
    val scopes by koinInject<MainDataSource>().grantedScopes.collectAsStateWithLifecycle()
    CompositionLocalProvider(LocalGrantedScopes provides scopes, content)
}
