package io.music_assistant.client.ui.compose.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.music_assistant.client.api.ConnectionInfo
import io.music_assistant.client.api.Defaults
import io.music_assistant.client.auth.ServerIdMismatchException
import io.music_assistant.client.data.model.server.ServerInfo
import io.music_assistant.client.data.model.server.User
import io.music_assistant.client.player.sendspin.SendspinConfig
import io.music_assistant.client.player.sendspin.audio.Codec
import io.music_assistant.client.player.sendspin.audio.Codecs
import io.music_assistant.client.settings.ConnectionHistoryEntry
import io.music_assistant.client.settings.ConnectionType
import io.music_assistant.client.ui.compose.auth.AuthenticationPanel
import io.music_assistant.client.ui.compose.common.DisplayString
import io.music_assistant.client.ui.compose.common.OverflowMenuButton
import io.music_assistant.client.ui.compose.common.OverflowMenuOption
import io.music_assistant.client.ui.compose.common.TvFocusFlow
import io.music_assistant.client.ui.compose.common.TvPreferenceRow
import io.music_assistant.client.ui.compose.common.TvTextEditorDialog
import io.music_assistant.client.ui.compose.common.clearFocusOnScroll
import io.music_assistant.client.ui.compose.common.localizedTitle
import io.music_assistant.client.ui.compose.common.rememberTvFocusFlow
import io.music_assistant.client.ui.compose.common.toDisplayString
import io.music_assistant.client.ui.compose.common.tvFocus
import io.music_assistant.client.ui.compose.common.tvFocusRing
import io.music_assistant.client.ui.compose.nav.BackHandler
import io.music_assistant.client.ui.compose.nav.TopBarLayout
import io.music_assistant.client.ui.theme.ThemeSetting
import io.music_assistant.client.ui.theme.ThemeViewModel
import io.music_assistant.client.utils.DataConnectionState
import io.music_assistant.client.utils.SessionState
import io.music_assistant.client.utils.hasCamera
import io.music_assistant.client.utils.isIpPort
import io.music_assistant.client.utils.isTelevisionDevice
import io.music_assistant.client.utils.isValidHost
import io.music_assistant.client.webrtc.model.RemoteId
import kotlinx.coroutines.delay
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.auth_title
import musicassistantclient.composeapp.generated.resources.cd_connection_history
import musicassistantclient.composeapp.generated.resources.cd_delete_crash_logs
import musicassistantclient.composeapp.generated.resources.cd_scan_qr_code
import musicassistantclient.composeapp.generated.resources.cd_select_codec
import musicassistantclient.composeapp.generated.resources.common_back
import musicassistantclient.composeapp.generated.resources.common_cancel
import musicassistantclient.composeapp.generated.resources.common_delete
import musicassistantclient.composeapp.generated.resources.connection_error_generic
import musicassistantclient.composeapp.generated.resources.connection_error_lost
import musicassistantclient.composeapp.generated.resources.connection_error_timeout
import musicassistantclient.composeapp.generated.resources.connection_error_tls
import musicassistantclient.composeapp.generated.resources.connection_error_unreachable
import musicassistantclient.composeapp.generated.resources.connection_error_webrtc
import musicassistantclient.composeapp.generated.resources.nav_settings
import musicassistantclient.composeapp.generated.resources.server_id_mismatch_error
import musicassistantclient.composeapp.generated.resources.settings_about_description
import musicassistantclient.composeapp.generated.resources.settings_about_learn_more
import musicassistantclient.composeapp.generated.resources.settings_buffer_size
import musicassistantclient.composeapp.generated.resources.settings_codec_preference
import musicassistantclient.composeapp.generated.resources.settings_connect
import musicassistantclient.composeapp.generated.resources.settings_connect_saved
import musicassistantclient.composeapp.generated.resources.settings_connect_webrtc
import musicassistantclient.composeapp.generated.resources.settings_connected
import musicassistantclient.composeapp.generated.resources.settings_connected_to
import musicassistantclient.composeapp.generated.resources.settings_connected_webrtc
import musicassistantclient.composeapp.generated.resources.settings_connecting
import musicassistantclient.composeapp.generated.resources.settings_connecting_remote
import musicassistantclient.composeapp.generated.resources.settings_connecting_to
import musicassistantclient.composeapp.generated.resources.settings_connection_direct
import musicassistantclient.composeapp.generated.resources.settings_connection_experimental
import musicassistantclient.composeapp.generated.resources.settings_connection_method
import musicassistantclient.composeapp.generated.resources.settings_connection_webrtc
import musicassistantclient.composeapp.generated.resources.settings_custom_sendspin
import musicassistantclient.composeapp.generated.resources.settings_disable_local_player
import musicassistantclient.composeapp.generated.resources.settings_disconnect
import musicassistantclient.composeapp.generated.resources.settings_enable_local_player
import musicassistantclient.composeapp.generated.resources.settings_exit_app
import musicassistantclient.composeapp.generated.resources.settings_history_direct
import musicassistantclient.composeapp.generated.resources.settings_history_webrtc
import musicassistantclient.composeapp.generated.resources.settings_host
import musicassistantclient.composeapp.generated.resources.settings_local_player_disabled
import musicassistantclient.composeapp.generated.resources.settings_local_player_enabled
import musicassistantclient.composeapp.generated.resources.settings_misc
import musicassistantclient.composeapp.generated.resources.settings_no_saved_connections
import musicassistantclient.composeapp.generated.resources.settings_path
import musicassistantclient.composeapp.generated.resources.settings_player_name
import musicassistantclient.composeapp.generated.resources.settings_port
import musicassistantclient.composeapp.generated.resources.settings_port_default
import musicassistantclient.composeapp.generated.resources.settings_remote_id
import musicassistantclient.composeapp.generated.resources.settings_remote_id_hint
import musicassistantclient.composeapp.generated.resources.settings_remote_id_invalid
import musicassistantclient.composeapp.generated.resources.settings_saved_connections
import musicassistantclient.composeapp.generated.resources.settings_scan_qr
import musicassistantclient.composeapp.generated.resources.settings_server
import musicassistantclient.composeapp.generated.resources.settings_server_host
import musicassistantclient.composeapp.generated.resources.settings_share_crash_logs
import musicassistantclient.composeapp.generated.resources.settings_share_logs
import musicassistantclient.composeapp.generated.resources.settings_use_tls
import musicassistantclient.composeapp.generated.resources.settings_use_tls_wss
import musicassistantclient.composeapp.generated.resources.settings_version_info
import musicassistantclient.composeapp.generated.resources.settings_webrtc_description
import musicassistantclient.composeapp.generated.resources.settings_webrtc_disclaimer
import musicassistantclient.composeapp.generated.resources.settings_webrtc_info
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.publicvalue.multiplatform.qrcode.CameraPosition
import org.publicvalue.multiplatform.qrcode.CodeType
import org.publicvalue.multiplatform.qrcode.ScannerWithPermissions
import kotlin.math.roundToInt

// Android TV: initial-focus landing for the config form. The target sits behind a tab switch and
// needs a frame to attach its FocusRequester; additionally, the app's cold start on Google TV races
// a platform focus/IME transition that clears the first grant ~150ms in (leaving the screen with no
// focused node at all, so the remote goes dead). Re-requesting over this window survives that one-
// shot race; re-requesting an already-focused target is a no-op.
private const val CONFIG_FOCUS_RETRIES = 5
private const val CONFIG_FOCUS_RETRY_DELAY = 250L

