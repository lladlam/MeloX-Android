package com.lladlam.melox.core.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QqBracketBackingTest {
    private fun line(
        timeMs: Long,
        durationMs: Long,
        text: String,
        syllables: List<LyricSyllable> = emptyList(),
    ) = LyricLine(
        timeMs = timeMs,
        durationMs = durationMs,
        text = text,
        syllables = syllables,
        timingKind = LyricTimingKind.LineSynchronized,
    )

    @Test
    fun attachesBracketLineToPreviousLeadWithLeadEndAsStart() {
        val document = LyricsDocument(
            lines = listOf(
                line(10_000, 3_000, "主歌词"),
                line(14_000, 2_000, "（伴唱）"),
            ),
            source = LyricSource.QQMusic,
        )
        val result = document.withQqBracketBackingVocals()
        assertEquals(1, result.lines.size)
        val lead = result.lines.single()
        assertEquals("主歌词", lead.text)
        val backing = lead.accompaniment.single()
        assertEquals("伴唱", backing.text)
        assertEquals(13_000L, backing.timeMs)
        assertEquals(3_000L, backing.durationMs) // original end 16s - new start 13s
    }

    @Test
    fun consecutiveBracketLinesFollowEachOtherWithoutOverlap() {
        val document = LyricsDocument(
            lines = listOf(
                line(10_000, 3_000, "主歌词"),
                line(14_000, 2_000, "(和声一)"),
                line(16_000, 2_000, "（和声二）"),
            ),
            source = LyricSource.QQMusic,
        )
        val result = document.withQqBracketBackingVocals()
        val backing = result.lines.single().accompaniment
        assertEquals(2, backing.size)
        assertEquals(13_000L, backing[0].timeMs)
        assertEquals(16_000L, backing[0].timeMs + backing[0].durationMs!!)
        assertEquals(16_000L, backing[1].timeMs)
        assertEquals(18_000L, backing[1].timeMs + backing[1].durationMs!!)
    }

    @Test
    fun firstBracketLineAndInlineParenthesesRemainMainLines() {
        val document = LyricsDocument(
            lines = listOf(
                line(1_000, 2_000, "（开场伴唱）"),
                line(4_000, 2_000, "主（伴）歌词"),
            ),
            source = LyricSource.QQMusic,
        )
        val result = document.withQqBracketBackingVocals()
        assertEquals(listOf("（开场伴唱）", "主（伴）歌词"), result.lines.map { it.text })
        assertTrue(result.lines.all { it.accompaniment.isEmpty() })
    }

    @Test
    fun doesNotConvertWhenOriginalEndIsNotAfterLeadEnd() {
        val document = LyricsDocument(
            lines = listOf(
                line(10_000, 5_000, "主歌词"),
                line(11_000, 2_000, "（伴唱）"),
            ),
            source = LyricSource.QQMusic,
        )
        val result = document.withQqBracketBackingVocals()
        assertEquals(2, result.lines.size)
        assertTrue(result.lines.all { it.accompaniment.isEmpty() })
    }

    @Test
    fun shiftsAuthoredSyllablesIntoTheNewWindow() {
        val syllables = listOf(
            LyricSyllable("伴", 14_000, 14_500),
            LyricSyllable("唱", 14_500, 16_000),
        )
        val document = LyricsDocument(
            lines = listOf(
                line(10_000, 3_000, "主歌词"),
                line(14_000, 2_000, "（伴唱）", syllables),
            ),
            source = LyricSource.QQMusic,
        )
        val backing = document.withQqBracketBackingVocals().lines.single().accompaniment.single()
        assertEquals(13_000L, backing.syllables.first().startTimeMs)
        assertEquals(15_000L, backing.syllables.last().endTimeMs)
    }

    @Test
    fun transformIsIdempotent() {
        val document = LyricsDocument(
            lines = listOf(
                line(10_000, 3_000, "主歌词"),
                line(14_000, 2_000, "（伴唱）"),
            ),
            source = LyricSource.QQMusic,
        )
        val once = document.withQqBracketBackingVocals()
        assertEquals(once, once.withQqBracketBackingVocals())
    }
}
