package io.music_assistant.client.ui.compose.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import musicassistantclient.composeapp.generated.resources.*
import musicassistantclient.composeapp.generated.resources.Res
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun AuthenticationPanel(
    viewModel: AuthenticationViewModel = koinViewModel(),
    modifier: Modifier = Modifier,
    user: User?,
) {
    val providers by viewModel.providers.collectAsStateWithLifecycle()
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    var loginError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(authState) {
        if (authState is AuthState.Error) {
            loginError = (authState as? AuthState.Error)?.message
        } else if (authState is AuthState.Authenticated) {
            loginError = null
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        user?.let {
            Text(
                text = stringResource(Res.string.auth_logged_in_as, user.description),
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
        } ?: run {
            // Show provider selection and auth UI
            if (providers.isNotEmpty()) {
                var selectedTab by remember(key1 = providers) { mutableIntStateOf(0) }
                // Provider tabs
                PrimaryTabRow(
                    selectedTabIndex = selectedTab,
                ) {
                    providers.forEachIndexed { index, provider ->
                        when (provider.type) {
                            "builtin" -> Tab(
                                selected = index == selectedTab,
                                onClick = { selectedTab = index },
                                text = {
                                    Text("Music Assistant")
                                },
                            )

                            "homeassistant" -> Tab(
                                selected = index == selectedTab,
                                onClick = { selectedTab = index },
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
                        "builtin" -> BuiltinAuthForm(viewModel, provider)
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

        loginError?.let {
            Logger.e("Error $it")
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = it,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun BuiltinAuthForm(viewModel: AuthenticationViewModel, provider: AuthProvider) {
    val username by viewModel.username.collectAsStateWithLifecycle()
    val password by viewModel.password.collectAsStateWithLifecycle()
    var isPasswordVisible by remember { mutableStateOf(false) }

    Column {
        TextField(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            value = username,
            onValueChange = { viewModel.username.value = it },
            label = { Text(stringResource(Res.string.auth_username)) },
            singleLine = true,
            colors = TextFieldDefaults.colors(
                focusedTextColor = MaterialTheme.colorScheme.onBackground,
                unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
            ),
        )

        TextField(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            value = password,
            onValueChange = { viewModel.password.value = it },
            label = { Text(stringResource(Res.string.auth_password)) },
            visualTransformation = if (isPasswordVisible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            trailingIcon = {
                val icon = if (isPasswordVisible) {
                    Icons.Filled.VisibilityOff
                } else {
                    Icons.Filled.Visibility
                }
                val description = if (isPasswordVisible) {
                    stringResource(Res.string.auth_hide_password)
                } else {
                    stringResource(Res.string.auth_show_password)
                }
                IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                    Icon(imageVector = icon, contentDescription = description)
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            colors = TextFieldDefaults.colors(
                focusedTextColor = MaterialTheme.colorScheme.onBackground,
                unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
            ),
        )

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = { viewModel.login(provider) },
            enabled = username.isNotEmpty() && password.isNotEmpty(),
        ) {
            Text(stringResource(Res.string.auth_login))
        }
    }
}
