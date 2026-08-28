package com.lladlam.melox.core.lyrics

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcLyricsParserTest {
    @Test
    fun providerLrcFallbackDoesNotBecomeSyntheticWordTiming() {
        val document = LrcLyricsParser.parse(
            lrc = "[00:01.00]第一句歌词\n[00:04.00]第二句歌词",
        )

        assertFalse(document.pseudoTimingAllowed)
        assertTrue(document.lines.isNotEmpty())
        assertTrue(document.withPseudoTiming().lines.all { it.syllables.isEmpty() })
    }

    @Test
    fun nativeNeteaseLineLyricsKeepExistingPseudoTimingBehavior() {
        val document = NeteaseLyricParser.parse(
            yrc = "",
            lrc = "[00:01.00]第一句歌词\n[00:04.00]第二句歌词",
        )

        assertTrue(document.pseudoTimingAllowed)
        assertTrue(document.withPseudoTiming().lines.first().syllables.isNotEmpty())
    }

    @Test
    fun romanizationUsesMonotonicPairingAndCorrectsGlobalOffset() {
        val document = NeteaseLyricParser.parse(
            yrc = "",
            lrc = "[00:01.00]第一句\n[00:04.00]第二句\n[00:07.00]第三句",
            romanizedLrc = "[00:02.00]di yi ju\n[00:05.00]di er ju\n[00:08.00]di san ju",
        )

        assertTrue(document.lines.map { it.romanization } == listOf("di yi ju", "di er ju", "di san ju"))
    }
}
