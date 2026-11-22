package io.music_assistant.client.data.share

import java.util.zip.DataFormatException
import java.util.zip.Inflater

internal actual fun inflateSharePayload(input: ByteArray, maxOutput: Int): ByteArray? {
    val inflater = Inflater(true)
    val output = ByteArray(maxOutput + 1)
    return try {
        inflater.setInput(input)
        val size = inflater.inflate(output)
        output.copyOf(size).takeIf { inflater.finished() && size <= maxOutput }
    } catch (_: DataFormatException) {
        null
    } finally {
        inflater.end()
    }
}