// A dialog is its own window, so once it closes the platform does not restore D-pad focus to the
// row that opened it. Wait for the window to tear down before re-requesting focus.
private const val DIALOG_CLOSE_FOCUS_DELAY = 150L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(goHome: () -> Unit, exitApp: () -> Unit) {
    val themeViewModel = koinViewModel<ThemeViewModel>()
    val theme = themeViewModel.theme.collectAsStateWithLifecycle(ThemeSetting.FollowSystem)
    val viewModel = koinViewModel<SettingsViewModel>()
    val savedConnectionInfo by viewModel.savedConnectionInfo.collectAsStateWithLifecycle()
    val sessionState by viewModel.sessionState.collectAsStateWithLifecycle()
    val connectionHistory by viewModel.connectionHistory.collectAsStateWithLifecycle()
    val dataConnection = (sessionState as? SessionState.Connected)?.dataConnectionState
    val isAuthenticated = dataConnection is DataConnectionState.Authenticated
    val sendspinEnabled by viewModel.sendspinEnabled.collectAsStateWithLifecycle()
    val sendspinUseCustomConnection by viewModel.sendspinUseCustomConnection.collectAsStateWithLifecycle()
    val hasCrashLog by viewModel.hasCrashLog.collectAsStateWithLifecycle()
    val isPreparingShare by viewModel.isPreparingShare.collectAsStateWithLifecycle()
    val preferredMethod by viewModel.preferredConnectionMethod.collectAsStateWithLifecycle()

    // Android TV: Compose's geometric focus search does not reliably move focus between siblings
    // on this hardware, so the initial-config form declares explicit directional links per target
    // via FocusProperties instead of trusting geometry. Both connection tabs stay in the graph and
    // link sideways; UP from the first field lands on the right-hand tab.
    val isTv = isTelevisionDevice()
    val configFlow = rememberTvFocusFlow()
    val isDirect = preferredMethod != "webrtc"
    val configLinks = if (isDirect) {
        buildMap {
            put("exitApp", TvFocusFlow.Links(down = "tabDirect"))
            put("tabDirect", TvFocusFlow.Links(up = "exitApp", right = "tabWebRTC", down = "host"))
            put("tabWebRTC", TvFocusFlow.Links(up = "exitApp", left = "tabDirect", down = "host"))
            put("host", TvFocusFlow.Links(up = "tabWebRTC", down = "port"))
            put("port", TvFocusFlow.Links(up = "host", down = "tls"))
            put("tls", TvFocusFlow.Links(up = "port", down = "connect"))
            put("connect", TvFocusFlow.Links(up = "tls", down = "history", right = "history"))
            put("history", TvFocusFlow.Links(up = "connect", left = "connect", down = "shareLogs"))
            put("shareLogs", TvFocusFlow.Links(up = "history"))
            if (hasCrashLog) {
                put("shareLogs", TvFocusFlow.Links(up = "history", down = "crashShare"))
                put("crashShare", TvFocusFlow.Links(up = "shareLogs", right = "crashDelete"))
                put("crashDelete", TvFocusFlow.Links(up = "shareLogs", left = "crashShare"))
            }
        }
    } else {
        buildMap {
            put("exitApp", TvFocusFlow.Links(down = "tabDirect"))
            put("tabDirect", TvFocusFlow.Links(up = "exitApp", right = "tabWebRTC", down = "remoteId"))
            put("tabWebRTC", TvFocusFlow.Links(up = "exitApp", left = "tabDirect", down = "remoteId"))
            put("remoteId", TvFocusFlow.Links(up = "tabWebRTC", down = "connect"))
            put("connect", TvFocusFlow.Links(up = "remoteId", down = "history", right = "scanQr"))
            put("scanQr", TvFocusFlow.Links(left = "connect", right = "history", down = "history"))
            put("history", TvFocusFlow.Links(up = "connect", left = "scanQr", down = "shareLogs"))
            put("shareLogs", TvFocusFlow.Links(up = "history"))
            if (hasCrashLog) {
                put("shareLogs", TvFocusFlow.Links(up = "history", down = "crashShare"))
                put("crashShare", TvFocusFlow.Links(up = "shareLogs", right = "crashDelete"))
                put("crashDelete", TvFocusFlow.Links(up = "shareLogs", left = "crashShare"))
            }
        }
    }

    // Same explicit-link strategy for the connected screen: the Server/Login/Local-player cards are
    // separate composables whose controls the geometric search on this hardware also fails to chain
    // (verified: D-pad focus gets stuck on the Codec and Enable rows and can't move up). The chain
    // follows the on-screen order and adapts to whichever controls actually exist right now (the
    // login form vs. the logged-in row; the Sendspin custom-connection fields vs. the plain toggle;
    // the Enable vs. Disable local-player button).
    val authFlow = rememberTvFocusFlow()
    val authLinks: Map<String, TvFocusFlow.Links> = when {
        sessionState is SessionState.Connected && !isAuthenticated -> buildMap {
            put("exitApp", TvFocusFlow.Links(down = "disconnect"))
            put("disconnect", TvFocusFlow.Links(up = "exitApp", down = "loginTab"))
            put("loginTab", TvFocusFlow.Links(up = "disconnect", down = "username"))
            put("username", TvFocusFlow.Links(up = "loginTab", down = "password"))
            put("password", TvFocusFlow.Links(up = "username", down = "login", right = "passwordToggle"))
            put("passwordToggle", TvFocusFlow.Links(up = "password", down = "login", left = "password"))
            put("login", TvFocusFlow.Links(up = "password", down = "shareLogs"))
            put("shareLogs", TvFocusFlow.Links(up = "login"))
            if (hasCrashLog) {
                put("shareLogs", TvFocusFlow.Links(up = "login", down = "crashShare"))
                put("crashShare", TvFocusFlow.Links(up = "shareLogs", right = "crashDelete"))
                put("crashDelete", TvFocusFlow.Links(up = "shareLogs", left = "crashShare"))
            }
        }

        sessionState is SessionState.Connected && sendspinEnabled -> buildMap {
            put("disconnect", TvFocusFlow.Links(down = "logout"))
            put("logout", TvFocusFlow.Links(up = "disconnect", down = "playerToggle"))
            put("playerToggle", TvFocusFlow.Links(up = "logout", down = "shareLogs"))
            put("shareLogs", TvFocusFlow.Links(up = "playerToggle"))
            if (hasCrashLog) {
                put("shareLogs", TvFocusFlow.Links(up = "playerToggle", down = "crashShare"))
                put("crashShare", TvFocusFlow.Links(up = "shareLogs", right = "crashDelete"))
                put("crashDelete", TvFocusFlow.Links(up = "shareLogs", left = "crashShare"))
            }
        }

        sessionState is SessionState.Connected && sendspinUseCustomConnection -> buildMap {
            put("disconnect", TvFocusFlow.Links(down = "logout"))
            put("logout", TvFocusFlow.Links(up = "disconnect", down = "playerName"))
            put("playerName", TvFocusFlow.Links(up = "logout", down = "codec"))
            put("codec", TvFocusFlow.Links(up = "playerName", down = "customToggle"))
            put("customToggle", TvFocusFlow.Links(up = "codec", down = "customHost"))
            put("customHost", TvFocusFlow.Links(up = "customToggle", down = "customPort"))
            put("customPort", TvFocusFlow.Links(up = "customHost", right = "customPath", down = "customTls"))
            put("customPath", TvFocusFlow.Links(up = "customPort", left = "customPort", down = "customTls"))
            put("customTls", TvFocusFlow.Links(up = "customPort", down = "playerToggle"))
            put("playerToggle", TvFocusFlow.Links(up = "customTls", down = "shareLogs"))
            put("shareLogs", TvFocusFlow.Links(up = "playerToggle"))
            if (hasCrashLog) {
                put("shareLogs", TvFocusFlow.Links(up = "playerToggle", down = "crashShare"))
                put("crashShare", TvFocusFlow.Links(up = "shareLogs", right = "crashDelete"))
                put("crashDelete", TvFocusFlow.Links(up = "shareLogs", left = "crashShare"))
            }
        }

        sessionState is SessionState.Connected -> buildMap {
            put("disconnect", TvFocusFlow.Links(down = "logout"))
            put("logout", TvFocusFlow.Links(up = "disconnect", down = "playerName"))
            put("playerName", TvFocusFlow.Links(up = "logout", down = "codec"))
            put("codec", TvFocusFlow.Links(up = "playerName", down = "customToggle"))
            put("customToggle", TvFocusFlow.Links(up = "codec", down = "playerToggle"))
            put("playerToggle", TvFocusFlow.Links(up = "customToggle", down = "shareLogs"))
            put("shareLogs", TvFocusFlow.Links(up = "playerToggle"))
            if (hasCrashLog) {
                put("shareLogs", TvFocusFlow.Links(up = "playerToggle", down = "crashShare"))
                put("crashShare", TvFocusFlow.Links(up = "shareLogs", right = "crashDelete"))
                put("crashDelete", TvFocusFlow.Links(up = "shareLogs", left = "crashShare"))
            }
        }

        else -> emptyMap()
    }

    // On TV, land D-pad focus on a non-text control when the form appears (or when switching
    // tabs). Landing straight on a text field makes the app request the Leanback keyboard, and on
    // Google TV that focus does not stick (the field loses focus within a frame and the platform's
    // initial-focus fallback starts fighting the field for it). A button stays focused, and the
    // explicit links below still move the remote into the fields.
    val isConnected = sessionState is SessionState.Connected
    val tvFlow = if (isConnected) authFlow else configFlow
    // Land initial focus on a non-text control. Right after a successful connect that's the auth
    // provider tab row (progress toward the next expected step, not the destructive Disconnect
    // button); when already authenticated there is no tab row, so the top of the connected form
    // (Disconnect) is the sensible entry. On the config form it's the connection-method tab row.
    val primaryTarget = when {
        !isTv -> null
        sessionState is SessionState.Connected && !isAuthenticated -> "loginTab"
        sessionState is SessionState.Connected -> "disconnect"
        sessionState is SessionState.Disconnected && !isAuthenticated -> "tabDirect"
        else -> null
    }
    LaunchedEffect(primaryTarget, tvFlow) {
        if (primaryTarget != null) {
            var attempt = 0
            while (attempt < CONFIG_FOCUS_RETRIES) {
                tvFlow.requestFocus(primaryTarget)
                attempt++
                delay(CONFIG_FOCUS_RETRY_DELAY)
            }
        }
    }

    // Self-healing D-pad: the cold-start focus race above can still leave the form without any
    // focused node (or with focus on a node outside the declared link chain, e.g. a clipped Misc
    // button), which makes the remote a dead control until something is re-focused. When a
    // directional key arrives and the current focus is not a linked target of the on-screen chain,
    // land it on the primary target and consume that press (the next press navigates). Tracked
    // per-recomposition so it only applies while a TV form is the on-screen content.
    val activeLinks = if (isConnected) authLinks else configLinks

    // Only allow back navigation when authenticated
    BackHandler(enabled = true) {
        if (isAuthenticated) {
            goHome()
        } else {
            exitApp()
        }
    }

    TopBarLayout(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.nav_settings)) },
                actions = {
                    ThemeChooser(
                        modifier = Modifier.padding(end = 16.dp),
                        currentTheme = theme.value,
                    ) { changedTheme ->
                        themeViewModel.switchTheme(changedTheme)
                    }
                },
                navigationIcon = {
                    if (isAuthenticated) {
                        IconButton(onClick = goHome) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.common_back))
                        }
                    }
                },
            )
        },
    ) {
        Column(
            modifier = Modifier
                .background(color = MaterialTheme.colorScheme.background)
                .fillMaxSize(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    // Android TV: the Compose scroll container is a focus target in this CMP
                    // version, and on Google TV it fights the D-pad for focus (the framework keeps
                    // re-granting it, wiping out the focused field within ~50ms). The settings form
                    // fits a 1080p TV screen, so skip scrolling there and keep D-pad focus stable.
                    .then(if (isTv) Modifier else Modifier.clearFocusOnScroll())
                    .onPreviewKeyEvent { event ->
                        val directional = event.type == KeyEventType.KeyDown &&
                            (
                                event.key == Key.DirectionUp || event.key == Key.DirectionDown ||
                                event.key == Key.DirectionLeft || event.key == Key.DirectionRight
                            )
                        if (directional) {
                            val focusIsOnLinkedTarget =
                                tvFlow.focusedTarget != null && activeLinks.containsKey(tvFlow.focusedTarget)
                            if (primaryTarget != null && !focusIsOnLinkedTarget) {
                                tvFlow.requestFocus(primaryTarget)
                                true
                            } else {
                                false
                            }
                        } else {
                            false
                        }
                    }
                    .then(if (isTv) Modifier else Modifier.verticalScroll(rememberScrollState())),
                verticalArrangement = Arrangement.spacedBy(if (isTv) 8.dp else 16.dp),
            ) {
                var ipAddress by remember { mutableStateOf(if (isTv) "" else Defaults.URI) }
                var port by remember { mutableStateOf(if (isTv) "" else Defaults.PORT.toString()) }
                var isTls by remember { mutableStateOf(false) }

                LaunchedEffect(savedConnectionInfo) {
                    savedConnectionInfo?.let {
                        ipAddress = it.host
                        port = it.port.toString()
                        isTls = it.isTls
                    }
                }

                // Track if we've already attempted auto-reconnect
                var autoReconnectAttempted by remember { mutableStateOf(false) }

                // Auto-reconnect on error ONLY if user hasn't changed the connection info
                // This prevents auto-reconnect to old server when user is trying to connect to new server
                // Does NOT auto-reconnect when user is using WebRTC (different failure mode)
                LaunchedEffect(sessionState) {
                    val connInfo = savedConnectionInfo
                    if (sessionState is SessionState.Disconnected.Error &&
                        connInfo != null &&
                        !autoReconnectAttempted &&
                        preferredMethod != "webrtc"
                    ) {
                        // Only auto-reconnect if text fields match saved connection info
                        // (i.e., user hasn't changed anything)
                        val userChangedConnectionInfo =
                            ipAddress != connInfo.host ||
                                    port != connInfo.port.toString() ||
                                    isTls != connInfo.isTls

                        if (!userChangedConnectionInfo) {
                            // User is trying to reconnect to same server - auto-retry
                            autoReconnectAttempted = true
                            viewModel.attemptConnection(
                                connInfo.host,
                                connInfo.port.toString(),
                                connInfo.isTls,
                            )
                        }
                        // If user changed connection info, don't auto-retry - let them manually retry
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    if (!isAuthenticated) {
                        OutlinedButton(
                            onClick = exitApp,
                            modifier = if (isTv) {
                                Modifier.tvFocus(flow = tvFlow, links = activeLinks, id = "exitApp")
                            } else {
                                Modifier
                            },
                        ) { Text(stringResource(Res.string.settings_exit_app)) }
                    }
                }

                when (sessionState) {
                    is SessionState.Disconnected -> {
                        // The settings form already needs every pixel of a 1080p TV screen
                        // (scrolling is disabled on TV to keep D-pad focus stable), so skip the
                        // informational About card there; it stays on phones which can scroll.
                        if (!isTv) {
                            AboutSection()
                        }
                        ConnectionMethodTabs(
                            viewModel = viewModel,
                            preferredMethod = preferredMethod,
                            configFlow = configFlow,
                            configLinks = configLinks,
                            ipAddress = ipAddress,
                            port = port,
                            isTls = isTls,
                            onIpAddressChange = { ipAddress = it },
                            onPortChange = { port = it },
                            onTlsChange = { isTls = it },
                            onDirectConnect = {
                                viewModel.attemptConnection(
                                    ipAddress,
                                    port,
                                    isTls,
                                )
                            },
                            directConnectEnabled = ipAddress.isValidHost() && port.isIpPort(),
                            sessionState = sessionState,
                            connectionHistory = connectionHistory,
                        )
                    }

                    SessionState.Connecting -> {
                        ConnectingSection(
                            ipAddress = ipAddress,
                            port = port,
                            preferredMethod = preferredMethod,
                            onCancel = { viewModel.disconnect() },
                        )
                    }

                    is SessionState.Reconnecting -> {
                        ConnectingSection(
                            ipAddress = ipAddress,
                            port = port,
                            preferredMethod = preferredMethod,
                            onCancel = { viewModel.disconnect() },
                        )
                    }

                    is SessionState.Connected -> {
                        val connectedState = sessionState as SessionState.Connected

                        // Server Info Section (always shown when connected)
                        ServerInfoSection(
                            connectionInfo = savedConnectionInfo,
                            serverInfo = connectedState.serverInfo,
                            isWebRTC = connectedState is SessionState.Connected.WebRTC,
                            authFlow = if (isTv) authFlow else null,
                            authLinks = authLinks,
                            onDisconnect = { viewModel.disconnect() },
                        )

                        LoginSection(
                            connectedState.user,
                            authFlow = if (isTv) authFlow else null,
                            authLinks = authLinks,
                        )

                        when (dataConnection) {
                            is DataConnectionState.Authenticated -> {
                                // State 4: Connected and authenticated

                                // Local Player Section
                                SendspinSection(
                                    viewModel = viewModel,
                                    authFlow = if (isTv) authFlow else null,
                                    authLinks = authLinks,
                                )

                                // Car options route to the local player — only meaningful when
                                // it's reachable (authenticated) and enabled.
                                if (sendspinEnabled) {
                                    CarSection()
                                }
                            }

                            else -> Unit
                        }
                    }
                }

                // Misc settings - always visible
                val shareLogsTitle = stringResource(Res.string.settings_share_logs)
                val shareCrashLogsTitle = stringResource(Res.string.settings_share_crash_logs)
                MiscSection(
                    onShareLogs = { viewModel.shareLogs(shareLogsTitle) },
                    hasCrashLog = hasCrashLog,
                    isPreparingShare = isPreparingShare,
                    onShareCrashLog = { viewModel.shareCrashLog(shareCrashLogsTitle) },
                    onDeleteCrashLog = { viewModel.deleteCrashLog() },
                    tvFlow = if (isTv) tvFlow else null,
                    tvLinks = activeLinks,
                )

                Spacer(modifier = Modifier.size(16.dp))
            }
        }
    }
}

