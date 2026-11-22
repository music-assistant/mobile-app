@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.music_assistant.client.data.share

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import platform.zlib.Z_FINISH
import platform.zlib.Z_OK
import platform.zlib.Z_STREAM_END
import platform.zlib.inflate
import platform.zlib.inflateEnd
import platform.zlib.inflateInit2
import platform.zlib.z_stream

private const val RAW_DEFLATE_WINDOW_BITS = -15

internal actual fun inflateSharePayload(input: ByteArray, maxOutput: Int): ByteArray? = memScoped {
    val stream = alloc<z_stream>()
    stream.zalloc = null
    stream.zfree = null
    stream.opaque = null
    val output = ByteArray(maxOutput + 1)
    input.usePinned { source ->
        output.usePinned { target ->
            stream.next_in = source.addressOf(0).reinterpret()
            stream.avail_in = input.size.toUInt()
            stream.next_out = target.addressOf(0).reinterpret()
            stream.avail_out = output.size.toUInt()
            if (inflateInit2(stream.ptr, RAW_DEFLATE_WINDOW_BITS) != Z_OK) return null
            try {
                if (inflate(stream.ptr, Z_FINISH) != Z_STREAM_END) return null
                val size = stream.total_out.toInt()
                if (size <= maxOutput) output.copyOf(size) else null
            } finally {
                inflateEnd(stream.ptr)
            }
        }
    }
}
