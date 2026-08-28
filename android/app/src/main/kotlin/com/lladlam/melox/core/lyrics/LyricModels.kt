package com.lladlam.melox.core.lyrics

import java.text.BreakIterator
import java.util.Locale

data class LyricSyllable(
    val text: String,
    val startTimeMs: Long,
    val endTimeMs: Long,
)

/** Authored word timing and LRC-inferred display durations must not be conflated. */
enum class LyricTimingKind { Precise, LineSynchronized }

enum class LyricHighlightStrategy {
    /**
     * The active line is the one whose start time has passed. This matches most
     * line-level renderers and is the historical default.
     */
    LineStart,

    /**
     * The active line only changes once its first authored syllable has started.
     * Providers that return a lead-in marker before the first word (common in
     * YRC/QRC/TTML) therefore keep the previous line highlighted until the vocal
     * actually begins.
     */
    FirstSyllableOrLineStart,
}

data class LyricAgent(
    val id: String,
    val name: String,
    val alignment: LyricAgentAlignment = LyricAgentAlignment.Normal,
)

enum class LyricAgentAlignment { Normal, Flipped }

data class LyricAccompaniment(
    val timeMs: Long,
    val durationMs: Long? = null,
    val text: String,
    val syllables: List<LyricSyllable> = emptyList(),
    val translation: String? = null,
    val romanization: String? = null,
    val agent: LyricAgent? = null,
    val timingKind: LyricTimingKind = LyricTimingKind.Precise,
)

enum class LyricSource { Netease, QQMusic, Kugou, AppleMusic, AmlL, Local }

enum class LyricQuality { Fallback, LineSynchronized, WordSynchronized, Authored }

data class LyricLine(
    val timeMs: Long,
    val durationMs: Long? = null,
    val text: String,
    val syllables: List<LyricSyllable> = emptyList(),
    val translation: String? = null,
    val romanization: String? = null,
    val romanizationSyllables: List<LyricSyllable> = emptyList(),
    val agent: LyricAgent? = null,
    val timingKind: LyricTimingKind = LyricTimingKind.Precise,
    val accompaniment: List<LyricAccompaniment> = emptyList(),
)

data class LyricsDocument(
    val lines: List<LyricLine>,
    val source: LyricSource = LyricSource.Local,
    val quality: LyricQuality = LyricQuality.Fallback,
    /** Whether line-timed lyrics may be expanded into synthetic per-grapheme timing. */
    val pseudoTimingAllowed: Boolean = true,
) {
    fun highlightedIndex(positionMs: Long, strategy: LyricHighlightStrategy = LyricHighlightStrategy.LineStart): Int? {
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
        val index = (low - 1).takeIf { it >= 0 } ?: return null
        if (strategy != LyricHighlightStrategy.FirstSyllableOrLineStart || index == 0) return index
        val line = lines[index]
        val firstSyllable = line.syllables.firstOrNull()
            ?: line.romanizationSyllables.firstOrNull()
        val vocalStart = firstSyllable?.startTimeMs?.takeIf { it >= 0L } ?: line.timeMs
        return if (positionMs >= vocalStart) index else (index - 1).coerceAtLeast(0)
    }
}

/**
 * Gives ordinary LRC lines a stable grapheme timeline so the same renderer can
 * animate both YRC and line-timed lyrics. Real YRC syllables are never replaced.
 *
 * Provider fallbacks can opt out with [LyricsDocument.pseudoTimingAllowed]. This
 * prevents a plain LRC fallback from becoming an expensive fake word-by-word
 * document when the provider did not actually return word timing.
 */