// Section Composables

@Composable
private fun MiscSection(
    onShareLogs: () -> Unit,
    hasCrashLog: Boolean,
    isPreparingShare: Boolean,
    onShareCrashLog: () -> Unit,
    onDeleteCrashLog: () -> Unit,
    tvFlow: TvFocusFlow?,
    tvLinks: Map<String, TvFocusFlow.Links>,
) {
    SectionCard {
        SectionTitle(stringResource(Res.string.settings_misc))
        OutlinedButton(
            modifier = Modifier
                .fillMaxWidth()
                .tvFocus(flow = tvFlow, links = tvLinks, id = "shareLogs"),
            enabled = !isPreparingShare,
            onClick = onShareLogs,
        ) {
            if (isPreparingShare) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                )
                Spacer(modifier = Modifier.size(8.dp))
            }
            Text(stringResource(Res.string.settings_share_logs))
        }
        if (hasCrashLog) {
            Spacer(modifier = Modifier.size(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    modifier = Modifier
                        .weight(1f)
                        .tvFocus(flow = tvFlow, links = tvLinks, id = "crashShare"),
                    enabled = !isPreparingShare,
                    onClick = onShareCrashLog,
                ) {
                    Text(stringResource(Res.string.settings_share_crash_logs))
                }
                OutlinedButton(
                    modifier = Modifier.tvFocus(flow = tvFlow, links = tvLinks, id = "crashDelete"),
                    enabled = !isPreparingShare,
                    onClick = onDeleteCrashLog,
                ) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(Res.string.cd_delete_crash_logs))
                }
            }
        }
    }
}

