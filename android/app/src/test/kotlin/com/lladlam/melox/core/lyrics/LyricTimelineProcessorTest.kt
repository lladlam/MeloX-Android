package com.lladlam.melox.core.lyrics

import com.lladlam.melox.playback.MeloXAutoMixMode
import com.lladlam.melox.playback.MeloXAutoMixPlanner
import com.lladlam.melox.playback.MeloXAutoMixSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricTimelineProcessorTest {
    @Test
    fun processSortsLinesAndRepairsMissingDurations() {
        val document = LyricsDocument(
            lines = listOf(
                LyricLine(2_000L, text = "two"),
                LyricLine(-100L, text = "first"),
            ),
        )
        val processed = LyricTimelineProcessor.process(document)
        assertEquals(listOf("first", "two"), processed.lines.map { it.text })
        assertEquals(2_000L, processed.lines.first().durationMs)
        assertEquals(3_000L, processed.lines.last().durationMs)
    }

    @Test
    fun processRepairsSyllableBoundsAndIsIdempotent() {
        val document = LyricsDocument(
            lines = listOf(
                LyricLine(
                    0L,
                    text = "word",
                    syllables = listOf(LyricSyllable("word", -20L, -20L)),
                ),
            ),
        )
        val processed = LyricTimelineProcessor.process(document)
        assertEquals(0L, processed.lines.single().syllables.single().startTimeMs)
        assertEquals(1L, processed.lines.single().syllables.single().endTimeMs)
        assertEquals(processed, LyricTimelineProcessor.process(processed))
    }

    @Test
    fun longSyllableExtendsLineAndRemainsActiveAcrossNextLine() {
        val document = LyricTimelineProcessor.process(
            LyricsDocument(
                lines = listOf(
                    LyricLine(
                        1_000L,
                        durationMs = 2_000L,
                        text = "啊",
                        syllables = listOf(LyricSyllable("啊", 1_000L, 6_000L)),
                    ),
                    LyricLine(
                        4_000L,
                        text = "下一句",
                        syllables = listOf(LyricSyllable("下一句", 4_000L, 4_500L)),
                    ),
                ),
            ),
        )

        assertEquals(5_000L, document.lines.first().durationMs)
        assertEquals(setOf(0, 1), LyricTimelineProcessor.activeTimedLineIndexes(document, 4_200L))
        assertEquals(setOf(0, 1), LyricTimelineProcessor.activeTimedLineIndexes(document, 5_500L))
    }

    @Test
    fun authoredLineRangesKeepMultipleOrdinaryTimedLinesActive() {
        val document = LyricTimelineProcessor.process(
            LyricsDocument(
                lines = listOf(
                    LyricLine(1_000L, durationMs = 3_500L, text = "第一句", syllables = listOf(LyricSyllable("第一句", 1_000L, 2_000L))),
                    LyricLine(3_000L, durationMs = 2_000L, text = "第二句", syllables = listOf(LyricSyllable("第二句", 3_200L, 4_000L))),
                ),
            ),
        )

        assertEquals(setOf(0, 1), LyricTimelineProcessor.activeTimedLineIndexes(document, 3_500L))
        assertEquals(setOf(1), LyricTimelineProcessor.activeTimedLineIndexes(document, 5_000L - 1L))
        assertTrue(LyricTimelineProcessor.activeTimedLineIndexes(document, 5_000L).isEmpty())
        assertEquals(5_000L, LyricTimelineProcessor.nextEventTimeMs(document, 4_500L))
    }

    @Test
    fun anyNumberOfOverlappingTimedLinesRemainActiveTogether() {
        val document = LyricsDocument(
            lines = (0 until 4).map { index ->
                LyricLine(
                    timeMs = index * 500L,
                    durationMs = 4_000L,
                    text = "line $index",
                )
            },
        )

        assertEquals(
            setOf(0, 1, 2, 3),
            LyricTimelineProcessor.activeTimedLineIndexes(document, 1_500L),
        )
    }

    @Test
    fun nextEventReturnsNextLineOrWordBoundary() {
        val document = LyricsDocument(
            lines = listOf(
                LyricLine(0L, text = "one", syllables = listOf(LyricSyllable("one", 100L, 300L))),
                LyricLine(1_000L, text = "two"),
            ),
        )
        assertEquals(100L, LyricTimelineProcessor.nextEventTimeMs(document, 0L))
        assertEquals(300L, LyricTimelineProcessor.nextEventTimeMs(document, 150L))
        assertEquals(1_000L, LyricTimelineProcessor.nextEventTimeMs(document, 500L))
        assertTrue(LyricTimelineProcessor.nextEventTimeMs(document, 1_000L) == null)
    }

    @Test
    fun highlightStrategyLineStartUsesLineTime() {
        val document = LyricsDocument(
            lines = listOf(
                LyricLine(0L, text = "first"),
                LyricLine(1_000L, text = "second", syllables = listOf(LyricSyllable("second", 1_200L, 1_500L))),
            ),
        )
        assertEquals(0, document.highlightedIndex(500L, LyricHighlightStrategy.LineStart))
        assertEquals(1, document.highlightedIndex(1_050L, LyricHighlightStrategy.LineStart))
    }

    @Test
    fun highlightStrategyFirstSyllableDelaysUntilVocal() {
        val document = LyricsDocument(
            lines = listOf(
                LyricLine(0L, text = "first"),
                LyricLine(1_000L, text = "second", syllables = listOf(LyricSyllable("second", 1_200L, 1_500L))),
            ),
        )
        assertEquals(0, document.highlightedIndex(1_050L, LyricHighlightStrategy.FirstSyllableOrLineStart))
        assertEquals(1, document.highlightedIndex(1_250L, LyricHighlightStrategy.FirstSyllableOrLineStart))
    }

    @Test
    fun processNormalizesAccompanimentAndReturnsItsEvents() {
        val document = LyricsDocument(
            lines = listOf(
                LyricLine(
                    0L,
                    text = "lead",
                    accompaniment = listOf(
                        LyricAccompaniment(timeMs = -50L, text = "bg", syllables = listOf(LyricSyllable("bg", -30L, -10L))),
                    ),
                ),
                LyricLine(2_000L, text = "next"),
            ),
        )
        val processed = LyricTimelineProcessor.process(document)
        val accompaniment = processed.lines.first().accompaniment.first()
        assertEquals(0L, accompaniment.timeMs)
        assertEquals(0L, accompaniment.syllables.first().startTimeMs)
        assertEquals(1L, accompaniment.syllables.first().endTimeMs)
        assertEquals(0L, LyricTimelineProcessor.nextEventTimeMs(processed, -1L))
        assertEquals(2_000L, LyricTimelineProcessor.nextEventTimeMs(processed, 1L))
    }

    @Test
    fun fallbackCrossfadeProducesTransitionWhenRemainingIsShort() {
        val settings = MeloXAutoMixSettings(
            mode = MeloXAutoMixMode.Smart,
            fixedDurationMs = 8_000L,
            tailCutBars = 4,
        )
        val plan = MeloXAutoMixPlanner.plan(settings, outgoingRemainingMs = 9_000L)
        assertTrue("fallback should produce a transition", plan.performsTransition)
        assertTrue(plan.durationMs >= MeloXAutoMixPlanner.MIN_DURATION_MS)
    }

    @Test
    fun eventsAtReturnsTypedActiveEventsAndNextDeadline() {
        val document = LyricsDocument(
            lines = listOf(
                LyricLine(
                    0L,
                    text = "hello",
                    syllables = listOf(LyricSyllable("he", 0L, 200L), LyricSyllable("llo", 200L, 500L)),
                    accompaniment = listOf(
                        LyricAccompaniment(
                            timeMs = 0L,
                            text = "echo",
                            syllables = listOf(LyricSyllable("e", 50L, 150L)),
                        ),
                    ),
                ),
                LyricLine(1_000L, text = "world"),
            ),
        )
        val snapshot = LyricTimelineProcessor.eventsAt(document, 100L)
        assertEquals(4, snapshot.activeEvents.size)
        assertTrue(snapshot.activeEvents.any { it is LyricEvent.LineEnter && it.lineIndex == 0 })
        assertTrue(snapshot.activeEvents.any { it is LyricEvent.AccompanimentLineEnter && it.accompaniment.text == "echo" })
        assertTrue(snapshot.activeEvents.any { it is LyricEvent.WordEnter && it.word.text == "he" })
        assertTrue(snapshot.activeEvents.any { it is LyricEvent.AccompanimentWordEnter && it.word.text == "e" })
        assertEquals(150L, snapshot.nextEventTimeMs)
    }
}
