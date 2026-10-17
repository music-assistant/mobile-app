package io.music_assistant.client.data.model.server

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LoginResponse(
    @SerialName("success") val success: Boolean,
    @SerialName("access_token") val token: String? = null,
    @SerialName("user") val user: User? = null,
    @SerialName("error") val error: String? = null,
)

@Serializable
data class User(
    @SerialName("user_id") val userId: String? = null,
    @SerialName("username") val username: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("role") val role: String? = null,
) {
    val description: String
        get() = (displayName ?: username ?: "Unknown User") + if (role != null) " ($role)" else ""
}

@Serializable
data class AuthorizationResponse(
    @SerialName("user") val user: User,
)

@Serializable
data class AuthProvider(
    @SerialName("provider_id") val id: String,
    @SerialName("provider_type") val type: String,
    @SerialName("requires_redirect") val requiresRedirect: Boolean,
    /** Server 2.11: the browser can return to the app, over Remote Access only. */
    @SerialName("supports_remote_app") val supportsRemoteApp: Boolean = false,
)

/** [url] is null, with an [error], when the server refuses the sign-in. */
@Serializable
data class OauthUrl(
    @SerialName("authorization_url") val url: String? = null,
    @SerialName("state") val state: String? = null,
    @SerialName("error") val error: String? = null,
)