@Composable
internal fun SectionCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            // Tighter padding on TV so more settings fit without scrolling (see SettingsScreen:
            // TV disables scrolling to keep D-pad focus stable).
            modifier = Modifier.padding(if (isTelevisionDevice()) 8.dp else 16.dp),
        ) {
            content()
        }
    }
}

@Composable
internal fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(bottom = if (isTelevisionDevice()) 8.dp else 12.dp),
    )
}

@Composable
private fun AboutSection() {
    val uriHandler = LocalUriHandler.current
    SectionCard {
        Text(
            text = stringResource(Res.string.settings_about_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.size(4.dp))
        Text(
            text = stringResource(Res.string.settings_about_learn_more),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable { uriHandler.openUri("https://music-assistant.io") },
        )
    }
}

@Composable
private fun ExperimentalPill() {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.primary)
            .padding(horizontal = 6.dp, vertical = 1.dp),
    ) {
        Text(
            text = stringResource(Res.string.settings_connection_experimental),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

@Composable
private fun ConnectionMethodTabs(
    viewModel: SettingsViewModel,
    preferredMethod: String?,
    configFlow: TvFocusFlow,
    configLinks: Map<String, TvFocusFlow.Links>,
    ipAddress: String,
    port: String,
    isTls: Boolean,
    onIpAddressChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onTlsChange: (Boolean) -> Unit,
    onDirectConnect: () -> Unit,
    directConnectEnabled: Boolean,
    sessionState: SessionState,
    connectionHistory: List<ConnectionHistoryEntry>,
) {
    val selectedTab = if (preferredMethod == "webrtc") 1 else 0
    val webrtcRemoteId by viewModel.webrtcRemoteId.collectAsStateWithLifecycle()
    var showHistoryDialog by remember { mutableStateOf(false) }

    val directHasToken = port.toIntOrNull()
        ?.let { viewModel.hasCredentialsForDirect(ipAddress, it, isTls) } ?: false
    val webrtcHasToken = webrtcRemoteId.isNotBlank() &&
            viewModel.hasCredentialsForWebRTC(webrtcRemoteId)

    SectionCard {
        SectionTitle(stringResource(Res.string.settings_connection_method))

        // Tabs
        PrimaryTabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { viewModel.setPreferredConnectionMethod("direct") },
                modifier = configFlow.modifierFor("tabDirect", configLinks.getValue("tabDirect"))
                    .tvFocusRing()
                    .testTag("Config-TabDirect"),
                text = { Text(stringResource(Res.string.settings_connection_direct)) },
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { viewModel.setPreferredConnectionMethod("webrtc") },
                modifier = configFlow.modifierFor("tabWebRTC", configLinks.getValue("tabWebRTC"))
                    .tvFocusRing()
                    .testTag("Config-TabWebRTC"),
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(Res.string.settings_connection_webrtc))
                        Spacer(modifier = Modifier.size(6.dp))
                        ExperimentalPill()
                    }
                },
            )
        }

        Spacer(modifier = Modifier.size(16.dp))

        // Tab content
        when (selectedTab) {
            0 -> {
                // Direct connection tab
                DirectConnectionContent(
                    configFlow = configFlow,
                    configLinks = configLinks,
                    ipAddress = ipAddress,
                    port = port,
                    isTls = isTls,
                    hasToken = directHasToken,
                    onIpAddressChange = onIpAddressChange,
                    onPortChange = onPortChange,
                    onTlsChange = onTlsChange,
                    onConnect = onDirectConnect,
                    enabled = directConnectEnabled,
                    onShowHistory = { showHistoryDialog = true },
                )
            }

            1 -> {
                // WebRTC connection tab
                WebRTCConnectionContent(
                    configFlow = configFlow,
                    configLinks = configLinks,
                    remoteId = webrtcRemoteId,
                    onRemoteIdChange = { viewModel.setWebrtcRemoteId(it.uppercase()) },
                    onConnect = { viewModel.attemptWebRTCConnection(webrtcRemoteId) },
                    sessionState = sessionState,
                    hasToken = webrtcHasToken,
                    onShowHistory = { showHistoryDialog = true },
                )
            }
        }

        val error = (sessionState as? SessionState.Disconnected.Error)?.reason
        if (error != null) {
            val errorMessage = when {
                error is ServerIdMismatchException -> Res.string.server_id_mismatch_error.toDisplayString()
                isTelevisionDevice() -> friendlyConnectionError(error)
                else -> error.message?.toDisplayString()
            }

            if (errorMessage != null) {
                Text(
                    errorMessage.string(),
                    modifier = Modifier.padding(top = 8.dp),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }

    if (showHistoryDialog) {
        ConnectionHistoryDialog(
            history = connectionHistory,
            onFill = { entry ->
                when (entry.type) {
                    ConnectionType.DIRECT -> {
                        entry.connectionInfo?.let {
                            onIpAddressChange(it.host)
                            onPortChange(it.port.toString())
                            onTlsChange(it.isTls)
                        }
                        viewModel.setPreferredConnectionMethod("direct")
                    }

                    ConnectionType.WEBRTC -> {
                        entry.remoteId?.let { viewModel.setWebrtcRemoteId(it) }
                        viewModel.setPreferredConnectionMethod("webrtc")
                    }
                }
                showHistoryDialog = false
            },
            onDelete = viewModel::removeFromHistory,
            onDismiss = { showHistoryDialog = false },
        )
    }
}

/**
 * Map a raw connect failure to wording a user can act on. Transport and watchdog
 * errors surface technical strings ("Connect timed out after 30000ms", Ktor's
 * "Connection refused" / TLS / DNS exceptions), so the settings screen shows a
 * friendly line instead. Matching is on message content only to stay
 * platform-agnostic; unknown failures fall back to a generic message.
 */
private fun friendlyConnectionError(error: Throwable): DisplayString {
    val message = error.message?.lowercase() ?: ""
    return when {
        message.contains("timed out") || message.contains("timeout") ->
            Res.string.connection_error_timeout.toDisplayString()
        message.contains("refused") || message.contains("failed to connect") ||
            message.contains("unable to connect") || message.contains("resolve host") ||
            message.contains("unknown host") || message.contains("no route to host") ->
            Res.string.connection_error_unreachable.toDisplayString()
        message.contains("ssl") || message.contains("tls") || message.contains("certificate") ||
            message.contains("trust anchor") || message.contains("cleartext") ->
            Res.string.connection_error_tls.toDisplayString()
        message.contains("webrtc") || message.contains("signaling") ||
            message.contains("remote id") || message.contains("ice") ->
            Res.string.connection_error_webrtc.toDisplayString()
        message.contains("reconnect") || message.contains("recovery machinery") ||
            message.contains("connection lost") ->
            Res.string.connection_error_lost.toDisplayString()
        else -> Res.string.connection_error_generic.toDisplayString()
    }
}

@Composable
fun DirectConnectionContent(
    configFlow: TvFocusFlow,
    configLinks: Map<String, TvFocusFlow.Links>,
    ipAddress: String,
    port: String,
    isTls: Boolean,
    hasToken: Boolean,
    onIpAddressChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onTlsChange: (Boolean) -> Unit,
    onConnect: () -> Unit,
    enabled: Boolean,
    onShowHistory: () -> Unit,
) {
    if (isTelevisionDevice()) {
        DirectConnectionContentTv(
            configFlow = configFlow,
            configLinks = configLinks,
            ipAddress = ipAddress,
            port = port,
            isTls = isTls,
            hasToken = hasToken,
            onIpAddressChange = onIpAddressChange,
            onPortChange = onPortChange,
            onTlsChange = onTlsChange,
            onConnect = onConnect,
            enabled = enabled,
            onShowHistory = onShowHistory,
        )
        return
    }

    val focusManager = LocalFocusManager.current

    // Host input
    TextField(
        modifier = configFlow.modifierFor("host", configLinks.getValue("host"), textField = true)
            .testTag("Config-Host")
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        value = ipAddress,
        onValueChange = onIpAddressChange,
        label = { Text(stringResource(Res.string.settings_server_host)) },
        placeholder = { Text(Defaults.URI) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        keyboardActions = KeyboardActions(
            onNext = { focusManager.moveFocus(FocusDirection.Down) },
        ),
        colors = TextFieldDefaults.colors(
            focusedTextColor = MaterialTheme.colorScheme.onBackground,
            unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
        ),
    )

    // Port input
    TextField(
        modifier = configFlow.modifierFor("port", configLinks.getValue("port"), textField = true)
            .testTag("Config-Port")
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        value = port,
        onValueChange = onPortChange,
        label = { Text(stringResource(Res.string.settings_port)) },
        placeholder = { Text(Defaults.PORT.toString()) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        colors = TextFieldDefaults.colors(
            focusedTextColor = MaterialTheme.colorScheme.onBackground,
            unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
        ),
    )

    // TLS toggle
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = isTls,
            onCheckedChange = onTlsChange,
            modifier = configFlow.modifierFor("tls", configLinks.getValue("tls"))
                .testTag("Config-Tls"),
        )
        Text(stringResource(Res.string.settings_use_tls))
    }

    // Connect button + history icon
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            modifier = configFlow.modifierFor("connect", configLinks.getValue("connect"))
                .testTag("Config-Connect")
                .weight(1f),
            onClick = onConnect,
            enabled = enabled,
        ) {
            Text(
                if (hasToken) {
                    stringResource(
                        Res.string.settings_connect_saved,
                    )
                } else {
                    stringResource(Res.string.settings_connect)
                },
            )
        }
        IconButton(
            onClick = onShowHistory,
            modifier = configFlow.modifierFor("history", configLinks.getValue("history"))
                .testTag("Config-History"),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.List,
                contentDescription = stringResource(Res.string.cd_connection_history),
            )
        }
    }
}

