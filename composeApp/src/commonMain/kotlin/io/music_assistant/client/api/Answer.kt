package io.music_assistant.client.api

import co.touchlab.kermit.Logger
import io.music_assistant.client.utils.myJson
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive

data class Answer(
    val json: JsonObject
) {
    val messageId: String? = json["message_id"]?.jsonPrimitive?.content
    val result: JsonElement? = json["result"]

    /**
     * Decode the RPC result payload as [T], returning `null` if there's no
     * result or if the payload doesn't match the Kotlin model. Decode
     * failures are logged with a truncated JSON preview (so the reason is
     * recoverable from logs) rather than thrown — an uncaught
     * [SerializationException] on a background coroutine dispatcher aborts
     * the whole Kotlin/Native process, so schema drift must stay local to the
     * affected RPC rather than crash the app.
     */
    inline fun <reified T : Any> resultAs(): T? {
        val payload = result ?: return null
        return try {
            myJson.decodeFromJsonElement<T>(payload)
        } catch (e: SerializationException) {
            Logger.withTag("Answer").w(e) {
                val preview = payload.toString().take(500)
                "Failed to decode RPC result as ${T::class.simpleName}: $preview"
            }
            null
        }
    }
}
