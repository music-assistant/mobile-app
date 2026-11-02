package io.music_assistant.client.data

import io.music_assistant.client.auth.AuthCoordinator
import io.music_assistant.client.auth.AuthState
import io.music_assistant.client.data.model.server.AuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeItemDetailsAuthCoordinator : AuthCoordinator {
    override val authState: StateFlow<AuthState> =
        MutableStateFlow(AuthState.Idle)

    override suspend fun getProviders(): Result<List<AuthProvider>> =
        Result.success(emptyList())

    override suspend fun loginWithCredentials(
        providerId: String,
        username: String,
        password: String,
    ): Result<Unit> = Result.success(Unit)

    override suspend fun loginWithToken(token: String): Result<Unit> =
        Result.success(Unit)

    override suspend fun getOAuthUrl(
        provider: AuthProvider,
        returnUrl: String,
    ): Result<String> = Result.success("")

    override fun startOAuthFlow(oauthUrl: String): Result<Unit> =
        Result.success(Unit)

    override suspend fun logout(): Result<Unit> =
        Result.success(Unit)
}