/**
 * TV variant of [DirectConnectionContent]: the host/port fields become "label + value" rows
 * ([TvPreferenceRow]) that open the full-window editor dialog ([TvTextEditorDialog]) when
 * selected, so the form needs no inline text fields and fits any TV screen without scrolling.
 */
@Composable
private fun DirectConnectionContentTv(
    configFlow: TvFocusFlow,
    configLinks: Map<String, TvFocusFlow.Links>,
    ipAddress: String,
    port: String,
    isTls: Boolean,
    hasToken: Boolean,
    onIpAddressChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onTlsChange: (Boolean) -> Unit,
    onConnect: () -> Unit,
    enabled: Boolean,
    onShowHistory: () -> Unit,
) {
    var editing by remember { mutableStateOf<String?>(null) }
    var returnTo by remember { mutableStateOf("host") }
    // Set when a dialog opens; the LaunchedEffect below then knows to re-land focus after it
    // closes, without stealing focus from the tabs on the form's first composition.
    var dialogWasOpen by remember { mutableStateOf(false) }

    if (editing != null) {
        val editor = when (editing) {
            "host" -> TvEditorSpec(
                title = stringResource(Res.string.settings_server_host),
                initialValue = ipAddress,
                keyboardType = KeyboardType.Text,
                validate = { true },
                onSave = onIpAddressChange,
            )

            "port" -> TvEditorSpec(
                title = stringResource(Res.string.settings_port),
                initialValue = port,
                keyboardType = KeyboardType.Number,
                validate = { it.isEmpty() || it.toIntOrNull() != null },
                onSave = onPortChange,
            )

            else -> null
        }
        if (editor != null) {
            dialogWasOpen = true
            TvTextEditorDialog(
                title = editor.title,
                initialValue = editor.initialValue,
                keyboardType = editor.keyboardType,
                validate = editor.validate,
                onConfirm = { value ->
                    editor.onSave(value)
                    returnTo = editing ?: "host"
                    editing = null
                },
                onDismiss = {
                    returnTo = editing ?: "host"
                    editing = null
                },
            )
        }
    }

    // The dialog is its own window, so once it closes put D-pad focus back on the row that opened
    // it (the platform does not restore it for us).
    LaunchedEffect(editing) {
        if (editing == null && dialogWasOpen) {
            dialogWasOpen = false
            delay(DIALOG_CLOSE_FOCUS_DELAY)
            configFlow.requestFocus(returnTo)
        }
    }

    TvPreferenceRow(
        label = stringResource(Res.string.settings_server_host),
        value = ipAddress.ifBlank { "homeassistant.local" },
        onClick = { editing = "host" },
        focusModifier = configFlow.modifierFor("host", configLinks.getValue("host"))
            .testTag("Config-Host"),
    )
    TvPreferenceRow(
        label = stringResource(Res.string.settings_port),
        value = port.ifBlank { "8095" },
        onClick = { editing = "port" },
        focusModifier = configFlow.modifierFor("port", configLinks.getValue("port"))
            .testTag("Config-Port"),
    )

    // TLS toggle
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = isTls,
            onCheckedChange = onTlsChange,
            modifier = configFlow.modifierFor("tls", configLinks.getValue("tls"))
                .testTag("Config-Tls"),
        )
        Text(stringResource(Res.string.settings_use_tls))
    }

    // Connect button + history icon
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            modifier = configFlow.modifierFor("connect", configLinks.getValue("connect"))
                .testTag("Config-Connect")
                .weight(1f),
            onClick = onConnect,
            enabled = enabled,
        ) {
            Text(
                if (hasToken) {
                    stringResource(Res.string.settings_connect_saved)
                } else {
                    stringResource(Res.string.settings_connect)
                },
            )
        }
        IconButton(
            onClick = onShowHistory,
            modifier = configFlow.modifierFor("history", configLinks.getValue("history"))
                .testTag("Config-History"),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.List,
                contentDescription = stringResource(Res.string.cd_connection_history),
            )
        }
    }
}

