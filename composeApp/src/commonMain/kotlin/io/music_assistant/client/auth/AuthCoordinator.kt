package io.music_assistant.client.auth

import io.music_assistant.client.data.model.server.AuthProvider
import kotlinx.coroutines.flow.StateFlow

/** Surface of [AuthenticationManager] consumed by UI viewmodels. */
interface AuthCoordinator {
    val authState: StateFlow<AuthState>

    suspend fun getProviders(): Result<List<AuthProvider>>

    suspend fun loginWithCredentials(
        providerId: String,
        username: String,
        password: String,
    ): Result<Unit>

    suspend fun getOAuthUrl(providerId: String, returnUrl: String): Result<String>

    fun startOAuthFlow(oauthUrl: String): Result<Unit>

    suspend fun logout(): Result<Unit>
}
