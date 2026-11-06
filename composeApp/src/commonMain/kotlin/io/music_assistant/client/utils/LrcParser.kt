package io.music_assistant.client.utils

import io.music_assistant.client.data.model.client.LrcLine

/**
 * Minimal LRC parser. Handles standard line-level timestamps
 * `[mm:ss]`, `[mm:ss.xx]`, `[mm:ss.xxx]`, with any number of minute digits so
 * tracks longer than 99 minutes keep their later lines (also tolerating the `:` fraction
 * separator some encoders emit), including multiple timestamps on one line
 * (`[00:12.00][00:47.00] chorus` → two lines). The `[offset:±ms]` ID tag is
 * honoured — a global timestamp correction where a positive value makes lines
 * appear earlier (per the LRC convention), so effective time = stamp − offset.
 * Other ID3-style metadata tags (`[ar:]`, `[ti:]`, …) don't match the numeric
 * time shape and are ignored. Enhanced word-level `<..>` tags are not
 * interpreted (their surrounding text is kept verbatim).
 *
 * Returns lines sorted by [LrcLine.timeMs]. An empty result signals "not
 * parseable as synced LRC" — callers should fall back to plain text.
 */
object LrcParser {
    // Leading timestamp tag: minutes (any digit count, e.g. [102:30.00]):seconds with an
    // optional 1–3 digit fraction.
    private val timeTag = Regex("""\[(\d+):(\d{2})(?:[.:](\d{1,3}))?]""")

    // Global offset tag, e.g. [offset:+250] / [offset:-1000]; whitespace tolerated.
    private val offsetTag = Regex("""\[offset:\s*([+-]?\d+)\s*]""", RegexOption.IGNORE_CASE)

    fun parse(lrc: String): List<LrcLine> {
        val offsetMs = offsetTag.find(lrc)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val lines = mutableListOf<LrcLine>()
        for (raw in lrc.lineSequence()) {
            // Collect the run of timestamp tags anchored at the line start.
            val stamps = mutableListOf<Long>()
            var cursor = 0
            while (true) {
                val match = timeTag.matchAt(raw, cursor) ?: break
                stamps += match.timeMs()
                cursor = match.range.last + 1
            }
            if (stamps.isEmpty()) continue // metadata tag or plain line — skip
            val text = raw.substring(cursor).trim()
            // Positive offset → lyrics sooner → subtract; floor at 0 so early lines stay valid.
            stamps.forEach { ms -> lines += LrcLine((ms - offsetMs).coerceAtLeast(0L), text) }
        }
        return lines.sortedBy { it.timeMs }
    }

    private fun MatchResult.timeMs(): Long {
        val (min, sec, frac) = destructured
        val fractionMs = when (frac.length) {
            0 -> 0
            1 -> frac.toInt() * 100
            2 -> frac.toInt() * 10
            else -> frac.take(3).toInt()
        }
        return min.toLong() * MS_IN_MINUTE + sec.toLong() * 1_000 + fractionMs
    }
}

private const val MS_IN_MINUTE = 60_000L
