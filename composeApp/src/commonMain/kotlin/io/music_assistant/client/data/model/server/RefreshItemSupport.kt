package io.music_assistant.client.data.model.server

/** Which `music/refresh_item` actions the signed-in user may run. */
enum class RefreshItemAccess {
    NONE,

    /** "Find on music services" for an unavailable item only. */
    FIND_ONLY,

    /** "Refresh" for any item and "Find on music services" for an unavailable item. */
    ALL,
}

/**
 * `music/refresh_item` needs `library.manage` before server 2.11 and `library.write` from 2.11
 * (server schema 81). Like the web frontend, a plain "Refresh" also needs `library.manage`, so a
 * library writer can only look up an item that no music service has any more.
 */
fun refreshItemAccess(scopes: GrantedScopes, schemaVersion: Int?): RefreshItemAccess {
    val required = if (schemaVersion != null && schemaVersion >= SERVER_2_11_SCHEMA) {
        Scope.LIBRARY_WRITE
    } else {
        Scope.LIBRARY_MANAGE
    }
    return when {
        !scopes.has(required) -> RefreshItemAccess.NONE
        scopes.has(Scope.LIBRARY_MANAGE) -> RefreshItemAccess.ALL
        else -> RefreshItemAccess.FIND_ONLY
    }
}
