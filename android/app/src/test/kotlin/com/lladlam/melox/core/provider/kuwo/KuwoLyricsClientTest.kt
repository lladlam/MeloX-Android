package com.lladlam.melox.core.provider.kuwo

import org.junit.Assert.assertTrue
import org.junit.Test

class KuwoLyricsClientTest {
    @Test
    fun decodesRealLyricxSample() {
        val bytes = javaClass.classLoader.getResourceAsStream("kuwo_lrcx_sample.bin")!!.readBytes()
        val document = KuwoLyricsClient().decodeLyrics(bytes)
        assertTrue("expected non-empty lyrics, got ${document.lines.size} lines", document.lines.size > 10)
        val firstLine = document.lines.first().text
        assertTrue("first line should not contain word-timing tags: $firstLine", "<" !in firstLine)
    }
}
