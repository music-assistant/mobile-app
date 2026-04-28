package io.music_assistant.client.ui.compose.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
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
import io.music_assistant.client.ui.compose.common.ConnectionStatusBanner
import io.music_assistant.client.ui.compose.home.MainNavigationRoot
import io.music_assistant.client.ui.compose.settings.SettingsScreen
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
    val sessionState by serviceClient.sessionState.collectAsStateWithLifecycle()

    // Determine initial screen based on authentication state
    val initialScreen = when (val state = sessionState) {
        is SessionState.Connected -> {
            when (state.dataConnectionState) {
                DataConnectionState.Authenticated -> Nav.Home
                else -> Nav.Settings
            }
        }

        else -> Nav.Settings
    }

    val backStack = rememberNavBackStack(
        SavedStateConfiguration(
            from = SavedStateConfiguration.DEFAULT,
            builderAction = {
                serializersModule = SerializersModule {
                    polymorphic(NavKey::class) {
                        subclass(Nav.Home::class, Nav.Home.serializer())
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

                // Auto-navigate to Home ONLY when authenticated via auto-login with saved token
                if (connState == DataConnectionState.Authenticated && connectedState.wasAutoLogin) {
                    if (backStack.last() !is Nav.Home) {
                        backStack.clear()
                        backStack.add(Nav.Home)
                    }
                }
                // Don't navigate to Settings here - we handle Disconnected states separately
                // This prevents navigation during reconnection when auth might not be loaded yet
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
            sceneStrategy = bottomSheetStrategy.then(dialogStrategy),
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(
                    rememberSaveableStateHolder(),
                ),
            ),
            entryProvider = entryProvider {
                entry<Nav.Home> {
                    MainNavigationRoot(
                        goToSettings = { backStack.add(Nav.Settings) },
                    )
                }

                entry<Nav.Settings> {
                    SettingsScreen(
                        goHome = {
                            ->
                            backStack.clear()
                            backStack.add(Nav.Home)
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
    }
}

private sealed interface Nav : NavKey {
    @Serializable
    data object Home : Nav

    @Serializable
    data object Settings : Nav
}
