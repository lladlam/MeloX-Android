package com.lladlam.melox.core.lyrics

/**
 * Normalizes provider lyric data once before it reaches any renderer and offers
 * event-driven timeline queries without depending on Android or Compose clocks.
 */
object LyricTimelineProcessor {
    const val Version = 1
    private const val DefaultLastLineDurationMs = 3_000L

    fun process(document: LyricsDocument): LyricsDocument {
        if (document.lines.isEmpty()) return document
        val sorted = document.lines.withIndex()
            .sortedWith(compareBy<IndexedValue<LyricLine>> { it.value.timeMs.coerceAtLeast(0L) }.thenBy { it.index })
            .map(IndexedValue<LyricLine>::value)
        val lines = sorted.mapIndexed { index, line ->
            val start = line.timeMs.coerceAtLeast(0L)
            val nextStart = sorted.getOrNull(index + 1)?.timeMs?.coerceAtLeast(start)
            val inferredDuration = line.durationMs
                ?.takeIf { it > 0L }
                ?: nextStart?.minus(start)?.takeIf { it > 0L }
                ?: DefaultLastLineDurationMs
            val authoredEnd = (line.syllables + line.romanizationSyllables)
                .maxOfOrNull(LyricSyllable::endTimeMs)
                ?: start
            val duration = maxOf(inferredDuration, authoredEnd - start)
            line.copy(
                timeMs = start,
                durationMs = duration,
                syllables = normalizeSyllables(line.syllables),
                romanizationSyllables = normalizeSyllables(line.romanizationSyllables),
                accompaniment = line.accompaniment.map { accompaniment ->
                    val accompanimentStart = accompaniment.timeMs.coerceAtLeast(0L)
                    accompaniment.copy(
                        timeMs = accompanimentStart,
                        durationMs = accompaniment.durationMs?.takeIf { it > 0L },
                        syllables = normalizeSyllables(accompaniment.syllables),
                    )
                }.sortedBy(LyricAccompaniment::timeMs),
            )
        }
        return if (lines == document.lines) document else document.copy(lines = lines)
    }

    fun lineIndexAt(
        document: LyricsDocument,
        positionMs: Long,
        strategy: LyricHighlightStrategy = LyricHighlightStrategy.LineStart,
    ): Int? = document.highlightedIndex(positionMs, strategy)

    fun activeTimedLineIndexes(document: LyricsDocument, positionMs: Long): Set<Int> =
        document.lines.mapIndexedNotNull { index, line ->
            index.takeIf {
                positionMs >= line.timeMs && positionMs < effectiveLineEndMs(line)
            }
        }.toSet()

    private fun effectiveLineEndMs(line: LyricLine): Long {
        val durationEnd = line.durationMs?.takeIf { it > 0L }?.let { line.timeMs + it } ?: line.timeMs
        val syllableEnd = line.syllables.maxOfOrNull(LyricSyllable::endTimeMs) ?: line.timeMs
        val accompanimentEnd = line.accompaniment.maxOfOrNull { accompaniment ->
            maxOf(
                accompaniment.durationMs?.takeIf { it > 0L }
                    ?.let { accompaniment.timeMs + it } ?: accompaniment.timeMs,
                accompaniment.syllables.maxOfOrNull(LyricSyllable::endTimeMs) ?: accompaniment.timeMs,
            )
        } ?: line.timeMs
        return maxOf(durationEnd, syllableEnd, accompanimentEnd).coerceAtLeast(line.timeMs + 1L)
    }

    /** Absolute song position of the next line or word boundary, if one exists. */
    fun nextEventTimeMs(document: LyricsDocument, positionMs: Long): Long? {
        if (document.lines.isEmpty()) return null
        var next = Long.MAX_VALUE
        fun consider(value: Long) {
            if (value > positionMs && value < next) next = value
        }
        document.lines.forEach { line ->
            consider(line.timeMs)
            if (line.durationMs != null || line.syllables.isNotEmpty() || line.accompaniment.isNotEmpty()) {
                consider(effectiveLineEndMs(line))
            }
            line.syllables.forEach { syllable ->
                consider(syllable.startTimeMs)
                consider(syllable.endTimeMs)
            }
            line.accompaniment.forEach { accompaniment ->
                consider(accompaniment.timeMs)
                accompaniment.syllables.forEach { syllable ->
                    consider(syllable.startTimeMs)
                    consider(syllable.endTimeMs)
                }
            }
        }
        return next.takeUnless { it == Long.MAX_VALUE }
    }

    /**
     * Returns all typed events that are active at [positionMs] and the absolute
     * time of the next event, matching the Apple Music lyric engine's five
     * callback categories (main line, main word, background line, background word).
     */
    fun eventsAt(document: LyricsDocument, positionMs: Long): LyricEventSnapshot {
        val active = mutableListOf<LyricEvent>()
        document.lines.forEachIndexed { lineIndex, line ->
            if (line.timeMs <= positionMs) {
                active += LyricEvent.LineEnter(line.timeMs, lineIndex, line)
            }
            line.syllables.forEachIndexed { wordIndex, syllable ->
                if (syllable.startTimeMs <= positionMs) {
                    active += LyricEvent.WordEnter(syllable.startTimeMs, lineIndex, wordIndex, syllable)
                }
            }
            line.accompaniment.forEachIndexed { accompanimentIndex, accompaniment ->
                if (accompaniment.timeMs <= positionMs) {
                    active += LyricEvent.AccompanimentLineEnter(accompaniment.timeMs, lineIndex, accompanimentIndex, accompaniment)
                }
                accompaniment.syllables.forEachIndexed { wordIndex, syllable ->
                    if (syllable.startTimeMs <= positionMs) {
                        active += LyricEvent.AccompanimentWordEnter(syllable.startTimeMs, lineIndex, accompanimentIndex, wordIndex, syllable)
                    }
                }
            }
        }
        return LyricEventSnapshot(active, nextEventTimeMs(document, positionMs))
    }

    private fun normalizeSyllables(values: List<LyricSyllable>): List<LyricSyllable> =
        values.withIndex()
            .map { indexed ->
                val start = indexed.value.startTimeMs.coerceAtLeast(0L)
                indexed.index to indexed.value.copy(
                    startTimeMs = start,
                    endTimeMs = indexed.value.endTimeMs.coerceAtLeast(start + 1L),
                )
            }
            .sortedWith(compareBy<Pair<Int, LyricSyllable>> { it.second.startTimeMs }.thenBy { it.first })
            .map(Pair<Int, LyricSyllable>::second)
}

sealed class LyricEvent {
    abstract val timeMs: Long

    data class LineEnter(override val timeMs: Long, val lineIndex: Int, val line: LyricLine) : LyricEvent()
    data class WordEnter(override val timeMs: Long, val lineIndex: Int, val wordIndex: Int, val word: LyricSyllable) : LyricEvent()
    data class AccompanimentLineEnter(
        override val timeMs: Long,
        val lineIndex: Int,
        val accompanimentIndex: Int,
        val accompaniment: LyricAccompaniment,
    ) : LyricEvent()

    data class AccompanimentWordEnter(
        override val timeMs: Long,
        val lineIndex: Int,
        val accompanimentIndex: Int,
        val wordIndex: Int,
        val word: LyricSyllable,
    ) : LyricEvent()
}

data class LyricEventSnapshot(
    val activeEvents: List<LyricEvent>,
    val nextEventTimeMs: Long?,
)
