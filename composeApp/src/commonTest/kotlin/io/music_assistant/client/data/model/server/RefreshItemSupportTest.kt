package io.music_assistant.client.data.model.server

import kotlin.test.Test
import kotlin.test.assertEquals

/** Pins the `music/refresh_item` scope: `library.manage` before server 2.11, `library.write` from 2.11. */
class RefreshItemSupportTest {
    private val admin = GrantedScopes(setOf("*"))
    private val user = GrantedScopes(setOf(Scope.LIBRARY_READ, Scope.LIBRARY_WRITE))

    // A 2.11 custom role can hold library.manage without library.write.
    private val managerOnly = GrantedScopes(setOf(Scope.LIBRARY_READ, Scope.LIBRARY_MANAGE))
    private val server210 = SERVER_2_11_SCHEMA - 1

    @Test
    fun adminRefreshesOnEveryServer() {
        assertEquals(RefreshItemAccess.ALL, refreshItemAccess(admin, server210))
        assertEquals(RefreshItemAccess.ALL, refreshItemAccess(admin, SERVER_2_11_SCHEMA))
    }

    @Test
    fun libraryWriterFindsOnlyFromServer211() {
        assertEquals(RefreshItemAccess.NONE, refreshItemAccess(user, server210))
        assertEquals(RefreshItemAccess.FIND_ONLY, refreshItemAccess(user, SERVER_2_11_SCHEMA))
    }

    @Test
    fun managerWithoutWriteCannotRunTheCommandOnServer211() {
        assertEquals(RefreshItemAccess.NONE, refreshItemAccess(managerOnly, SERVER_2_11_SCHEMA))
    }

    @Test
    fun unknownSchemaNeedsTheStricterScope() {
        assertEquals(RefreshItemAccess.NONE, refreshItemAccess(user, null))
        assertEquals(RefreshItemAccess.ALL, refreshItemAccess(admin, null))
    }

    @Test
    fun noScopesGrantNothing() {
        assertEquals(RefreshItemAccess.NONE, refreshItemAccess(GrantedScopes.NONE, SERVER_2_11_SCHEMA))
    }
}
