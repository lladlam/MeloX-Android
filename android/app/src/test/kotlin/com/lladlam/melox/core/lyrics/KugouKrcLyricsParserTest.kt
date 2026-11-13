package com.lladlam.melox.core.lyrics

import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.DeflaterOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KugouKrcLyricsParserTest {
    @Test
    fun rejectsHighlyCompressedOversizedLyrics() {
        val output = ByteArrayOutputStream()
        DeflaterOutputStream(output).use { stream ->
            val block = ByteArray(8192) { 'a'.code.toByte() }
            repeat(513) { stream.write(block) }
        }
        val key = byteArrayOf(64, 71, 97, 119, 94, 50, 116, 71, 81, 54, 49, 45, 206.toByte(), 210.toByte(), 110, 105)
        val compressed = output.toByteArray()
        val encrypted = ByteArray(compressed.size + 4)
        "krc1".toByteArray().copyInto(encrypted)
        compressed.indices.forEach { index ->
            encrypted[index + 4] = (compressed[index].toInt() xor key[index % key.size].toInt()).toByte()
        }

        assertEquals("", KugouKrcLyricsParser.decodeBase64(Base64.getEncoder().encodeToString(encrypted)))
    }

    @Test
    fun parsesRelativeWordTimingIntoAbsoluteMeloXSyllables() {
        val source = "[1000,2000]<0,500,0>Hello <500,600,0>world"
        val document = KugouKrcLyricsParser.parse(source)

        assertEquals(1, document.lines.size)
        val line = document.lines.single()
        assertEquals(1000L, line.timeMs)
        assertEquals(2000L, line.durationMs)
        assertEquals("Hello world", line.text)
        assertEquals(2, line.syllables.size)
        assertEquals(1000L, line.syllables[0].startTimeMs)
        assertEquals(1500L, line.syllables[0].endTimeMs)
        assertEquals(1500L, line.syllables[1].startTimeMs)
        assertEquals(2100L, line.syllables[1].endTimeMs)
        assertTrue(line.syllables[1].text.contains("world"))
    }
}
