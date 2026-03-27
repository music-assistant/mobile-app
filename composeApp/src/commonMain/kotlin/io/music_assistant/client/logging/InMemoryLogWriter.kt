package io.music_assistant.client.logging

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity
import io.music_assistant.client.utils.currentTimeMillis
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
object InMemoryLogWriter : LogWriter() {

    private const val MAX_ENTRIES = 3000
    private val bufferRef = AtomicReference(emptyList<String>())

    override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
        val line = buildString {
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
        }
        while (true) {
            val current = bufferRef.load()
            val updated = current.toMutableList().apply {
                if (size >= MAX_ENTRIES) removeFirst()
                add(line)
            }
            if (bufferRef.compareAndSet(current, updated)) break
        }
    }

    fun getLogText(): String = bufferRef.load().joinToString("\n")
}
