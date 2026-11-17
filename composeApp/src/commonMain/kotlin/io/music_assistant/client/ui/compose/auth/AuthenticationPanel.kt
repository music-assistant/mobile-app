package io.music_assistant.client.ui.compose.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.touchlab.kermit.Logger
import io.music_assistant.client.auth.AuthState
import io.music_assistant.client.data.model.server.AuthProvider
import io.music_assistant.client.data.model.server.User
import io.music_assistant.client.ui.compose.common.TvFocusFlow
import io.music_assistant.client.ui.compose.common.TvTextInputGuard
import io.music_assistant.client.ui.compose.common.tvFocus
import io.music_assistant.client.ui.compose.common.tvFocusRing
import io.music_assistant.client.ui.compose.common.tvSelectToEdit
import io.music_assistant.client.utils.isTelevisionDevice
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.auth_authenticating
import musicassistantclient.composeapp.generated.resources.auth_authorize_ha
import musicassistantclient.composeapp.generated.resources.auth_hide_password
import musicassistantclient.composeapp.generated.resources.auth_hide_token
import musicassistantclient.composeapp.generated.resources.auth_loading_providers
import musicassistantclient.composeapp.generated.resources.auth_logged_in_as
import musicassistantclient.composeapp.generated.resources.auth_login
import musicassistantclient.composeapp.generated.resources.auth_logout
import musicassistantclient.composeapp.generated.resources.auth_password
import musicassistantclient.composeapp.generated.resources.auth_retry_providers
import musicassistantclient.composeapp.generated.resources.auth_show_password
import musicassistantclient.composeapp.generated.resources.auth_show_token
import musicassistantclient.composeapp.generated.resources.auth_token
import musicassistantclient.composeapp.generated.resources.auth_token_hint
import musicassistantclient.composeapp.generated.resources.auth_use_password
import musicassistantclient.composeapp.generated.resources.auth_use_token
import musicassistantclient.composeapp.generated.resources.auth_username
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun AuthenticationPanel(
    viewModel: AuthenticationViewModel = koinViewModel(),
    modifier: Modifier = Modifier,
    user: User?,
    authFlow: TvFocusFlow? = null,
    authLinks: Map<String, TvFocusFlow.Links> = emptyMap(),
) {
    val providers by viewModel.providers.collectAsStateWithLifecycle()
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    // Shown while the error is the current state. A retry of the provider load, a new
    // login attempt and a reconnect all replace it.
    val loginError = (authState as? AuthState.Error)?.message
    var selectedTab by remember(key1 = providers) { mutableIntStateOf(0) }

    Column(modifier = modifier.fillMaxWidth()) {
        user?.let { u ->
            if (isTelevisionDevice()) {
                // TV: no scrolling, so condense the logged-in status to a single row and leave
                // vertical space for the local-player form below.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(Res.string.auth_logged_in_as, u.description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                    )
                    OutlinedButton(
                        modifier = Modifier.tvFocus(authFlow, authLinks, "logout"),
                        onClick = { viewModel.logout() },
                    ) {
                        Text(stringResource(Res.string.auth_logout))
                    }
                }
            } else {
                Text(
                    text = stringResource(Res.string.auth_logged_in_as, u.description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(bottom = 12.dp),
                )

                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { viewModel.logout() },
                ) {
                    Text(stringResource(Res.string.auth_logout))
                }
            }
        } ?: run {
            // Show provider selection and auth UI
            if (providers.isNotEmpty()) {
                // Provider tabs
                PrimaryTabRow(
                    selectedTabIndex = selectedTab,
                ) {
                    providers.forEachIndexed { index, provider ->
                        when (provider.type) {
                            "builtin" -> Tab(
                                selected = index == selectedTab,
                                onClick = { selectedTab = index },
                                modifier = Modifier
                                    .tvFocus(authFlow, authLinks, "loginTab")
                                    .tvFocusRing(),
                                text = {
                                    Text("Music Assistant")
                                },
                            )

                            "homeassistant" -> Tab(
                                selected = index == selectedTab,
                                onClick = { selectedTab = index },
                                modifier = Modifier.tvFocusRing(),
                                text = {
                                    Text("Home Assistant")
                                },
                            )

                            else -> Unit
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Show provider-specific UI
                providers.getOrNull(selectedTab)?.let { provider ->
                    when (provider.type) {
                        "builtin" -> BuiltinAuthForm(
                            viewModel,
                            provider,
                            authFlow,
                            authLinks,
                            loginError = loginError,
                        )
                        "homeassistant" -> Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { viewModel.login(provider) },
                            enabled = authState !is AuthState.Loading,
                        ) {
                            Text(stringResource(Res.string.auth_authorize_ha))
                        }

                        else -> Unit
                    }
                }
            } else {
                // No providers loaded yet - show loading or retry
                Text(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    text = stringResource(Res.string.auth_loading_providers),
                    textAlign = TextAlign.Center,
                )
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { viewModel.loadProviders() },
                ) {
                    Text(stringResource(Res.string.auth_retry_providers))
                }
            }

            // Show loading state
            if (authState is AuthState.Loading) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(Res.string.auth_authenticating),
                    textAlign = TextAlign.Center,
                )
            }
        }

        // The builtin form renders its own login error between the password field and the Login
        // button; other providers (and phone layouts) fall back to this spot. On the non-scrolling
        // TV layout the bottom edge of the card clips anything rendered last in this Column, so the
        // builtin error must stay up in the form where it is visible.
        if (loginError != null && providers.getOrNull(selectedTab)?.type != "builtin") {
            Logger.e("Error $loginError")
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                text = loginError,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** The Music Assistant provider signs in with a username and a password, or with a long-lived token. */
@Composable
private fun BuiltinAuthForm(
    viewModel: AuthenticationViewModel,
    provider: AuthProvider,
    authFlow: TvFocusFlow?,
    authLinks: Map<String, TvFocusFlow.Links>,
    loginError: String?,
) {
    val username by viewModel.username.collectAsStateWithLifecycle()
    val password by viewModel.password.collectAsStateWithLifecycle()
    val token by viewModel.token.collectAsStateWithLifecycle()
    var useToken by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val canLogin = if (useToken) token.isNotBlank() else username.isNotEmpty() && password.isNotEmpty()
    val login = {
        if (useToken) viewModel.loginWithToken() else viewModel.login(provider)
    }
    val onDone = {
        focusManager.clearFocus()
        if (canLogin) {
            login()
        }
    }
    val tv = isTelevisionDevice()
    // TV only: the input method stays closed while the D-pad traverses the fields and opens on an
    // explicit CENTER/ENTER press (see TvTextInput.kt). On phones the guard is skipped entirely and
    // tapping a field opens the keyboard as usual.
    val usernameEditing = remember { mutableStateOf(false) }
    val passwordEditing = remember { mutableStateOf(false) }
    val tokenEditing = remember { mutableStateOf(false) }

    TvTextInputGuard(
        enabled = tv,
        editing = usernameEditing.value || passwordEditing.value || tokenEditing.value,
    ) {
        Column {
            if (useToken) {
                SecretTextField(
                    modifier = Modifier
                        .tvFocus(authFlow, authLinks, "token", textField = true)
                        .tvSelectToEdit(tokenEditing),
                    value = token,
                    onValueChange = { viewModel.token.value = it },
                    label = stringResource(Res.string.auth_token),
                    showDescription = stringResource(Res.string.auth_show_token),
                    hideDescription = stringResource(Res.string.auth_hide_token),
                    onDone = onDone,
                )
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(Res.string.auth_token_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                )
            } else {
                TextField(
                    modifier = Modifier
                        .tvFocus(authFlow, authLinks, "username", textField = true)
                        .tvSelectToEdit(usernameEditing)
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    value = username,
                    onValueChange = { viewModel.username.value = it },
                    label = { Text(stringResource(Res.string.auth_username)) },
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
                SecretTextField(
                    modifier = Modifier
                        .tvFocus(authFlow, authLinks, "password", textField = true)
                        .tvSelectToEdit(passwordEditing),
                    value = password,
                    onValueChange = { viewModel.password.value = it },
                    label = stringResource(Res.string.auth_password),
                    showDescription = stringResource(Res.string.auth_show_password),
                    hideDescription = stringResource(Res.string.auth_hide_password),
                    onDone = onDone,
                )
            }

            TextButton(
                modifier = Modifier
                    .tvFocus(authFlow, authLinks, "useToken")
                    .fillMaxWidth(),
                onClick = { useToken = !useToken },
                contentPadding = PaddingValues(0.dp),
            ) {
                Text(
                    stringResource(
                        if (useToken) Res.string.auth_use_password else Res.string.auth_use_token,
                    ),
                )
            }

            // Inline, above the button: the non-scrolling TV layout clips anything appended after
            // the form, so a login failure rendered below the button would be invisible.
            loginError?.let {
                Logger.e("Error $it")
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    text = it,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            }

            Button(
                modifier = Modifier
                    .tvFocus(authFlow, authLinks, "login")
                    .fillMaxWidth(),
                onClick = login,
                enabled = canLogin,
            ) {
                Text(stringResource(Res.string.auth_login))
            }
        }
    }
}

/** A single-line field for a secret. The text is hidden until the user taps the eye icon. */
@Composable
private fun SecretTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    showDescription: String,
    hideDescription: String,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isVisible by remember { mutableStateOf(false) }

    TextField(
        modifier = modifier.fillMaxWidth().padding(bottom = 16.dp),
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        visualTransformation = if (isVisible) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        trailingIcon = {
            IconButton(onClick = { isVisible = !isVisible }) {
                Icon(
                    imageVector = if (isVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = if (isVisible) hideDescription else showDescription,
                )
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        colors = TextFieldDefaults.colors(
            focusedTextColor = MaterialTheme.colorScheme.onBackground,
            unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
        ),
    )
}
