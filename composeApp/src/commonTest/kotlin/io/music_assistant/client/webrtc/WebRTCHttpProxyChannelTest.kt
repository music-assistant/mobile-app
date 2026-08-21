package io.music_assistant.client.webrtc

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Covers the dedicated `http_proxy` channel framing introduced by
 * music-assistant/server#5635 and #5643, mirroring the web frontend's own suite
 * (`tests/plugins/webrtc-transport-http-proxy-channel.test.ts`).
 *
 * The two framings must coexist: a schema-49 server that predates the binary framing still
 * answers in hex on the dedicated channel, and every server answers in hex on `ma-api`.
 */
class WebRTCHttpProxyChannelTest {
    /** Captures outgoing requests so a test can answer the id the proxy actually generated. */
    private class RecordingChannel {
        val sent = mutableListOf<JsonObject>()
        private val firstSend = CompletableDeferred<Unit>()

        val send: suspend (JsonObject) -> Unit = { json ->
            sent.add(json)
            firstSend.complete(Unit)
        }

        suspend fun awaitFirstRequestId(): String {
            firstSend.await()
            return (sent.first()["id"] as JsonPrimitive).contentOrNull!!
        }
    }

    /** The hex-in-JSON reply, which is what a caller on `ma-api` always receives. */
    private fun hexResponse(id: String, body: List<Int>): String {
        val hex = body.joinToString("") { it.toString(16).padStart(2, '0') }
        return """{"type":"http-proxy-response","id":"$id","status":200,""" +
            """"headers":{"content-type":"image/png"},"body":"$hex"}"""
    }

    /** The dedicated channel's framing: a JSON header, then the body as raw binary frames. */
    private fun binaryHeader(id: String, size: Int): String =
        """{"type":"http-proxy-response","id":"$id","status":200,""" +
            """"headers":{"content-type":"image/png"},"size":$size}"""

    private fun frames(body: List<Int>, frameBytes: Int): List<ByteArray> =
        body.chunked(frameBytes).map { chunk -> ByteArray(chunk.size) { chunk[it].toByte() } }

    @Test
    fun reassemblesBodyDeliveredInOneFrame() = runTest {
        val channel = RecordingChannel()
        val proxy = WebRTCHttpProxy(sender = { error("must not use ma-api") })
        proxy.attachChannel(channel.send)

        val response = async { proxy.get("/imageproxy?p=1") }
        val id = channel.awaitFirstRequestId()
        proxy.dispatchProxyChannelText(binaryHeader(id, 4))
        proxy.dispatchProxyChannelBinary(byteArrayOf(1, 2, 3, 4))

        val result = response.await()
        assertEquals(200, result.status)
        assertContentEquals(byteArrayOf(1, 2, 3, 4), result.body)
        assertEquals("image/png", result.headers["content-type"])
    }

    @Test
    fun reassemblesBodySplitAcrossSeveralFrames() = runTest {
        val channel = RecordingChannel()
        val proxy = WebRTCHttpProxy(sender = { error("must not use ma-api") })
        proxy.attachChannel(channel.send)

        val body = listOf(9, 8, 7, 6, 5, 4, 3, 2, 1, 0)
        val response = async { proxy.get("/imageproxy?p=1") }
        val id = channel.awaitFirstRequestId()
        proxy.dispatchProxyChannelText(binaryHeader(id, body.size))
        // 4/4/2 — the server sizes frames to the channel's negotiated limit, not to the body.
        frames(body, frameBytes = 4).forEach { proxy.dispatchProxyChannelBinary(it) }

        assertContentEquals(
            ByteArray(body.size) { body[it].toByte() },
            response.await().body,
        )
    }

    @Test
    fun resolvesEmptyBodyFromItsHeaderAlone() = runTest {
        val channel = RecordingChannel()
        val proxy = WebRTCHttpProxy(sender = { error("must not use ma-api") })
        proxy.attachChannel(channel.send)

        val response = async { proxy.get("/imageproxy?p=1") }
        val id = channel.awaitFirstRequestId()
        proxy.dispatchProxyChannelText(binaryHeader(id, 0))

        val result = response.await()
        assertEquals(200, result.status)
        assertEquals(0, result.body.size)
    }

