package io.music_assistant.client.imageloader

import io.ktor.client.engine.darwin.DarwinHttpRequestException
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import platform.Foundation.NSError

class AtsBlockedImageDiagnosticIosTest {
    @Test
    fun detects_ats_in_native_error_and_kotlin_cause() {
        val error = DarwinHttpRequestException(
            NSError(domain = "NSURLErrorDomain", code = -1022, userInfo = emptyMap<Any?, Any?>()),
        )

        assertTrue(isAtsBlockedImageError(error))
        assertTrue(isAtsBlockedImageError(Exception("Image failed", error)))
    }

    @Test
    fun rejects_other_errors() {
        assertFalse(isAtsBlockedImageError(Exception("Image failed")))
        for ((domain, code) in listOf("NSURLErrorDomain" to -1003L, "OtherDomain" to -1022L)) {
            val error = DarwinHttpRequestException(
                NSError(domain = domain, code = code, userInfo = emptyMap<Any?, Any?>()),
            )
            assertFalse(isAtsBlockedImageError(error))
        }
    }
}
