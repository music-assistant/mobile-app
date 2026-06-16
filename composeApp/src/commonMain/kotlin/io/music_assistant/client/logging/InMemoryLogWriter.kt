package io.music_assistant.client.logging

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity
import io.music_assistant.client.utils.currentTimeMillis

/** Kermit [LogWriter] that retains recent log lines in memory for crash/share reports. */
object InMemoryLogWriter : LogWriter() {
    private const val MAX_ENTRIES = 8000

    // Own severity floor, independent of the global (console) min severity.
    private val minSeverity = Severity.Info
    private val buffer = LogRingBuffer(MAX_ENTRIES)

    override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
        if (severity < minSeverity) return
        buffer.add(
            buildString {
                append(currentTimeMillis())
                append(' ')
                append(severity.name)
                append('/')
                append(tag)
                append(": ")
                append(message)
                if (throwable != null) {
                    append('\n')
                    append(throwable.stackTraceToString())
                }
            },
        )
    }

    fun getLogText(): String = buffer.snapshot().joinToString("\n")
}