fun LyricsDocument.withPseudoTiming(): LyricsDocument {
    if (!pseudoTimingAllowed) return this
    return copy(
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
}

object NeteaseLyricParser {
    // Translation LRCs from different providers are often authored against a
    // slightly different vocal onset than the primary YRC.  A 750ms window
    // drops otherwise valid foreign-language annotations line by line.
    private const val ANNOTATION_TOLERANCE_MS = 1_500L
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
        val alignedTranslations = alignSecondary(primary, translated)
        val alignedRomanizations = alignSecondary(primary, romanized)

        return LyricsDocument(
            primary.mapIndexed { index, line ->
                val translationLine = alignedTranslations.getOrNull(index)
                val romanizationLine = alignedRomanizations.getOrNull(index)
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
                    timingKind = LyricTimingKind.LineSynchronized,
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
            if (line.startsWith("[bg:", ignoreCase = true)) {
                val close = line.indexOf(']')
                if (close > 4 && result.isNotEmpty()) {
                    val timing = line.substring(4, close).split(',')
                    val start = timing.getOrNull(0)?.toLongOrNull() ?: continue
                    val duration = timing.getOrNull(1)?.toLongOrNull()?.coerceAtLeast(1L) ?: continue
                    val content = line.substring(close + 1)
                    val parsed = parseYrc("[$start,$duration]$content").firstOrNull() ?: continue
                    val parent = result.last()
                    result[result.lastIndex] = parent.copy(
                        accompaniment = parent.accompaniment + LyricAccompaniment(
                            timeMs = parsed.timeMs,
                            durationMs = parsed.durationMs,
                            text = parsed.text,
                            syllables = parsed.syllables,
                            agent = parent.agent,
                            timingKind = parsed.timingKind,
                        ),
                    )
                }
                continue
            }
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
                    timingKind = LyricTimingKind.LineSynchronized,
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
                timingKind = LyricTimingKind.Precise,
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

    internal fun alignSecondary(
        primary: List<LyricLine>,
        candidates: List<LyricLine>,
    ): List<LyricLine?> {
        if (primary.isEmpty() || candidates.isEmpty()) return List(primary.size) { null }
        val offset = estimateSecondaryOffset(primary, candidates)
        val adjusted = candidates.map { it.shiftBy(-offset) }
        val result = MutableList<LyricLine?>(primary.size) { null }
        var cursor = 0
        for ((index, target) in primary.withIndex()) {
            while (cursor + 1 < adjusted.size &&
                kotlin.math.abs(adjusted[cursor + 1].timeMs - target.timeMs) <=
                kotlin.math.abs(adjusted[cursor].timeMs - target.timeMs)
            ) {
                cursor++
            }
            val candidate = adjusted[cursor]
            if (kotlin.math.abs(candidate.timeMs - target.timeMs) <= ANNOTATION_TOLERANCE_MS) {
                result[index] = candidate
            }
            if (cursor < adjusted.lastIndex) cursor++
        }
        return result
    }

    private fun estimateSecondaryOffset(
        primary: List<LyricLine>,
        candidates: List<LyricLine>,
    ): Long {
        val differences = if (primary.size == candidates.size) {
            primary.indices.map { candidates[it].timeMs - primary[it].timeMs }
        } else {
            primary.mapNotNull { target ->
                candidates.minByOrNull { kotlin.math.abs(it.timeMs - target.timeMs) }
                    ?.let { candidate ->
                        (candidate.timeMs - target.timeMs).takeIf { kotlin.math.abs(it) <= 5_000L }
                    }
            }
        }.sorted()
        return differences.getOrNull(differences.size / 2) ?: 0L
    }

    private fun LyricLine.shiftBy(deltaMs: Long): LyricLine = copy(
        timeMs = timeMs + deltaMs,
        syllables = syllables.map { it.copy(startTimeMs = it.startTimeMs + deltaMs, endTimeMs = it.endTimeMs + deltaMs) },
        romanizationSyllables = romanizationSyllables.map {
            it.copy(startTimeMs = it.startTimeMs + deltaMs, endTimeMs = it.endTimeMs + deltaMs)
        },
        accompaniment = accompaniment.map { accompaniment ->
            accompaniment.copy(
                timeMs = accompaniment.timeMs + deltaMs,
                syllables = accompaniment.syllables.map {
                    it.copy(startTimeMs = it.startTimeMs + deltaMs, endTimeMs = it.endTimeMs + deltaMs)
                },
            )
        },
    )

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
