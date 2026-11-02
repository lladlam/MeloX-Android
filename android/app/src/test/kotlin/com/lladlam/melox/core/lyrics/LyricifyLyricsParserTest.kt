package com.lladlam.melox.core.lyrics

import org.junit.Assert.*
import org.junit.Test
import com.lladlam.melox.core.provider.local.LocalLyricsCodec

class LyricifyLyricsParserTest {
    @Test
    fun linesUseAbsoluteEndTimeAndSubtractOffset() {
        val doc = LyricFileParser.parse("[type:LyricifyLines]\n[offset:200]\n[1000,2500]Hello\n[3000,4000]World")
        assertEquals(800L, doc.lines[0].timeMs)
        assertEquals(1500L, doc.lines[0].durationMs)
        assertEquals(2800L, doc.lines[1].timeMs)
        assertEquals(LyricTimingKind.LineSynchronized, doc.lines[0].timingKind)
        assertTrue(doc.lines.all { it.syllables.isEmpty() })
        assertFalse(doc.pseudoTimingAllowed)
    }

    @Test
    fun syllablePreservesAbsoluteTimingDuetAndBackgroundVocals() {
        val doc = LyricFileParser.parse("[offset:100]\n[4]你(1000,400)好(1400,600)\n[8]和声(1500,300)\n[5]再见(2500,500)")
        assertEquals(2, doc.lines.size)
        assertEquals("你好", doc.lines[0].text)
        assertEquals(listOf(900L, 1300L), doc.lines[0].syllables.map { it.startTimeMs })
        assertEquals(1900L, doc.lines[0].syllables.last().endTimeMs)
        assertEquals(LyricAgentAlignment.Flipped, doc.lines[1].agent?.alignment)
        val backing = doc.lines[0].accompaniment.single()
        assertEquals("和声", backing.text)
        assertEquals(1400L, backing.timeMs)
        assertEquals(LyricAgentAlignment.Flipped, backing.agent?.alignment)
    }

    @Test
    fun malformedLinesDoNotDropValidFollowingLines() {
        val doc = LyricifyLyricsParser.parseLines("[type:LyricifyLines]\n[broken]\n[2000,1000]bad\n[3000,4000]good")
        assertEquals("good", doc.lines.single().text)
        assertTrue(LyricFileParser.parse("[3]bad(999999999999999999999,100)").lines.isEmpty())
    }

    @Test
    fun detectionDistinguishesQrcKrcAndLyricify() {
        val qrc = LyricFileParser.parse("[1000,1000]你(1000,400)好(1400,600)")
        val krc = LyricFileParser.parse("[1000,1000]<0,400,0>你<400,600,0>好")
        assertEquals(2, qrc.lines.single().syllables.size)
        assertEquals(2, krc.lines.single().syllables.size)
        assertEquals(1000L, krc.lines.single().durationMs)
        assertEquals(1400L, krc.lines.single().syllables.last().startTimeMs)
    }

    @Test
    fun utf8BomAndOrdinaryLrcRemainReadable() {
        val doc = LyricFileParser.parse("\uFEFF[00:01.00]Hello".toByteArray())
        assertEquals("Hello", doc.lines.single().text)
        assertEquals(1000L, doc.lines.single().timeMs)
    }

    @Test
    fun localCachePreservesDuetAndBackingAfterReload() {
        val original = LyricFileParser.parse("[5]Lead(1000,1000)\n[7]Backing(1400,300)")
        val reloaded = LocalLyricsCodec.decodeLyrics(LocalLyricsCodec.encodeLyrics(original))
        assertEquals(original, reloaded)
    }

    @Test
    fun plainProviderTextRemainsReadableWithoutInventedTiming() {
        val doc = LrcLyricsParser.parse("First line\nSecond line")
        assertEquals(2, doc.lines.size)
        assertTrue(doc.isUnsynchronized)
        assertNull(doc.highlightedIndex(10000L))
        assertEquals(doc, LyricTimelineProcessor.process(doc).withPseudoTiming())
        assertEquals(doc, LocalLyricsCodec.decodeLyrics(LocalLyricsCodec.encodeLyrics(doc)))
    }
}
