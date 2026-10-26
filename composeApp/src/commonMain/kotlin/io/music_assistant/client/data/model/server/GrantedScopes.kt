package io.music_assistant.client.data.model.server

/** Server scope names the client gates on (`music_assistant_models.auth.Scope`). */
object Scope {
    const val LIBRARY_READ = "library.read"
    const val LIBRARY_WRITE = "library.write"
    const val LIBRARY_MANAGE = "library.manage"
    const val QUEUES_CONTROL = "queues.control"
    const val CONFIG_PLAYERS_WRITE = "config.players.write"
    const val CONFIG_PROVIDERS_READ = "config.providers.read"

    /** The server's wildcard scope, granted to `admin`. */
    internal const val ALL = "*"
}

/**
 * The scopes that the server grants the signed-in user: the user's role looked up in the
 * role-to-scope table from `auth/scopes`. Mirrors the server's own `has_scope`.
 *
 * Deliberately keyed on the scope and not on `role == "admin"`: from server 2.11 a role can be
 * a custom role id, and a role absent from the table grants nothing. Asking about the scope also
 * means a server that widens a role unlocks the UI with no client change.
 */
data class GrantedScopes(private val scopes: Set<String>) {
    fun has(scope: String): Boolean = Scope.ALL in scopes || scope in scopes

    fun hasAll(required: Collection<String>): Boolean = required.all(::has)

    /** Favorites, library membership, playlist edits and played state. */
    val canEditLibrary: Boolean get() = has(Scope.LIBRARY_WRITE)

    /** Player settings, DSP included. */
    val canConfigurePlayers: Boolean get() = has(Scope.CONFIG_PLAYERS_WRITE)

    companion object {
        /** Nothing granted: the state before `auth/scopes` answers, and after it fails. */
        val NONE = GrantedScopes(emptySet())

        fun of(roleScopes: Map<String, List<String>>, role: String?): GrantedScopes =
            roleScopes[role]?.let { GrantedScopes(it.toSet()) } ?: NONE
    }
}
