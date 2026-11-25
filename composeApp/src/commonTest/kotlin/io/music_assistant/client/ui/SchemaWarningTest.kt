package io.music_assistant.client.ui

import io.music_assistant.client.data.model.server.LOCAL_SCHEMA_VERSION
import io.music_assistant.client.data.model.server.MIN_SERVER_SCHEMA_VERSION
import io.music_assistant.client.data.model.server.ServerInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SchemaWarningTest {
    private fun info(schema: Int?, minSupported: Int? = null) =
        ServerInfo(serverId = "id", schemaVersion = schema, minSupportedSchemaVersion = minSupported)

    @Test
    fun server_2_9_is_too_old() {
        assertEquals(SchemaWarning.SERVER_TOO_OLD, schemaWarningFor(info(schema = 31)))
    }

    @Test
    fun the_floor_and_the_local_schema_get_no_warning() {
        assertNull(schemaWarningFor(info(schema = MIN_SERVER_SCHEMA_VERSION)))
        assertNull(schemaWarningFor(info(schema = LOCAL_SCHEMA_VERSION)))
    }

    @Test
    fun a_newer_server_is_ahead() {
        assertEquals(SchemaWarning.SERVER_AHEAD, schemaWarningFor(info(schema = LOCAL_SCHEMA_VERSION + 1)))
    }

    @Test
    fun a_client_below_the_server_minimum_wins_over_every_other_warning() {
        assertEquals(
            SchemaWarning.CLIENT_INCOMPATIBLE,
            schemaWarningFor(info(schema = 31, minSupported = LOCAL_SCHEMA_VERSION + 1)),
        )
    }

    @Test
    fun a_missing_schema_gets_no_warning() {
        assertNull(schemaWarningFor(info(schema = null)))
    }
}
