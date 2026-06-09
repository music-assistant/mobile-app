package io.music_assistant.client.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.ui.compose.common.dismissKeyboardOnTap
import io.music_assistant.client.ui.compose.common.items.ProvideClickActionPrefs
import io.music_assistant.client.ui.theme.AppTheme
import io.music_assistant.client.ui.theme.SystemAppearance
import io.music_assistant.client.ui.theme.ThemeSetting
import io.music_assistant.client.ui.theme.ThemeViewModel
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.KoinExperimentalAPI

@Composable
private fun AppLifecycleObserver() {
    val serviceClient: ServiceClient = koinInject()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> serviceClient.onAppForeground()
                Lifecycle.Event.ON_STOP -> serviceClient.onAppBackground()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}

@OptIn(KoinExperimentalAPI::class)
@Composable
fun App() {
    AppLifecycleObserver()
    val themeViewModel = koinViewModel<ThemeViewModel>()
    val theme = themeViewModel.theme.collectAsStateWithLifecycle(ThemeSetting.FollowSystem)
    val darkTheme = when (theme.value) {
        ThemeSetting.Dark -> true
        ThemeSetting.Light -> false
        ThemeSetting.FollowSystem -> isSystemInDarkTheme()
    }
    SystemAppearance(isDarkTheme = darkTheme)
    AppTheme(darkTheme = darkTheme) {
        Box(Modifier.fillMaxSize().dismissKeyboardOnTap()) {
            ProvideClickActionPrefs {
                TopLevelNavRoot()
            }
            StatusBarScrim(darkTheme, Modifier.align(Alignment.TopCenter))
        }
    }
}

/**
 * Protection shade over just the status-bar inset strip so the system clock/icons stay legible
 * against arbitrary content drawn beneath them under edge-to-edge, while content stays visible
 * through it. A straight top-to-bottom gradient fades to transparent by the strip's bottom, so
 * nothing below the status bar is dimmed and there's no hard bottom edge.
 *
 * Strength is theme-adaptive after Android's own edge-to-edge scrims: light theme needs a much
 * stronger scrim than dark (cf. `DefaultLightScrim` ≈ 0.9 / `DefaultDarkScrim` ≈ 0.5). The tint
 * itself uses [MaterialTheme]'s `surface` so it matches the active theme rather than always-black.
 */
@Composable
private fun StatusBarScrim(darkTheme: Boolean, modifier: Modifier = Modifier) {
    @Suppress("MagicNumber") // Visual design tokens, after Android's DefaultLight/DarkScrim.
    val topAlpha = if (darkTheme) 0.5f else 0.9f
    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsTopHeight(WindowInsets.statusBars)
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.surface.copy(alpha = topAlpha),
                        Color.Transparent,
                    ),
                ),
            ),
    )
}
