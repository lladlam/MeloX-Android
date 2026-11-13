package com.lladlam.melox.core.network

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

/** Thrown when a decompressed payload exceeds the caller's hard limit. */
internal class BoundedStreamExceededException(val limit: Int) :
    IOException("stream exceeded $limit bytes")

/**
 * Reads an [InputStream] while enforcing a hard limit on the total number of
 * bytes produced. Unlike `readBytes()`, an attacker-controlled deflate stream
 * cannot expand into an unbounded allocation before the caller can check it.
 */
internal fun InputStream.readBoundedBytes(maxBytes: Int): ByteArray {
    require(maxBytes >= 0) { "maxBytes must not be negative" }
    val output = ByteArrayOutputStream(minOf(maxBytes, 64 * 1024))
    val buffer = ByteArray(16 * 1024)
    var total = 0
    while (true) {
        val read = read(buffer)
        if (read < 0) break
        total += read
        if (total > maxBytes) throw BoundedStreamExceededException(maxBytes)
        output.write(buffer, 0, read)
    }
    return output.toByteArray()
}