@Composable
fun WebRTCConnectionContent(
    configFlow: TvFocusFlow,
    configLinks: Map<String, TvFocusFlow.Links>,
    remoteId: String,
    onRemoteIdChange: (String) -> Unit,
    onConnect: () -> Unit,
    sessionState: SessionState,
    hasToken: Boolean,
    onShowHistory: () -> Unit,
) {
    if (isTelevisionDevice()) {
        WebRTCConnectionContentTv(
            configFlow = configFlow,
            configLinks = configLinks,
            remoteId = remoteId,
            onRemoteIdChange = onRemoteIdChange,
            onConnect = onConnect,
            sessionState = sessionState,
            hasToken = hasToken,
            onShowHistory = onShowHistory,
        )
        return
    }

    val isInvalidRemoteId = remoteId.isNotBlank() && !RemoteId.isValid(remoteId)
    val isConnected = sessionState is SessionState.Connected.WebRTC
    val isConnecting = sessionState is SessionState.Connecting
    var showQrDialog by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    Text(
        text = stringResource(Res.string.settings_webrtc_description),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 12.dp),
    )

    Text(
        text = stringResource(Res.string.settings_webrtc_disclaimer),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 12.dp),
    )

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        // Remote ID input field
        TextField(
            modifier = configFlow.modifierFor("remoteId", configLinks.getValue("remoteId"), textField = true)
                .weight(1f)
                .padding(bottom = 8.dp),
            value = remoteId,
            onValueChange = onRemoteIdChange,
            label = { Text(stringResource(Res.string.settings_remote_id)) },
            placeholder = { Text("XXXXXXXX-XXXXX-XXXXX-XXXXXXXX") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = {
                    focusManager.clearFocus()
                    if (remoteId.isNotBlank() && !isInvalidRemoteId &&
                        !isConnected && !isConnecting
                    ) {
                        onConnect()
                    }
                },
            ),
            colors = TextFieldDefaults.colors(
                focusedTextColor = MaterialTheme.colorScheme.onBackground,
                unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
            ),
            supportingText = {
                Text(
                    text = stringResource(Res.string.settings_remote_id_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            isError = isInvalidRemoteId,
        )

        if (hasCamera()) {
            IconButton(onClick = { showQrDialog = true }) {
                Icon(
                    imageVector = Icons.Default.QrCodeScanner,
                    contentDescription = stringResource(Res.string.cd_scan_qr_code),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }

    // Validation message
    if (isInvalidRemoteId) {
        Text(
            text = stringResource(Res.string.settings_remote_id_invalid),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(bottom = 8.dp),
        )
    }

    // Info text about WebRTC
    Text(
        text = stringResource(Res.string.settings_webrtc_info),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        modifier = Modifier.padding(bottom = 12.dp),
    )

    // Connect button + history icon
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            modifier = configFlow.modifierFor("connect", configLinks.getValue("connect"))
                .testTag("Config-Connect")
                .weight(1f),
            onClick = onConnect,
            enabled = remoteId.isNotBlank() && !isInvalidRemoteId && !isConnected && !isConnecting,
        ) {
            Text(
                when {
                    isConnected -> stringResource(Res.string.settings_connected)
                    isConnecting -> stringResource(Res.string.settings_connecting)
                    hasToken -> stringResource(Res.string.settings_connect_saved)
                    else -> stringResource(Res.string.settings_connect_webrtc)
                },
            )
        }
        IconButton(
            onClick = onShowHistory,
            modifier = configFlow.modifierFor("history", configLinks.getValue("history"))
                .testTag("Config-History"),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.List,
                contentDescription = stringResource(Res.string.cd_connection_history),
            )
        }
    }

    if (showQrDialog) {
        QrScanDialog(
            onDismiss = { showQrDialog = false },
            onScanned = { scannedText ->
                onRemoteIdChange(
                    (scannedText.indexOf(WEB_RTC_URL_PREFIX) + WEB_RTC_URL_PREFIX.length)
                        .takeIf { it < scannedText.length }
                        ?.let { scannedText.substring(it) }
                        ?: scannedText,
                )
                showQrDialog = false
            },
        )
    }
}

/**
 * TV variant of [WebRTCConnectionContent]: the remote-ID field becomes a [TvPreferenceRow] that
 * opens the full-window editor dialog ([TvTextEditorDialog]) when selected, so the tab needs no
 * inline text field and fits any TV screen without scrolling.
 */
@Composable
private fun WebRTCConnectionContentTv(
    configFlow: TvFocusFlow,
    configLinks: Map<String, TvFocusFlow.Links>,
    remoteId: String,
    onRemoteIdChange: (String) -> Unit,
    onConnect: () -> Unit,
    sessionState: SessionState,
    hasToken: Boolean,
    onShowHistory: () -> Unit,
) {
    val isInvalidRemoteId = remoteId.isNotBlank() && !RemoteId.isValid(remoteId)
    val isConnected = sessionState is SessionState.Connected.WebRTC
    val isConnecting = sessionState is SessionState.Connecting
    var showQrDialog by remember { mutableStateOf(false) }

    var editing by remember { mutableStateOf<String?>(null) }
    var dialogWasOpen by remember { mutableStateOf(false) }

    if (editing != null) {
        dialogWasOpen = true
        TvTextEditorDialog(
            title = stringResource(Res.string.settings_remote_id),
            initialValue = remoteId,
            keyboardType = KeyboardType.Text,
            validate = { true },
            onConfirm = { value ->
                onRemoteIdChange(value)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }

    LaunchedEffect(editing) {
        if (editing == null && dialogWasOpen) {
            dialogWasOpen = false
            delay(DIALOG_CLOSE_FOCUS_DELAY)
            configFlow.requestFocus("remoteId")
        }
    }

    TvPreferenceRow(
        label = stringResource(Res.string.settings_remote_id),
        value = remoteId.ifBlank { "XXXXXXXX-XXXXX-XXXXX-XXXXXXXX" },
        onClick = { editing = "remoteId" },
        focusModifier = configFlow.modifierFor("remoteId", configLinks.getValue("remoteId"))
            .testTag("Config-RemoteId"),
    )
    if (isInvalidRemoteId) {
        Text(
            text = stringResource(Res.string.settings_remote_id_invalid),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(bottom = 8.dp),
        )
    }

    // Connect button + history icon
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            modifier = configFlow.modifierFor("connect", configLinks.getValue("connect"))
                .testTag("Config-Connect")
                .weight(1f),
            onClick = onConnect,
            enabled = remoteId.isNotBlank() && !isInvalidRemoteId && !isConnected && !isConnecting,
        ) {
            Text(
                when {
                    isConnected -> stringResource(Res.string.settings_connected)
                    isConnecting -> stringResource(Res.string.settings_connecting)
                    hasToken -> stringResource(Res.string.settings_connect_saved)
                    else -> stringResource(Res.string.settings_connect_webrtc)
                },
            )
        }
        if (hasCamera()) {
            IconButton(
                onClick = { showQrDialog = true },
                modifier = configFlow.modifierFor("scanQr", configLinks["scanQr"] ?: TvFocusFlow.Links()),
            ) {
                Icon(
                    imageVector = Icons.Default.QrCodeScanner,
                    contentDescription = stringResource(Res.string.cd_scan_qr_code),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        IconButton(
            onClick = onShowHistory,
            modifier = configFlow.modifierFor("history", configLinks.getValue("history"))
                .testTag("Config-History"),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.List,
                contentDescription = stringResource(Res.string.cd_connection_history),
            )
        }
    }

    if (showQrDialog) {
        QrScanDialog(
            onDismiss = { showQrDialog = false },
            onScanned = { scannedText ->
                onRemoteIdChange(
                    (scannedText.indexOf(WEB_RTC_URL_PREFIX) + WEB_RTC_URL_PREFIX.length)
                        .takeIf { it < scannedText.length }
                        ?.let { scannedText.substring(it) }
                        ?: scannedText,
                )
                showQrDialog = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QrScanDialog(
    onDismiss: () -> Unit,
    onScanned: (String) -> Unit,
) {
    BasicAlertDialog(
        onDismissRequest = onDismiss,
        content = {
            Column(
                modifier = Modifier.fillMaxWidth().wrapContentHeight()
                    .background(
                        MaterialTheme.colorScheme.surfaceContainer,
                        RoundedCornerShape(corner = CornerSize(12.dp)),
                    )
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    style = MaterialTheme.typography.bodyLarge,
                    text = stringResource(Res.string.settings_scan_qr),
                )
                ScannerWithPermissions(
                    modifier = Modifier.heightIn(120.dp, 360.dp),
                    onScanned = { text ->
                        onScanned(text)
                        true // return true to disable the scanner
                    },
                    types = listOf(CodeType.QR),
                    cameraPosition = CameraPosition.BACK,
                    enableTorch = false,
                )
                OutlinedButton(
                    modifier = Modifier.align(Alignment.End),
                    onClick = onDismiss,
                ) {
                    Text(stringResource(Res.string.common_cancel))
                }
            }
        },
    )
}

@Composable
private fun ConnectingSection(
    ipAddress: String,
    port: String,
    preferredMethod: String?,
    onCancel: () -> Unit,
) {
    val text = if (preferredMethod == "webrtc") {
        stringResource(Res.string.settings_connecting_remote)
    } else {
        stringResource(Res.string.settings_connecting_to, ipAddress, port)
    }
    SectionCard {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(Res.string.common_cancel))
            }
        }
    }
}

@Composable
private fun ServerInfoSection(
    connectionInfo: ConnectionInfo?,
    serverInfo: ServerInfo?,
    isWebRTC: Boolean = false,
    authFlow: TvFocusFlow? = null,
    authLinks: Map<String, TvFocusFlow.Links> = emptyMap(),
    onDisconnect: () -> Unit,
) {
    SectionCard {
        SectionTitle(stringResource(Res.string.settings_server))

        val connectionText = if (isWebRTC) {
            stringResource(Res.string.settings_connected_webrtc)
        } else {
            connectionInfo?.let { stringResource(Res.string.settings_connected_to, it.host, it.port) }
        }
        connectionText?.let { text ->
            if (isTelevisionDevice()) {
                // TV: no scrolling, so condense the status card to a single row and leave the
                // vertical space for the login / local-player forms below.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                    )
                    OutlinedButton(
                        modifier = Modifier.tvFocus(authFlow, authLinks, "disconnect"),
                        onClick = onDisconnect,
                    ) {
                        Text(stringResource(Res.string.settings_disconnect))
                    }
                }
            } else {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(bottom = 8.dp),
                )

                serverInfo?.let { server ->
                    Text(
                        text = stringResource(
                            Res.string.settings_version_info,
                            server.serverVersion ?: "",
                            server.schemaVersion?.toString().orEmpty(),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }

                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onDisconnect,
                ) {
                    Text(stringResource(Res.string.settings_disconnect))
                }
            }
        }
    }
}

@Composable
private fun LoginSection(
    user: User?,
    authFlow: TvFocusFlow? = null,
    authLinks: Map<String, TvFocusFlow.Links> = emptyMap(),
) {
    SectionCard {
        SectionTitle(stringResource(Res.string.auth_title))

        AuthenticationPanel(
            modifier = Modifier.fillMaxWidth(),
            user = user,
            authFlow = authFlow,
            authLinks = authLinks,
        )
    }
}

@Composable
private fun SendspinSection(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel,
    authFlow: TvFocusFlow? = null,
    authLinks: Map<String, TvFocusFlow.Links> = emptyMap(),
) {
    val sendspinEnabled by viewModel.sendspinEnabled.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val sendspinDeviceName by viewModel.sendspinDeviceName.collectAsStateWithLifecycle()
    val sendspinUseCustomConnection by viewModel.sendspinUseCustomConnection.collectAsStateWithLifecycle()
    val sendspinPort by viewModel.sendspinPort.collectAsStateWithLifecycle()
    val sendspinPath by viewModel.sendspinPath.collectAsStateWithLifecycle()
    val sendspinCodecPreference by viewModel.sendspinCodecPreference.collectAsStateWithLifecycle()

    SectionCard(modifier = modifier) {
        SectionTitle(
            if (sendspinEnabled) {
                stringResource(
                    Res.string.settings_local_player_enabled,
                )
            } else {
                stringResource(Res.string.settings_local_player_disabled)
            },
        )

        if (isTelevisionDevice() && authFlow != null) {
            SendspinSectionTv(
                viewModel = viewModel,
                authFlow = authFlow,
                authLinks = authLinks,
                sendspinEnabled = sendspinEnabled,
                sendspinDeviceName = sendspinDeviceName,
                sendspinUseCustomConnection = sendspinUseCustomConnection,
                sendspinPort = sendspinPort,
                sendspinPath = sendspinPath,
                sendspinCodecPreference = sendspinCodecPreference,
            )
        } else {
            // Text fields on top - disabled when player is running
            TextField(
                modifier = Modifier
                    .tvFocus(authFlow, authLinks, "playerName")
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                value = sendspinDeviceName,
                onValueChange = { viewModel.setSendspinDeviceName(it) },
                label = { Text(stringResource(Res.string.settings_player_name)) },
                singleLine = true,
                enabled = !sendspinEnabled,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                colors = TextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onBackground,
                    unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                    disabledTextColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                ),
            )

            // Codec selection
            OverflowMenuButton(
                options = Codecs.list.map { item ->
                    OverflowMenuOption(
                        title = item.localizedTitle(),
                    ) { viewModel.setSendspinCodecPreference(item) }
                },
                buttonContent = { onClick ->
                    Row(
                        modifier = Modifier
                            .tvFocus(authFlow, authLinks, "codec")
                            .fillMaxWidth()
                            .clickable(enabled = !sendspinEnabled) { onClick() }
                            .padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(Res.string.settings_codec_preference),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = sendspinCodecPreference.localizedTitle(),
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (sendspinEnabled) {
                                    MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                                } else {
                                    MaterialTheme.colorScheme.onBackground
                                },
                            )
                        }
                        Icon(
                            modifier = Modifier.size(24.dp),
                            imageVector = Icons.Default.ExpandMore,
                            contentDescription = stringResource(Res.string.cd_select_codec),
                            tint = if (sendspinEnabled) {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                },
            )

            // Buffer size (advertised buffer_capacity in MB). Connect-time config, so locked while
            // the local player is running — takes effect on the next connect.
            val sendspinBufferCapacityMb by viewModel.sendspinBufferCapacityMb.collectAsStateWithLifecycle()
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(Res.string.settings_buffer_size),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "$sendspinBufferCapacityMb MB",
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (sendspinEnabled) {
                            MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        } else {
                            MaterialTheme.colorScheme.onBackground
                        },
                    )
                }
                Slider(
                    value = sendspinBufferCapacityMb.toFloat(),
                    onValueChange = { viewModel.setSendspinBufferCapacityMb(it.roundToInt()) },
                    valueRange = SendspinConfig.BUFFER_MB_MIN.toFloat()..SendspinConfig.BUFFER_MB_MAX.toFloat(),
                    steps = (SendspinConfig.BUFFER_MB_MAX - SendspinConfig.BUFFER_MB_MIN) /
                        SendspinConfig.BUFFER_MB_STEP - 1,
                    enabled = !sendspinEnabled,
                )
            }

            // Custom connection toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = sendspinUseCustomConnection,
                    onCheckedChange = { viewModel.setSendspinUseCustomConnection(it) },
                    enabled = !sendspinEnabled,
                    modifier = Modifier.tvFocus(authFlow, authLinks, "customToggle"),
                )
                Text(
                    text = stringResource(Res.string.settings_custom_sendspin),
                    color = if (sendspinEnabled) {
                        MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    } else {
                        MaterialTheme.colorScheme.onBackground
                    },
                )
            }

            // Connection fields (only shown when using custom connection)
            if (sendspinUseCustomConnection) {
                val sendspinHost by viewModel.sendspinHost.collectAsStateWithLifecycle()
                val sendspinUseTls by viewModel.sendspinUseTls.collectAsStateWithLifecycle()

                TextField(
                    modifier = Modifier
                        .tvFocus(authFlow, authLinks, "customHost")
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    value = sendspinHost,
                    onValueChange = { viewModel.setSendspinHost(it) },
                    label = { Text(stringResource(Res.string.settings_host)) },
                    singleLine = true,
                    enabled = !sendspinEnabled,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) },
                    ),
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onBackground,
                        unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                        disabledTextColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    ),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TextField(
                        modifier = Modifier
                            .tvFocus(authFlow, authLinks, "customPort")
                            .weight(1f)
                            .padding(bottom = 12.dp),
                        value = sendspinPort.toString(),
                        onValueChange = {
                            it.toIntOrNull()?.let { port -> viewModel.setSendspinPort(port) }
                        },
                        label = { Text(stringResource(Res.string.settings_port_default)) },
                        singleLine = true,
                        enabled = !sendspinEnabled,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Next,
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Next) },
                        ),
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onBackground,
                            unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                            disabledTextColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        ),
                    )

                    TextField(
                        modifier = Modifier
                            .tvFocus(authFlow, authLinks, "customPath")
                            .weight(1f)
                            .padding(bottom = 12.dp),
                        value = sendspinPath,
                        onValueChange = { viewModel.setSendspinPath(it) },
                        label = { Text(stringResource(Res.string.settings_path)) },
                        singleLine = true,
                        enabled = !sendspinEnabled,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onBackground,
                            unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                            disabledTextColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        ),
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = sendspinUseTls,
                        onCheckedChange = { viewModel.setSendspinUseTls(it) },
                        enabled = !sendspinEnabled,
                        modifier = Modifier.tvFocus(authFlow, authLinks, "customTls"),
                    )
                    Text(
                        text = stringResource(Res.string.settings_use_tls_wss),
                        color = if (sendspinEnabled) {
                            MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        } else {
                            MaterialTheme.colorScheme.onBackground
                        },
                    )
                }
            }

            // Toggle button on the bottom
            if (sendspinEnabled) {
                OutlinedButton(
                    modifier = Modifier
                        .tvFocus(authFlow, authLinks, "playerToggle")
                        .fillMaxWidth(),
                    onClick = { viewModel.setSendspinEnabled(false) },
                ) {
                    Text(stringResource(Res.string.settings_disable_local_player))
                }
            } else {
                Button(
                    modifier = Modifier
                        .tvFocus(authFlow, authLinks, "playerToggle")
                        .fillMaxWidth(),
                    onClick = { viewModel.setSendspinEnabled(true) },
                ) {
                    Text(stringResource(Res.string.settings_enable_local_player))
                }
            }
        }
    }
}

