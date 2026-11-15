package io.music_assistant.client.data.model.server

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SubItemPagingSupportTest {
    @Test
    fun pagesFromTheServer211Schema() {
        assertFalse(pagesSubItemLists(null))
        // 2.10.6 answers the whole list and ignores `limit`/`offset`.
        assertFalse(pagesSubItemLists(65))
        assertFalse(pagesSubItemLists(SERVER_2_11_SCHEMA - 1))
        assertTrue(pagesSubItemLists(SERVER_2_11_SCHEMA))
    }
}
