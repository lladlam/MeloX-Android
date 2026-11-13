package com.lladlam.melox.core.lyrics

import com.lladlam.melox.core.network.readBoundedBytes
import java.io.ByteArrayInputStream
import java.util.Base64
import java.util.zip.InflaterInputStream

/** Hard cap on decompressed lyric text; generous for any real document. */
private const val MAX_DECOMPRESSED_LYRIC_BYTES = 4 * 1024 * 1024

/** Decodes Kugou KRC and maps its relative word timing into MeloX lyric models. */
object KugouKrcLyricsParser {
    private val xorKey = byteArrayOf(
        64, 71, 97, 119, 94, 50, 116, 71,
        81, 54, 49, 45, 206.toByte(), 210.toByte(), 110, 105,
    )
    private val lineTiming = Regex("^\\[(\\d+),(\\d+)](.*)$")
    private val wordTiming = Regex("<(\\d+),(\\d+),(?:\\d+)>")

    fun decodeBase64(value: String): String {
        if (value.isBlank()) return ""
        return runCatching {
            val encrypted = Base64.getDecoder().decode(value)
            if (encrypted.size <= 4) return@runCatching ""
            val compressed = encrypted.copyOfRange(4, encrypted.size)
            for (index in compressed.indices) {
                compressed[index] = (compressed[index].toInt() xor xorKey[index % xorKey.size].toInt()).toByte()
            }
            InflaterInputStream(ByteArrayInputStream(compressed)).use { stream ->
                stream.readBoundedBytes(MAX_DECOMPRESSED_LYRIC_BYTES).toString(Charsets.UTF_8)
            }
        }.getOrDefault("")
    }

    fun parse(value: String): LyricsDocument {
        val source = value.trim()
        if (source.isBlank()) return LyricsDocument(emptyList())
        val lines = buildList {
            for (raw in source.lineSequence()) {
                val match = lineTiming.find(raw.trim()) ?: continue
                val lineStart = match.groupValues[1].toLongOrNull() ?: continue
                val lineDuration = match.groupValues[2].toLongOrNull()?.coerceAtLeast(1L) ?: continue
                val content = match.groupValues[3]
                val timingMatches = wordTiming.findAll(content).toList()
                if (timingMatches.isEmpty()) {
                    val text = content.trim()
                    if (text.isNotBlank()) {
                        add(LyricLine(lineStart, lineDuration, text))
                    }
                    continue
                }

                val syllables = buildList {
                    for ((index, timing) in timingMatches.withIndex()) {
                        val offset = timing.groupValues[1].toLongOrNull() ?: continue
                        val duration = timing.groupValues[2].toLongOrNull()?.coerceAtLeast(1L) ?: continue
                        val textStart = timing.range.last + 1
                        val textEnd = timingMatches.getOrNull(index + 1)?.range?.first ?: content.length
                        if (textEnd < textStart) continue
                        val text = content.substring(textStart, textEnd)
                        if (text.isEmpty()) continue
                        val start = lineStart + offset
                        add(
                            LyricSyllable(
                                text = text,
                                startTimeMs = start,
                                endTimeMs = start + duration,
                            ),
                        )
                    }
                }
                val text = syllables.joinToString("") { it.text }.trim()
                if (text.isNotBlank()) {
                    add(
                        LyricLine(
                            timeMs = lineStart,
                            durationMs = lineDuration,
                            text = text,
                            syllables = syllables,
                        ),
                    )
                }
            }
        }.sortedBy(LyricLine::timeMs)

        if (lines.isNotEmpty()) return LyricsDocument(
            lines, source = LyricSource.Kugou,
            quality = if (lines.any { it.syllables.isNotEmpty() }) LyricQuality.WordSynchronized else LyricQuality.LineSynchronized,
            pseudoTimingAllowed = false,
        )
        return LrcLyricsParser.parse(source).copy(source = LyricSource.Kugou)
    }

    fun decodeAndParse(base64: String): LyricsDocument = parse(decodeBase64(base64))
}
