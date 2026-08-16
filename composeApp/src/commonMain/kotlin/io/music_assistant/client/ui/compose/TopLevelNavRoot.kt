package io.music_assistant.client.ui.compose

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.DialogSceneStrategy
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.auth.AuthenticationManager
import io.music_assistant.client.ui.compose.common.AutoLoginSplash
import io.music_assistant.client.ui.compose.common.ConnectionStatusBanner
import io.music_assistant.client.ui.compose.home.MainNavigationRoot
import io.music_assistant.client.ui.compose.nav.exitApp
import io.music_assistant.client.ui.compose.settings.SettingsScreen
import io.music_assistant.client.utils.AuthProcessState
import io.music_assistant.client.utils.BottomSheetSceneStrategy
import io.music_assistant.client.utils.DataConnectionState
import io.music_assistant.client.utils.SessionState
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopLevelNavRoot(modifier: Modifier = Modifier) {
    val serviceClient: ServiceClient = koinInject()
    val authManager: AuthenticationManager = koinInject()
    val sessionState by serviceClient.sessionState.collectAsStateWithLifecycle()

    // Cold-launch splash overlay during silent auto-login. Once dismissed (auth resolved
    // or connection error), it never reappears within this composable's lifetime —
    // user-initiated reconnects and background→foreground transitions don't bring it back.
    var splashDismissed by remember { mutableStateOf(false) }
    val splashVisible = !splashDismissed &&
            authManager.willAutoLoginOnLaunch &&
            when (val s = sessionState) {
                SessionState.Disconnected.Initial -> true
                SessionState.Connecting -> true
                is SessionState.Connected ->
                    s.dataConnectionState !is DataConnectionState.Authenticated &&
                            s.authProcessState !is AuthProcessState.Failed

                else -> false
            }
    LaunchedEffect(sessionState) {
        val terminal = when (val s = sessionState) {
            is SessionState.Connected -> {
                s.dataConnectionState is DataConnectionState.Authenticated ||
                        s.authProcessState is AuthProcessState.Failed
            }

            is SessionState.Disconnected.Error,
            is SessionState.Disconnected.ByUser,
            is SessionState.Disconnected.NoServerData,
                -> true

            else -> false
        }
        if (terminal) splashDismissed = true
    }

    // Determine initial screen based on authentication state.
    //
    // If auto-login is going to run on this cold launch, bias the initial screen to Main
    // so the splash overlay covers Main while it loads. When auth resolves successfully
    // splash dismisses and the user lands directly on Main — without this bias the
    // transition-less NavDisplay (workaround for the iOS SubcomposeLayout crash) would
    // briefly flash Settings before the LaunchedEffect below navigates to Main.
    // Auto-login failure is also covered: splash stays up until terminal state, and the
    // LaunchedEffect below redirects Main → Settings in the same recomposition pass.
    val initialScreen = when {
        sessionState is SessionState.Connected &&
                (sessionState as SessionState.Connected).dataConnectionState is
                DataConnectionState.Authenticated -> Nav.Main

        authManager.willAutoLoginOnLaunch -> Nav.Main
        else -> Nav.Settings
    }

    val backStack = rememberNavBackStack(
        SavedStateConfiguration(
            from = SavedStateConfiguration.DEFAULT,
            builderAction = {
                serializersModule = SerializersModule {
                    polymorphic(NavKey::class) {
                        subclass(Nav.Main::class, Nav.Main.serializer())
                        subclass(Nav.Settings::class, Nav.Settings.serializer())
                    }
                }
            },
        ),
        initialScreen,
    )

    // Monitor session state and navigate appropriately
    LaunchedEffect(sessionState) {
        when (sessionState) {
            is SessionState.Reconnecting -> {
                // Preserve current screen during reconnection - don't navigate
            }

            is SessionState.Disconnected -> {
                if (sessionState is SessionState.Disconnected.Backgrounded) {
                    // Backgrounded — preserve current screen for instant foreground reconnect
                } else {
                    // Navigate to Settings for all other disconnected states
                    // This includes: ByUser, Initial, NoServerData, and Error (max attempts reached)
                    if (backStack.last() !is Nav.Settings) {
                        backStack.clear()
                        backStack.add(Nav.Settings)
                    }
                }
            }

            is SessionState.Connected -> {
                val connectedState = sessionState as SessionState.Connected
                val connState = connectedState.dataConnectionState

                when {
                    // Auto-navigate to Home ONLY when authenticated via auto-login with saved token
                    connState is DataConnectionState.Authenticated && connectedState.wasAutoLogin -> {
                        if (backStack.last() !is Nav.Main) {
                            backStack.clear()
                            backStack.add(Nav.Main)
                        }
                    }

                    // Transport is up but auth was rejected (disabled user, expired/invalid token).
                    // Necessary because initial screen is biased to Main on auto-login launches;
                    // without this, the splash would dismiss and reveal Main behind a failed auth.
                    connectedState.authProcessState is AuthProcessState.Failed -> {
                        if (backStack.last() !is Nav.Settings) {
                            backStack.clear()
                            backStack.add(Nav.Settings)
                        }
                    }
                }
                // Other Connected sub-states intentionally don't navigate here — we let
                // Disconnected handling drive Settings redirects to avoid racing reconnection.
            }

            is SessionState.Connecting -> {
                /* Do nothing */
            }
        }
    }
    val bottomSheetStrategy = remember { BottomSheetSceneStrategy<NavKey>() }
    val dialogStrategy = remember { DialogSceneStrategy<NavKey>() }

    Box(modifier = modifier) {
        // Main navigation content
        NavDisplay(
            modifier = Modifier.fillMaxSize(),
            backStack = backStack,
            onBack = { backStack.removeLastOrNull() },
            sceneStrategies = listOf(bottomSheetStrategy, dialogStrategy),
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(
                    rememberSaveableStateHolder(),
                ),
            ),
            // Workaround for CMP 1.10.3 iOS crash: LazyLayout measured inside
            // AnimatedContent + CupertinoOverscroll trips a SubcomposeLayout
            // precondition on first frame. Disabling transitions removes the
            // animating measure path.
            transitionSpec = { EnterTransition.None togetherWith ExitTransition.None },
            popTransitionSpec = { EnterTransition.None togetherWith ExitTransition.None },
            predictivePopTransitionSpec = { EnterTransition.None togetherWith ExitTransition.None },
            entryProvider = entryProvider {
                entry<Nav.Main> {
                    MainNavigationRoot(
                        goToSettings = { backStack.add(Nav.Settings) },
                    )
                }

                entry<Nav.Settings> {
                    SettingsScreen(
                        goHome = {
                            ->
                            backStack.clear()
                            backStack.add(Nav.Main)
                        },
                        exitApp = { exitApp() },
                    )
                }
            },
        )

        // Connection status banner - overlays at top, doesn't shrink content
        ConnectionStatusBanner(
            modifier = Modifier.align(Alignment.TopCenter),
        )

        // Auto-login splash — drawn last so it covers the banner during the splash window.
        AutoLoginSplash(
            visible = splashVisible,
            onCancel = {
                // Tear down transport (cancels any in-flight `client/auth`) and lock
                // splash dismissed via the terminal-state branch in LaunchedEffect above.
                serviceClient.disconnectByUser()
                splashDismissed = true
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

private sealed interface Nav : NavKey {
    @Serializable
    data object Main : Nav

    @Serializable
    data object Settings : Nav
}