/**
 * Android TV version of the local-player card: rows instead of inline text fields. Each text value
 * opens [TvTextEditorDialog] on select, so the card stays short enough to fit a 1080p screen even
 * with the custom-connection fields enabled (inline fields overflowed, and TV can't scroll).
 */
@Composable
private fun SendspinSectionTv(
    viewModel: SettingsViewModel,
    authFlow: TvFocusFlow,
    authLinks: Map<String, TvFocusFlow.Links>,
    sendspinEnabled: Boolean,
    sendspinDeviceName: String,
    sendspinUseCustomConnection: Boolean,
    sendspinPort: Int,
    sendspinPath: String,
    sendspinCodecPreference: Codec,
) {
    val sendspinHost by viewModel.sendspinHost.collectAsStateWithLifecycle()
    val sendspinUseTls by viewModel.sendspinUseTls.collectAsStateWithLifecycle()

    var editing by remember { mutableStateOf<String?>(null) }
    var returnTo by remember { mutableStateOf("playerName") }

    if (editing != null) {
        val editor = when (editing) {
            "playerName" -> TvEditorSpec(
                title = stringResource(Res.string.settings_player_name),
                initialValue = sendspinDeviceName,
                keyboardType = KeyboardType.Text,
                validate = { true },
                onSave = { viewModel.setSendspinDeviceName(it) },
            )
            "customHost" -> TvEditorSpec(
                title = stringResource(Res.string.settings_host),
                initialValue = sendspinHost,
                keyboardType = KeyboardType.Text,
                validate = { true },
                onSave = { viewModel.setSendspinHost(it) },
            )
            "customPort" -> TvEditorSpec(
                title = stringResource(Res.string.settings_port),
                initialValue = sendspinPort.toString(),
                keyboardType = KeyboardType.Number,
                validate = { it.toIntOrNull() != null },
                onSave = { it.toIntOrNull()?.let { port -> viewModel.setSendspinPort(port) } },
            )
            "customPath" -> TvEditorSpec(
                title = stringResource(Res.string.settings_path),
                initialValue = sendspinPath,
                keyboardType = KeyboardType.Text,
                validate = { true },
                onSave = { viewModel.setSendspinPath(it) },
            )
            else -> null
        }
        if (editor != null) {
            TvTextEditorDialog(
                title = editor.title,
                initialValue = editor.initialValue,
                keyboardType = editor.keyboardType,
                validate = editor.validate,
                onConfirm = { value ->
                    editor.onSave(value)
                    returnTo = editing ?: "playerName"
                    editing = null
                },
                onDismiss = {
                    returnTo = editing ?: "playerName"
                    editing = null
                },
            )
        }
    }

    // The dialog is its own window, so once it closes put D-pad focus back on the row that
    // opened it (the platform does not restore it for us).
    LaunchedEffect(editing) {
        if (editing == null) {
            delay(DIALOG_CLOSE_FOCUS_DELAY)
            authFlow.requestFocus(returnTo)
        }
    }

    TvPreferenceRow(
        label = stringResource(Res.string.settings_player_name),
        value = sendspinDeviceName,
        onClick = if (sendspinEnabled) null else ({ editing = "playerName" }),
        focusModifier = Modifier.tvFocus(authFlow, authLinks, "playerName"),
    )

    // Codec selection
    OverflowMenuButton(
        options = Codecs.list.map { item ->
            OverflowMenuOption(
                title = item.localizedTitle(),
            ) { viewModel.setSendspinCodecPreference(item) }
        },
        buttonContent = { onClick ->
            Row(
                modifier = Modifier
                    .tvFocus(authFlow, authLinks, "codec")
                    .fillMaxWidth()
                    .clickable(enabled = !sendspinEnabled) { onClick() }
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(Res.string.settings_codec_preference),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = sendspinCodecPreference.localizedTitle(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (sendspinEnabled) {
                            MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        } else {
                            MaterialTheme.colorScheme.onBackground
                        },
                    )
                }
                Icon(
                    modifier = Modifier.size(24.dp),
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = stringResource(Res.string.cd_select_codec),
                    tint = if (sendspinEnabled) {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        },
    )

    // Custom connection toggle
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = sendspinUseCustomConnection,
            onCheckedChange = { viewModel.setSendspinUseCustomConnection(it) },
            enabled = !sendspinEnabled,
            modifier = Modifier.tvFocus(authFlow, authLinks, "customToggle"),
        )
        Text(
            text = stringResource(Res.string.settings_custom_sendspin),
            color = if (sendspinEnabled) {
                MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            } else {
                MaterialTheme.colorScheme.onBackground
            },
        )
    }

    // Connection rows (only shown when using custom connection)
    if (sendspinUseCustomConnection) {
        TvPreferenceRow(
            label = stringResource(Res.string.settings_host),
            value = sendspinHost,
            onClick = if (sendspinEnabled) null else ({ editing = "customHost" }),
            focusModifier = Modifier.tvFocus(authFlow, authLinks, "customHost"),
        )
        TvPreferenceRow(
            label = stringResource(Res.string.settings_port),
            value = sendspinPort.toString(),
            onClick = if (sendspinEnabled) null else ({ editing = "customPort" }),
            focusModifier = Modifier.tvFocus(authFlow, authLinks, "customPort"),
        )
        TvPreferenceRow(
            label = stringResource(Res.string.settings_path),
            value = sendspinPath,
            onClick = if (sendspinEnabled) null else ({ editing = "customPath" }),
            focusModifier = Modifier.tvFocus(authFlow, authLinks, "customPath"),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = sendspinUseTls,
                onCheckedChange = { viewModel.setSendspinUseTls(it) },
                enabled = !sendspinEnabled,
                modifier = Modifier.tvFocus(authFlow, authLinks, "customTls"),
            )
            Text(
                text = stringResource(Res.string.settings_use_tls_wss),
                color = if (sendspinEnabled) {
                    MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                } else {
                    MaterialTheme.colorScheme.onBackground
                },
            )
        }
    }

    // Toggle button on the bottom
    if (sendspinEnabled) {
        OutlinedButton(
            modifier = Modifier
                .tvFocus(authFlow, authLinks, "playerToggle")
                .fillMaxWidth(),
            onClick = { viewModel.setSendspinEnabled(false) },
        ) {
            Text(stringResource(Res.string.settings_disable_local_player))
        }
    } else {
        Button(
            modifier = Modifier
                .tvFocus(authFlow, authLinks, "playerToggle")
                .fillMaxWidth(),
            onClick = { viewModel.setSendspinEnabled(true) },
        ) {
            Text(stringResource(Res.string.settings_enable_local_player))
        }
    }
}

