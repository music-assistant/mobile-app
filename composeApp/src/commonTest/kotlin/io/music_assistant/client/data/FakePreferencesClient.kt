package io.music_assistant.client.data

import io.music_assistant.client.api.APICommands
import io.music_assistant.client.api.Answer
import io.music_assistant.client.api.Request
import io.music_assistant.client.data.model.server.StubServiceClient
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/** Answers `auth/me` and `auth/user/update` like the server: an update replaces all preferences. */
class FakePreferencesClient(var serverPreferences: JsonObject = JsonObject(emptyMap())) : StubServiceClient() {
    var failFetch = false
    var rejectUpdate = false

    /** Runs while the update is on its way, before the server answers. */
    var onUpdate: () -> Unit = {}

    val updates = mutableListOf<JsonObject>()

    override suspend fun sendRequest(request: Request): Result<Answer> = Result.success(
        when (request.command) {
            APICommands.AUTH_ME -> if (failFetch) error(request) else user(request)
            APICommands.AUTH_USER_UPDATE -> {
                val preferences = request.args?.get("preferences") as JsonObject
                updates += preferences
                onUpdate()
                if (rejectUpdate) {
                    error(request)
                } else {
                    serverPreferences = preferences
                    user(request)
                }
            }
            else -> error(request)
        },
    )

    private fun user(request: Request) = Answer(
        buildJsonObject {
            put("message_id", JsonPrimitive(request.messageId))
            put("result", buildJsonObject { put("preferences", serverPreferences) })
        },
    )

    // The server answers a failed command with an error code and no result.
    private fun error(request: Request) = Answer(
        buildJsonObject {
            put("message_id", JsonPrimitive(request.messageId))
            put("error_code", JsonPrimitive(errorCode))
            put("details", JsonPrimitive("rejected"))
        },
    )

    private val errorCode = 999
}
