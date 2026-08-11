package com.lladlam.melox.core.lyrics

import java.text.BreakIterator
import java.util.Locale

data class LyricSyllable(
    val text: String,
    val startTimeMs: Long,
    val endTimeMs: Long,
)

data class LyricLine(
    val timeMs: Long,
    val durationMs: Long? = null,
    val text: String,
    val syllables: List<LyricSyllable> = emptyList(),
    val translation: String? = null,
    val romanization: String? = null,
    val romanizationSyllables: List<LyricSyllable> = emptyList(),
)

data class LyricsDocument(
    val lines: List<LyricLine>,
) {
    fun highlightedIndex(positionMs: Long): Int? {
        if (lines.isEmpty()) return null
        var low = 0
        var high = lines.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (lines[mid].timeMs <= positionMs) {
                low = mid + 1
            } else {
                high = mid
            }
        }
        return (low - 1).takeIf { it >= 0 }
    }
}

/**
 * Gives ordinary LRC lines a stable grapheme timeline so the same renderer can
 * animate both YRC and line-timed lyrics. Real YRC syllables are never replaced.
 */
fun LyricsDocument.withPseudoTiming(): LyricsDocument = copy(
    lines = lines.mapIndexed { index, line ->
        if (line.syllables.isNotEmpty() || line.text.isBlank()) return@mapIndexed line
        val iterator = BreakIterator.getCharacterInstance(Locale.ROOT).apply { setText(line.text) }
        val graphemes = buildList {
            var start = iterator.first()
            var end = iterator.next()
            while (end != BreakIterator.DONE) {
                add(line.text.substring(start, end))
                start = end
                end = iterator.next()
            }
        }
        if (graphemes.isEmpty()) return@mapIndexed line
        val nextStart = lines.getOrNull(index + 1)?.timeMs
        val duration = (line.durationMs ?: nextStart?.minus(line.timeMs) ?: 3_000L)
            .coerceIn(400L, 12_000L)
        val weights = graphemes.map { if (it.isBlank()) 0.35 else 1.0 }
        val totalWeight = weights.sum().coerceAtLeast(1.0)
        var consumed = 0.0
        line.copy(
            syllables = graphemes.mapIndexed { graphemeIndex, text ->
                val startMs = line.timeMs + (duration * consumed / totalWeight).toLong()
                consumed += weights[graphemeIndex]
                val endMs = if (graphemeIndex == graphemes.lastIndex) {
                    line.timeMs + duration
                } else {
                    line.timeMs + (duration * consumed / totalWeight).toLong()
                }
                LyricSyllable(text, startMs, endMs.coerceAtLeast(startMs + 1L))
            },
        )
    },
)

object NeteaseLyricParser {
    private const val ANNOTATION_TOLERANCE_MS = 750L
    private val lrcTimestamp = Regex("\\[(\\d+):(\\d+(?:[.:]\\d+)?)\\]")
    private val yrcSyllableTiming = Regex("\\((\\d+),(\\d+),(\\d+)\\)")

    fun parse(
        yrc: String,
        lrc: String,
        translatedYrc: String = "",
        translatedLrc: String = "",
        romanizedYrc: String = "",
        romanizedLrc: String = "",
    ): LyricsDocument {
        val yrcLines = parseYrc(yrc)
        val lrcLines = parseLrc(lrc)
        val primary = if (yrcLines.isNotEmpty()) yrcLines else lrcLines
        if (primary.isEmpty()) return LyricsDocument(emptyList())

        val translated = selectSecondary(translatedYrc, translatedLrc)
        val romanized = selectSecondary(romanizedYrc, romanizedLrc)

        return LyricsDocument(
            primary.map { line ->
                val translationLine = nearestSecondary(line, translated)
                val romanizationLine = nearestSecondary(line, romanized)
                line.copy(
                    translation = annotationText(line, translationLine),
                    romanization = annotationText(line, romanizationLine),
                    romanizationSyllables = romanizationLine?.syllables.orEmpty(),
                )
            },
        )
    }

