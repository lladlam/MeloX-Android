package com.lladlam.melox.core.lyrics

/**
 * QQ Music sometimes returns backing vocals as ordinary lines wrapped in
 * parentheses, for example:
 *
 * ```
 * [00:10.00] 主歌词
 * [00:14.00] （伴唱）
 * ```
 *
 * With this transform the bracket line is removed from the main timeline and
 * attached to the previous line as [LyricLine.accompaniment]. Its new window
 * starts when the previous (lead) line ends, while its end time stays the one
 * the provider originally authored. Consecutive bracket lines are placed one
 * after another instead of overlapping.
 *
 * Only the outer pair is removed; the provider's inner text is kept verbatim.
 */
fun LyricsDocument.withQqBracketBackingVocals(): LyricsDocument {
    if (lines.size < 2) return this
    val output = ArrayList<LyricLine>(lines.size)
    var lastBackingEndMs = Long.MIN_VALUE
    lines.forEachIndexed { index, line ->
        val inner = line.bracketInnerText()
        val parent = output.lastOrNull()
        if (inner == null || parent == null) {
            output += line
            lastBackingEndMs = Long.MIN_VALUE
            return@forEachIndexed
        }
        val parentEnd = parent.timeMs + (parent.durationMs?.takeIf { it > 0L }
            ?: (line.timeMs - parent.timeMs).coerceAtLeast(1L))
        val originalEnd = line.timeMs + (line.durationMs?.takeIf { it > 0L }
            ?: lines.getOrNull(index + 1)?.timeMs?.minus(line.timeMs)?.takeIf { it > 0L }
            ?: fallbackDurationMs(line))
        val start = maxOf(parentEnd, lastBackingEndMs)
        if (originalEnd <= start) {
            output += line
            lastBackingEndMs = Long.MIN_VALUE
            return@forEachIndexed
        }
        val accompaniment = LyricAccompaniment(
            timeMs = start,
            durationMs = originalEnd - start,
            text = inner,
            syllables = line.syllables.withoutOuterBrackets().shiftedTo(line.timeMs, start, originalEnd),
            translation = line.translation,
            romanization = line.romanization,
            // Keep the lead line's alignment; a backing vocal should not jump
            // to the other side just because the provider tagged the bracket
            // line with a different agent.
            agent = parent.agent,
            timingKind = line.timingKind,
        )
        output[output.lastIndex] = parent.copy(
            accompaniment = parent.accompaniment + accompaniment,
        )
        lastBackingEndMs = originalEnd
    }
    return copy(lines = output)
}

/** Returns the inner text for a line whose whole text is one bracket pair. */
private fun LyricLine.bracketInnerText(): String? {
    val trimmed = text.trim()
    if (trimmed.length < 3) return null
    val opens = trimmed.first() == '(' || trimmed.first() == '（'
    val closes = trimmed.last() == ')' || trimmed.last() == '）'
    if (!opens || !closes) return null
    return trimmed.substring(1, trimmed.length - 1).trim().takeIf(String::isNotBlank)
}

/** Removes one outer ASCII/full-width bracket pair when the whole text is wrapped. */
internal fun String.withoutOuterBrackets(): String {
    val trimmed = trim()
    if (trimmed.length < 2) return this
    val opens = trimmed.first() == '(' || trimmed.first() == '（'
    val closes = trimmed.last() == ')' || trimmed.last() == '）'
    return if (opens && closes) trimmed.substring(1, trimmed.length - 1).trim() else this
}

/** Removes the outer brackets from the first/last authored syllables as well. */
internal fun List<LyricSyllable>.withoutOuterBrackets(): List<LyricSyllable> {
    if (isEmpty()) return this
    val joined = joinToString("") { it.text }
    if (joined.withoutOuterBrackets() == joined) return this
    val result = toMutableList()
    val firstIndex = result.indexOfFirst { it.text.isNotBlank() }
    if (firstIndex >= 0) {
        val syllable = result[firstIndex]
        val text = syllable.text.trimStart()
        result[firstIndex] = syllable.copy(
            text = text.removePrefix("(").removePrefix("（"),
        )
    }
    val lastIndex = result.indexOfLast { it.text.isNotBlank() }
    if (lastIndex >= 0) {
        val syllable = result[lastIndex]
        val text = syllable.text.trimEnd()
        result[lastIndex] = syllable.copy(
            text = text.removeSuffix(")").removeSuffix("）"),
        )
    }
    return result
}

private fun fallbackDurationMs(line: LyricLine): Long =
    (line.text.length * 320L).coerceIn(800L, 8_000L)

/**
 * Moves authored syllable timing into the new accompaniment window. Relative
 * offsets are preserved; the final syllable is clamped to the authored end.
 */
private fun List<LyricSyllable>.shiftedTo(
    oldStartMs: Long,
    newStartMs: Long,
    newEndMs: Long,
): List<LyricSyllable> {
    if (isEmpty()) return emptyList()
    val delta = newStartMs - oldStartMs
    return map { syllable ->
        val start = (syllable.startTimeMs + delta).coerceIn(newStartMs, newEndMs - 1)
        val end = (syllable.endTimeMs + delta).coerceIn(start + 1, newEndMs)
        syllable.copy(startTimeMs = start, endTimeMs = end)
    }
}
