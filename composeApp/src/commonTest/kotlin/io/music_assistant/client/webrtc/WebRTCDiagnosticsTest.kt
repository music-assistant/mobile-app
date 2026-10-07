package io.music_assistant.client.webrtc

import io.ktor.websocket.CloseReason
import kotlin.test.Test
import kotlin.test.assertEquals

class WebRTCDiagnosticsTest {
    @Test
    fun closeReasonOmitsArbitraryRemoteText() {
        assertEquals(
            "code=1008 reason=omitted",
            safeCloseSummary(CloseReason(1008.toShort(), "token=secret\nhttps://private.example/session")),
        )
    }

    @Test
    fun closeReasonKeepsCodeAndKnownCategory() {
        assertEquals(
            "code=1000 reason=client_disconnect",
            safeCloseSummary(CloseReason(CloseReason.Codes.NORMAL, "Client disconnect")),
        )
        assertEquals("code=1000 reason=empty", safeCloseSummary(CloseReason(CloseReason.Codes.NORMAL, "")))
        assertEquals("code=unavailable reason=unavailable", safeCloseSummary(null))
    }

    @Test
    fun channelLabelsAreAllowlisted() {
        for (label in listOf("ma-api", "sendspin", "http_proxy")) {
            assertEquals(label, safeChannelLabel(label))
        }
        assertEquals("other", safeChannelLabel("secret\nforged log line"))
    }

    @Test
    fun locallyAuthoredExceptionMessagesAreKept() {
        assertEquals(
            "Peer connection not initialized",
            safeErrorDetail(IllegalStateException("Peer connection not initialized")),
        )
        assertEquals(
            "only ordered=true is supported",
            safeErrorDetail(IllegalArgumentException("only ordered=true is supported")),
        )
    }

    @Test
    fun locallyAuthoredExceptionMessagesAreFlattenedAndCapped() {
        val multiline = IllegalArgumentException("line one\nhttps://secret.example/x line two")
        assertEquals("line one https://secret.example/x line two", safeErrorDetail(multiline))

        val long = IllegalStateException("x".repeat(300))
        assertEquals(120, safeErrorDetail(long)?.length)
    }

    @Test
    fun foreignExceptionMessagesAreOmitted() {
        // Remote-facing exceptions: their messages may embed URLs, SDP or payloads.
        assertEquals(null, safeErrorDetail(RuntimeException("wss://private.example/ws?token=secret")))
        assertEquals(null, safeErrorDetail(Exception("SDP: v=0 o=- 123 ...")))
    }
}
