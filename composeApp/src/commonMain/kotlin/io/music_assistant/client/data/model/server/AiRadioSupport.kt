package io.music_assistant.client.data.model.server

/** Provider domain of the AI Radio plugin, as reported by the `providers` command. */
const val AI_RADIO_DOMAIN = "ai_radio"

/**
 * Scope the server demands for `ai_radio/start` and `ai_radio/stop`. The plugin registers
 * them under `CONFIG_PROVIDERS_WRITE`, which in the builtin role table only `admin` holds:
 * a `user` may list stations but never play one. Gating on the write scope keeps that role
 * from seeing a station list where every tap fails.
 */
const val AI_RADIO_REQUIRED_SCOPE = "config.providers.write"

/** The server's wildcard scope, granted to `admin`. */
private const val SCOPE_ALL = "*"

/**
 * Reports whether [role] is granted [scope], against the role-to-scope table from
 * `auth/scopes`. Mirrors the server's own `has_scope`.
 *
 * Deliberately keyed on the scope and not on `role == "admin"`: the server models a role as
 * a free-form string to leave room for custom roles, and a role absent from the table grants
 * nothing. Asking about the scope also means a future server that widens these permissions
 * unlocks the UI with no client change.
 */
fun grantsScope(roleScopes: Map<String, List<String>>, role: String?, scope: String): Boolean =
    roleScopes[role].orEmpty().let { SCOPE_ALL in it || scope in it }
