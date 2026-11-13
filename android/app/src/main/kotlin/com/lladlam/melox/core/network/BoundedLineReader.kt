package com.lladlam.melox.core.network

import java.io.Reader

/**
 * Result of a bounded line read.
 *
 * [java.io.BufferedReader.readLine] allocates the complete line before the
 * caller gets a chance to check its size, so a peer that never sends a newline
 * can force an arbitrarily large allocation. Reading character by character
 * keeps the peak memory bounded by the configured limit.
 */
internal sealed class BoundedLineResult {
    data class Line(val value: String) : BoundedLineResult()
    object Eof : BoundedLineResult()
    data class TooLong(val limit: Int) : BoundedLineResult()
}

/** Reads one newline-terminated line, or null when it is missing or too long. */
internal fun Reader.readBoundedLine(limit: Int): String? =
    when (val result = readBoundedLineResult(limit)) {
        is BoundedLineResult.Line -> result.value
        else -> null
    }

/** Reads one newline-terminated line while enforcing a hard character limit. */
internal fun Reader.readBoundedLineResult(limit: Int): BoundedLineResult {
    val builder = StringBuilder()
    while (true) {
        val value = read()
        if (value < 0) {
            return if (builder.isEmpty()) BoundedLineResult.Eof
            else BoundedLineResult.Line(builder.toString())
        }
        if (value == '\n'.code) {
            if (builder.isNotEmpty() && builder.last() == '\r') builder.setLength(builder.length - 1)
            return BoundedLineResult.Line(builder.toString())
        }
        if (builder.length >= limit) return BoundedLineResult.TooLong(limit)
        builder.append(value.toChar())
    }
}