    @Test
    fun stillReadsHexResponseOnDedicatedChannel() = runTest {
        val channel = RecordingChannel()
        val proxy = WebRTCHttpProxy(sender = { error("must not use ma-api") })
        proxy.attachChannel(channel.send)

        // A server that reports schema 49 but predates the binary framing answers like this.
        val response = async { proxy.get("/imageproxy?p=1") }
        val id = channel.awaitFirstRequestId()
        proxy.dispatchProxyChannelText(hexResponse(id, listOf(4, 5, 6)))

        assertContentEquals(byteArrayOf(4, 5, 6), response.await().body)
    }

    @Test
    fun failsInFlightProxyChannelRequestsWhenChannelCloses() = runTest {
        val channel = RecordingChannel()
        val proxy = WebRTCHttpProxy(sender = { error("must not use ma-api") })
        proxy.attachChannel(channel.send)

        // runCatching inside the async: a bare failing async would propagate into the
        // runTest scope and abort the test before the assertion below could run.
        val streaming = async { runCatching { proxy.get("/imageproxy?p=1") } }
        val id = channel.awaitFirstRequestId()
        // Mid-body: the header landed and one frame arrived, then the channel dropped.
        proxy.dispatchProxyChannelText(binaryHeader(id, 8))
        proxy.dispatchProxyChannelBinary(byteArrayOf(1, 2, 3, 4))

        proxy.detachChannel(IllegalStateException("http_proxy channel closed"))

        // Fails now rather than sitting out the 30 s request timeout.
        assertTrue(streaming.await().exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun leavesApiChannelRequestsAloneWhenProxyChannelCloses() = runTest {
        val apiChannel = RecordingChannel()
        val proxy = WebRTCHttpProxy(sender = apiChannel.send)

        // Goes out before the dedicated channel exists, so it must survive that channel's close.
        val viaApi = async { proxy.get("/imageproxy?p=1") }
        val id = apiChannel.awaitFirstRequestId()

        proxy.attachChannel { error("must not be used by an already-sent request") }
        proxy.detachChannel(IllegalStateException("http_proxy channel closed"))

        // The server still answers on the channel the request arrived on.
        proxy.dispatchRawResponse(hexResponse(id, listOf(7, 7)))
        assertContentEquals(byteArrayOf(7, 7), viaApi.await().body)
    }

    @Test
    fun dropsBodyForRequestThatAlreadyGaveUp() = runTest {
        val channel = RecordingChannel()
        val proxy = WebRTCHttpProxy(sender = { error("must not use ma-api") })
        proxy.attachChannel(channel.send)

        // Nothing is pending, so the header must not open a reassembly slot. A later frame
        // then has no body to append to and is discarded rather than corrupting the next reply.
        proxy.dispatchProxyChannelText(binaryHeader("req_never_sent", 4))
        proxy.dispatchProxyChannelBinary(byteArrayOf(1, 2, 3, 4))

        val response = async { proxy.get("/imageproxy?p=1") }
        val id = channel.awaitFirstRequestId()
        proxy.dispatchProxyChannelText(binaryHeader(id, 2))
        proxy.dispatchProxyChannelBinary(byteArrayOf(8, 9))

        assertContentEquals(byteArrayOf(8, 9), response.await().body)
    }

    @Test
    fun routesToDedicatedChannelOnceAttachedAndBackToApiAfterDetach() = runTest {
        val apiChannel = RecordingChannel()
        val proxyChannel = RecordingChannel()
        val proxy = WebRTCHttpProxy(sender = apiChannel.send)

        proxy.attachChannel(proxyChannel.send)
        val onProxy = async { proxy.get("/imageproxy?p=1") }
        val proxyId = proxyChannel.awaitFirstRequestId()
        proxy.dispatchProxyChannelText(binaryHeader(proxyId, 1))
        proxy.dispatchProxyChannelBinary(byteArrayOf(1))
        onProxy.await()

        proxy.detachChannel(IllegalStateException("closed"))

        val onApi = async { proxy.get("/imageproxy?p=2") }
        val apiId = apiChannel.awaitFirstRequestId()
        proxy.dispatchRawResponse(hexResponse(apiId, listOf(2)))
        onApi.await()

        assertEquals(1, proxyChannel.sent.size)
        assertEquals(1, apiChannel.sent.size)
        assertTrue(proxyId != apiId)
    }
}
