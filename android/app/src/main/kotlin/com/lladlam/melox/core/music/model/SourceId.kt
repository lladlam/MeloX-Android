package com.lladlam.melox.core.music.model

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** Stable storage identity. A syntactically valid identity does not imply an installed provider. */
@JvmInline
value class SourceId private constructor(val value: String) {
    val builtIn: MusicSource?
        get() = MusicSource.entries.firstOrNull { it.storageValue == value }
    val displayName: String get() = builtIn?.displayName ?: value.removePrefix("plugin:")

    companion object {
        private val builtInNamespace = Regex("[a-z][a-z0-9_]{0,63}")
        private val pluginNamespace = Regex("plugin:[a-z0-9]+(?:-[a-z0-9]+)*(?:\\.[a-z0-9]+(?:-[a-z0-9]+)*)+")

        fun parse(value: String): SourceId? = value.takeIf {
            it.length <= 128 && (builtInNamespace.matches(it) || pluginNamespace.matches(it))
        }?.let(::SourceId)

        fun fromBuiltIn(source: MusicSource): SourceId = SourceId(source.storageValue)
    }
}

/** An identity codec for new-format references; it does not replace legacy Media3 IDs. */
object SourceResourceUri {
    private const val Prefix = "melox://source/v1/"
    private const val MaxResourceBytes = 4096
    private val hex = "0123456789ABCDEF"

    fun encode(source: SourceId, resource: String): String {
        require(resource.isNotBlank()) { "resource id must not be blank" }
        val bytes = resource.toByteArray(Charsets.UTF_8)
        require(bytes.size <= MaxResourceBytes && decodeUtf8(bytes) == resource) { "invalid resource id" }
        return Prefix + encodeSegment(source.value.toByteArray(Charsets.UTF_8)) + "/" + encodeSegment(bytes)
    }

    fun decode(uri: String): Pair<SourceId, String>? {
        if (uri.length > Prefix.length + 3 * (128 + MaxResourceBytes) + 1 || !uri.startsWith(Prefix)) return null
        val parts = uri.substring(Prefix.length).split('/')
        if (parts.size != 2) return null
        val source = decodeSegment(parts[0], 128)?.let(SourceId::parse) ?: return null
        val resource = decodeSegment(parts[1], MaxResourceBytes)?.takeIf { it.isNotBlank() } ?: return null
        // Reject alternate encodings, including lowercase escapes and escaped unreserved bytes.
        if (encode(source, resource) != uri) return null
        return source to resource
    }

    private fun encodeSegment(bytes: ByteArray): String = buildString {
        bytes.forEach {
            val b = it.toInt() and 0xff
            if (b in 65..90 || b in 97..122 || b in 48..57 || b == 45 || b == 46 || b == 95 || b == 126) {
                append(b.toChar())
            } else {
                append('%'); append(hex[b shr 4]); append(hex[b and 15])
            }
        }
    }

    private fun decodeSegment(segment: String, limit: Int): String? {
        val out = ByteArrayOutputStream()
        var index = 0
        while (index < segment.length) {
            val c = segment[index]
            if (c == '%') {
                if (index + 2 >= segment.length) return null
                val hi = hex.indexOf(segment[index + 1])
                val lo = hex.indexOf(segment[index + 2])
                if (hi < 0 || lo < 0) return null
                out.write((hi shl 4) or lo)
                index += 3
            } else {
                if (c.code > 127) return null
                out.write(c.code)
                index++
            }
            if (out.size() > limit) return null
        }
        return decodeUtf8(out.toByteArray())
    }

    private fun decodeUtf8(bytes: ByteArray): String? = try {
        Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
    } catch (_: java.nio.charset.CharacterCodingException) {
        null
    }
}
