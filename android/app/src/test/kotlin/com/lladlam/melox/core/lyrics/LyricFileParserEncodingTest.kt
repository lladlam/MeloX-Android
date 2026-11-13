package com.lladlam.melox.core.lyrics

import org.junit.Assert.assertEquals
import org.junit.Test

class LyricFileParserEncodingTest {
    @Test
    fun utf8SidecarKeepsItsText() {
        val document = LyricFileParser.parse("[00:01.00]你好 UTF-8".toByteArray(Charsets.UTF_8))
        assertEquals("你好 UTF-8", document.lines.single().text)
    }

    @Test
    fun gb18030SidecarDoesNotBecomeReplacementCharacters() {
        val document = LyricFileParser.parse("[00:01.00]你好，中文歌词".toByteArray(charset("GB18030")))
        assertEquals("你好，中文歌词", document.lines.single().text)
    }
}