    fun parseLrc(source: String): List<LyricLine> {
        val result = mutableListOf<LyricLine>()
        for (raw in source.lineSequence()) {
            val matches = lrcTimestamp.findAll(raw).toList()
            if (matches.isEmpty()) continue
            val text = raw.substring(matches.last().range.last + 1).trim()
            if (text.isBlank()) continue

            for (match in matches) {
                val minutes = match.groupValues[1].toLongOrNull() ?: continue
                val seconds = match.groupValues[2]
                    .replace(':', '.')
                    .toDoubleOrNull() ?: continue
                result += LyricLine(
                    timeMs = ((minutes * 60.0 + seconds) * 1_000.0).toLong(),
                    text = text,
                )
            }
        }
        return inferDurations(result.sortedBy { it.timeMs })
    }

    fun parseYrc(source: String): List<LyricLine> {
        val result = mutableListOf<LyricLine>()
        for (raw in source.lineSequence()) {
            val line = raw.trim()
            if (!line.startsWith('[')) continue
            val close = line.indexOf(']')
            if (close <= 1) continue

            val timing = line.substring(1, close).split(',')
            val startMs = timing.getOrNull(0)?.toLongOrNull() ?: continue
            val durationMs = timing.getOrNull(1)?.toLongOrNull()
            val content = line.substring(close + 1)
            val matches = yrcSyllableTiming.findAll(content).toList()

            if (matches.isEmpty()) {
                val text = content.trim()
                if (text.isBlank()) continue
                result += LyricLine(
                    timeMs = startMs,
                    durationMs = durationMs,
                    text = text,
                )
                continue
            }

            val syllables = buildList {
                for ((index, match) in matches.withIndex()) {
                    val syllableStartMs = match.groupValues[1].toLongOrNull() ?: continue
                    val syllableDurationMs = match.groupValues[2].toLongOrNull() ?: continue
                    val textStart = match.range.last + 1
                    val textEnd = matches.getOrNull(index + 1)?.range?.first ?: content.length
                    if (textEnd < textStart) continue
                    val syllableText = content.substring(textStart, textEnd)
                    if (syllableText.isEmpty()) continue
                    add(
                        LyricSyllable(
                            text = syllableText,
                            startTimeMs = syllableStartMs,
                            endTimeMs = syllableStartMs + syllableDurationMs.coerceAtLeast(1L),
                        ),
                    )
                }
            }

            val text = syllables.joinToString("") { it.text }.trim()
            if (text.isBlank()) continue
            result += LyricLine(
                timeMs = startMs,
                durationMs = durationMs,
                text = text,
                syllables = syllables,
            )
        }
        return result.sortedBy { it.timeMs }
    }

    private fun selectSecondary(yrc: String, lrc: String): List<LyricLine> {
        val synchronized = parseYrc(yrc)
        return if (synchronized.isNotEmpty()) synchronized else parseLrc(lrc)
    }

    private fun nearestSecondary(
        target: LyricLine,
        candidates: List<LyricLine>,
    ): LyricLine? {
        if (candidates.isEmpty()) return null
        val candidate = candidates.minByOrNull { kotlin.math.abs(it.timeMs - target.timeMs) }
            ?: return null
        if (kotlin.math.abs(candidate.timeMs - target.timeMs) > ANNOTATION_TOLERANCE_MS) {
            return null
        }
        return candidate
    }

    private fun annotationText(target: LyricLine, candidate: LyricLine?): String? {
        val text = candidate?.text?.trim().orEmpty()
        return text.takeIf { it.isNotBlank() && it != target.text.trim() }
    }

    private fun inferDurations(lines: List<LyricLine>): List<LyricLine> =
        lines.mapIndexed { index, line ->
            if (line.durationMs != null) return@mapIndexed line
            val next = lines.getOrNull(index + 1)?.timeMs
            val duration = next
                ?.let { (it - line.timeMs).coerceAtLeast(100L) }
                ?: (line.text.length * 320L).coerceIn(2_000L, 8_000L)
            line.copy(durationMs = duration)
        }
}