private data class TvEditorSpec(
    val title: String,
    val initialValue: String,
    val keyboardType: KeyboardType,
    val validate: (String) -> Boolean,
    val onSave: (String) -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConnectionHistoryDialog(
    history: List<ConnectionHistoryEntry>,
    onFill: (ConnectionHistoryEntry) -> Unit,
    onDelete: (ConnectionHistoryEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(12.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(Res.string.settings_saved_connections), style = MaterialTheme.typography.titleMedium)
            if (history.isEmpty()) {
                Text(
                    stringResource(Res.string.settings_no_saved_connections),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                history.forEach { entry ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        ),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onFill(entry) }
                                    .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                            ) {
                                Text(
                                    text = entry.displayAddress,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = when (entry.type) {
                                        ConnectionType.DIRECT -> stringResource(Res.string.settings_history_direct)
                                        ConnectionType.WEBRTC -> stringResource(Res.string.settings_history_webrtc)
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { onDelete(entry) }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = stringResource(Res.string.common_delete),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
            OutlinedButton(modifier = Modifier.align(Alignment.End), onClick = onDismiss) {
                Text(stringResource(Res.string.common_cancel))
            }
        }
    }
}

const val WEB_RTC_URL_PREFIX = "https://app.music-assistant.io/?remote_id="
