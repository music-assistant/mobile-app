package io.music_assistant.sendspin.android

/**
 * The OS interruptions of one sink handle, and when they are over. A call can take the
 * focus, enter call mode, or both, in either order; the output may resume only when every
 * cause is gone and none of them was final (a permanent focus loss, the output gone).
 */
internal class SinkInterruption {
    private var focusHeld = true
    private var inCall = false

    /** Null while not interrupted; false once any cause cannot resume. */
    private var resumable: Boolean? = null

    fun focusLost(transient: Boolean) {
        focusHeld = false
        interrupt(transient)
    }

    fun callStarted() {
        inCall = true
        interrupt(canResume = true)
    }

    fun outputGone() = interrupt(canResume = false)

    /** True when this ends the interruption and the app may resume. */
    fun focusGained(): Boolean {
        focusHeld = true
        return endIfClear()
    }

    /** True when this ends the interruption and the app may resume. */
    fun callEnded(): Boolean {
        if (!inCall) return false
        inCall = false
        return endIfClear()
    }

    private fun interrupt(canResume: Boolean) {
        resumable = (resumable ?: true) && canResume
    }

    private fun endIfClear(): Boolean =
        (resumable == true && focusHeld && !inCall).also { if (it) resumable = null }
}
