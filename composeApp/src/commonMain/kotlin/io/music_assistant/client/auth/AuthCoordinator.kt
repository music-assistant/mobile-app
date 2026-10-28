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

    /** A long-lived token that the user made on the profile page of the web frontend. */
    suspend fun loginWithToken(token: String): Result<Unit>

    suspend fun getOAuthUrl(provider: AuthProvider, returnUrl: String): Result<String>

    fun startOAuthFlow(oauthUrl: String): Result<Unit>

    suspend fun logout(): Result<Unit>
}
