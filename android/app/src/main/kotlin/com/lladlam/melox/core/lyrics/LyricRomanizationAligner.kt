package com.lladlam.melox.core.lyrics

import java.text.BreakIterator
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class LyricRubyUnit(
    val originalText: String,
    val originalSyllables: List<LyricSyllable>,
    val romanizationText: String?,
)

/** Aligns NetEase yromalrc/romalrc fragments with the primary timed lyric. */
object LyricRomanizationAligner {
    fun units(line: LyricLine): List<LyricRubyUnit> {
        val romanization = line.romanization?.trim().orEmpty()
        if (romanization.isBlank()) return emptyList()

        timedUnits(line)?.let { return it }

        val tokens = romanization.split(Regex("\\s+")).filter(String::isNotBlank)
        if (tokens.isNotEmpty() && line.syllables.size == tokens.size) {
            return line.syllables.zip(tokens) { syllable, token ->
                LyricRubyUnit(syllable.text, listOf(syllable), token)
            }
        }

        val graphemes = graphemes(line.text)
        val phonetic = graphemes.filterNot(String::isBlank)
        if (tokens.isNotEmpty() && phonetic.size == tokens.size) {
            var tokenIndex = 0
            return graphemes.map { original ->
                if (original.isBlank()) LyricRubyUnit(original, emptyList(), null)
                else LyricRubyUnit(original, emptyList(), tokens[tokenIndex++])
            }
        }

        return listOf(LyricRubyUnit(line.text, line.syllables, romanization))
    }

    private fun timedUnits(line: LyricLine): List<LyricRubyUnit>? {
        val originals = line.syllables.filter { it.text.isNotEmpty() }
        val romanized = line.romanizationSyllables.filter { it.text.isNotBlank() }
        if (originals.isEmpty() || romanized.isEmpty()) return null

        if (originals.size == romanized.size) {
            return originals.zip(romanized) { original, ruby ->
                LyricRubyUnit(original.text, listOf(original), ruby.text.trim())
            }
        }

        val groups = linkedMapOf<Int, MutableList<LyricSyllable>>()
        originals.forEach { original ->
            val match = romanized.indices.minByOrNull { temporalDistance(original, romanized[it]) }
                ?: return@forEach
            groups.getOrPut(match) { mutableListOf() }.add(original)
        }
        val indices = groups.keys.toList()
        if (indices != indices.sorted() || indices.toSet() != romanized.indices.toSet()) return null
        return groups.map { (romanizationIndex, originalGroup) ->
            LyricRubyUnit(
                originalText = originalGroup.joinToString("") { it.text },
                originalSyllables = originalGroup,
                romanizationText = romanized[romanizationIndex].text.trim(),
            )
        }
    }

    private fun temporalDistance(left: LyricSyllable, right: LyricSyllable): Long {
        val overlap = min(left.endTimeMs, right.endTimeMs) - max(left.startTimeMs, right.startTimeMs)
        if (overlap >= 0L) {
            val leftMid = (left.startTimeMs + left.endTimeMs) / 2L
            val rightMid = (right.startTimeMs + right.endTimeMs) / 2L
            return abs(leftMid - rightMid) / 6L
        }
        return max(left.startTimeMs, right.startTimeMs) - min(left.endTimeMs, right.endTimeMs)
    }

    private fun graphemes(text: String): List<String> {
        val iterator = BreakIterator.getCharacterInstance(Locale.ROOT).apply { setText(text) }
        return buildList {
            var start = iterator.first()
            var end = iterator.next()
            while (end != BreakIterator.DONE) {
                add(text.substring(start, end))
                start = end
                end = iterator.next()
            }
        }
    }
}
