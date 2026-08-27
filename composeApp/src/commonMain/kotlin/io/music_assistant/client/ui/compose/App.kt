package io.music_assistant.client.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.ui.BackgroundRestrictionViewModel
import io.music_assistant.client.ui.SchemaVersionWarningViewModel
import io.music_assistant.client.ui.SchemaWarning
import io.music_assistant.client.ui.compose.common.dismissKeyboardOnTap
import io.music_assistant.client.ui.compose.common.items.ProvideClickActionPrefs
import io.music_assistant.client.ui.compose.nav.exitApp
import io.music_assistant.client.ui.theme.AppTheme
import io.music_assistant.client.ui.theme.SystemAppearance
import io.music_assistant.client.ui.theme.ThemeSetting
import io.music_assistant.client.ui.theme.ThemeViewModel
import io.music_assistant.client.ui.theme.isSystemInDarkTheme
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.background_usage_dialog_dismiss
import musicassistantclient.composeapp.generated.resources.background_usage_dialog_message
import musicassistantclient.composeapp.generated.resources.background_usage_dialog_open_settings
import musicassistantclient.composeapp.generated.resources.background_usage_dialog_title
import musicassistantclient.composeapp.generated.resources.schema_incompatible_dialog_exit
import musicassistantclient.composeapp.generated.resources.schema_incompatible_dialog_message
import musicassistantclient.composeapp.generated.resources.schema_incompatible_dialog_title
import org.jetbrains.compose.resources.stringResource
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
    val followsSystem = theme.value == ThemeSetting.FollowSystem
    val darkTheme = when (theme.value) {
        ThemeSetting.Dark -> true
        ThemeSetting.Light -> false
        ThemeSetting.FollowSystem -> isSystemInDarkTheme()
    }
    SystemAppearance(isDarkTheme = darkTheme, followsSystem = followsSystem)
    AppTheme(darkTheme = darkTheme) {
        // Paint a themed background behind everything so the (light) platform window background
        // never bleeds through the transparent system bars in edge-to-edge — otherwise any region
        // Compose doesn't cover (e.g. the nav-bar strip beside the rail) shows as an off-theme white.
        Box(
            Modifier.fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .dismissKeyboardOnTap(),
        ) {
            ProvideClickActionPrefs {
                TopLevelNavRoot()
            }
            StatusBarScrim(darkTheme, Modifier.align(Alignment.TopCenter))
            BackgroundRestrictionDialog()
            SchemaVersionWarningDialog()
        }
    }
}

/**
 * Single warning dialog for the OS "background usage disabled" state (Android), which kills local
 * playback shortly after backgrounding. Shown at most once per launch and on enabling the local
 * player; see [BackgroundRestrictionViewModel] for the cadence.
 */
@OptIn(KoinExperimentalAPI::class)
@Composable
private fun BackgroundRestrictionDialog() {
    val viewModel = koinViewModel<BackgroundRestrictionViewModel>()
    val shouldWarn by viewModel.shouldWarn.collectAsStateWithLifecycle()
    // Transient per-appearance hide (Open settings / tap-outside) that doesn't persist. Reset when a
    // fresh warning condition arises (e.g. the local player is re-enabled) so that re-triggers show.
    var hidden by remember { mutableStateOf(false) }
    LaunchedEffect(shouldWarn) { if (shouldWarn) hidden = false }
    if (!shouldWarn || hidden) return
    AlertDialog(
        onDismissRequest = { hidden = true },
        title = { Text(stringResource(Res.string.background_usage_dialog_title)) },
        text = { Text(stringResource(Res.string.background_usage_dialog_message)) },
        confirmButton = {
            TextButton(onClick = {
                viewModel.onOpenSettings()
                hidden = true
            }) {
                Text(stringResource(Res.string.background_usage_dialog_open_settings))
            }
        },
        dismissButton = {
            TextButton(onClick = viewModel::onDismiss) {
                Text(stringResource(Res.string.background_usage_dialog_dismiss))
            }
        },
    )
}

/**
 * Schema-compatibility warning for the connected server (see [SchemaVersionWarningViewModel]),
 * re-shown on every fresh server-info arrival:
 * - [SchemaWarning.CLIENT_INCOMPATIBLE] — terminal: the client is below the server's minimum
 *   supported schema, so it can't function. Exit-only; any dismissal closes the app.
 * - [SchemaWarning.SERVER_AHEAD] — informational: the server is newer than the client, some
 *   features may misbehave. Dismissible; auth proceeds underneath.
 */
@OptIn(KoinExperimentalAPI::class)
@Composable
private fun SchemaVersionWarningDialog() {
    val viewModel = koinViewModel<SchemaVersionWarningViewModel>()
    val warning by viewModel.warning.collectAsStateWithLifecycle()
    // Transient hide for the dismissible warning; reset whenever a fresh warning arrives.
    var hidden by remember { mutableStateOf(false) }
    LaunchedEffect(warning) { hidden = false }
    when (warning) {
        null -> Unit

        SchemaWarning.CLIENT_INCOMPATIBLE ->
            AlertDialog(
                onDismissRequest = { exitApp() },
                title = { Text(stringResource(Res.string.schema_incompatible_dialog_title)) },
                text = { Text(stringResource(Res.string.schema_incompatible_dialog_message)) },
                confirmButton = {
                    TextButton(onClick = { exitApp() }) {
                        Text(stringResource(Res.string.schema_incompatible_dialog_exit))
                    }
                },
            )

        SchemaWarning.SERVER_AHEAD -> Unit
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
