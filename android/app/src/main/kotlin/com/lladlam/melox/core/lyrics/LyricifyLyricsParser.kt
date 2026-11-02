package com.lladlam.melox.core.lyrics

/** Format reference: WXRIW/Lyricify-Lyrics-Helper, Apache-2.0; Kotlin implementation. */
object LyricifyLyricsParser {
    private val linesTiming = Regex("^\\[(-?\\d+),(-?\\d+)](.*)$")
    private val syllableLine = Regex("^\\[(\\d+)](.*)$")
    private val syllable = Regex("(.*?)\\((\\d+),(\\d+)\\)")
    private val offsetTag = Regex("(?im)^\\[offset:([+-]?\\d+)]\\s*$")

    fun parseLines(value: String): LyricsDocument {
        val offset = offsetTag.find(value)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val lines = value.lineSequence().mapNotNull { raw ->
            val match = linesTiming.matchEntire(raw.trim()) ?: return@mapNotNull null
            val start = match.groupValues[1].toLongOrNull() ?: return@mapNotNull null
            val end = match.groupValues[2].toLongOrNull() ?: return@mapNotNull null
            if (end < start) return@mapNotNull null
            LyricLine(start - offset, end - start, match.groupValues[3].trim(),
                timingKind = LyricTimingKind.LineSynchronized)
        }.sortedBy { it.timeMs }.toList()
        return LyricsDocument(lines, quality = LyricQuality.LineSynchronized, pseudoTimingAllowed = false)
    }

    fun parseSyllable(value: String): LyricsDocument {
        val offset = offsetTag.find(value)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val lines = mutableListOf<LyricLine>()
        for (raw in value.lineSequence()) {
            val match = syllableLine.matchEntire(raw.trim()) ?: continue
            val properties = match.groupValues[1].toIntOrNull() ?: continue
            if (properties !in 0..8) continue
            val syllables = syllable.findAll(match.groupValues[2]).mapNotNull { word ->
                val start = word.groupValues[2].toLongOrNull() ?: return@mapNotNull null
                val duration = word.groupValues[3].toLongOrNull() ?: return@mapNotNull null
                if (duration > Long.MAX_VALUE - start) return@mapNotNull null
                LyricSyllable(word.groupValues[1], start - offset, start + duration - offset)
            }.toList()
            if (syllables.isEmpty()) continue
            val start = syllables.minOf { it.startTimeMs }
            val end = syllables.maxOf { it.endTimeMs }
            val agent = when (properties % 3) {
                1 -> LyricAgent("left", "", LyricAgentAlignment.Normal)
                2 -> LyricAgent("right", "", LyricAgentAlignment.Flipped)
                else -> null
            }
            val line = LyricLine(start, end - start, syllables.joinToString("") { it.text },
                syllables = syllables, agent = agent)
            if (properties >= 6 && lines.isNotEmpty()) {
                val previous = lines.last()
                lines[lines.lastIndex] = previous.copy(accompaniment = previous.accompaniment +
                    LyricAccompaniment(start, end - start, line.text, syllables, agent = agent))
            } else {
                lines += line
            }
        }
        return LyricsDocument(lines.sortedBy { it.timeMs }, quality = LyricQuality.WordSynchronized,
            pseudoTimingAllowed = false)
    }

    fun isSyllable(value: String): Boolean = value.lineSequence().any { raw ->
        val line = syllableLine.matchEntire(raw.trim()) ?: return@any false
        line.groupValues[1].toIntOrNull() in 0..8 && syllable.containsMatchIn(line.groupValues[2])
    }

    fun isLines(value: String): Boolean = value.contains("[type:LyricifyLines]", ignoreCase = true) ||
        value.lineSequence().any { linesTiming.matches(it.trim()) } &&
        !value.contains(Regex("\\(\\d+,\\d+\\)|<\\d+,\\d+,\\d+>"))
}
