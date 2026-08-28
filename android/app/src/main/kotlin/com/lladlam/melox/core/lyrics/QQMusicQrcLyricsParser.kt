package com.lladlam.melox.core.lyrics

import java.io.ByteArrayInputStream
import java.util.zip.InflaterInputStream
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * QQ Music QRC decoder/parser.
 *
 * Current QQ clients normally return QRC/translation/romanization as hex-encoded,
 * 3DES-encrypted zlib payloads. Some gateways/versions can already return decoded
 * XML or line text, so the parser deliberately accepts both forms.
 */
object QQMusicQrcLyricsParser {
    private val tripleDesKey = "!@#)(*$%123ZXC!@!@#)(NHL".toByteArray(Charsets.US_ASCII)
    // qrcDecrypt's published QRC transform is not a single DESede operation:
    // it performs D(KEY1) -> E(KEY2) -> D(KEY3), then zlib inflates the result.
    // Keep the former DESede path below as a fallback for gateway variants.
    private val qrcKey1 = "!@#)(NHL".toByteArray(Charsets.US_ASCII)
    private val qrcKey2 = "123ZXC!@".toByteArray(Charsets.US_ASCII)
    private val qrcKey3 = "!@#)(*$%".toByteArray(Charsets.US_ASCII)
    private val lineTiming = Regex("^\\[(\\d+),(\\d+)](.*)$")
    private val backgroundTiming = Regex("^\\[bg:(\\d+),(\\d+)](.*)$", RegexOption.IGNORE_CASE)
    private val wordTiming = Regex("\\((\\d+),(\\d+)\\)")
    private val lyricContent = Regex("LyricContent=\"(.*?)\"", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private const val AnnotationToleranceMs = 1_500L
    private const val OffsetConsistencyMs = 1_500L

    fun decryptHex(value: String): String {
        val normalized = value.trim()
        if (normalized.isBlank() || normalized.length % 2 != 0 || !normalized.all { it.isHexDigit() }) return ""
        val encrypted = runCatching {
            ByteArray(normalized.length / 2) { index ->
                normalized.substring(index * 2, index * 2 + 2).toInt(16).toByte()
            }
        }.getOrElse { return "" }
        return QQMusicMeiQrcDecoder.decodeLyric(normalized)
            .ifBlank { decodeCompressed(qrcDecrypt(encrypted)) }
            .ifBlank { decodeCompressed(legacyTripleDesDecrypt(encrypted)) }
    }

    private fun qrcDecrypt(encrypted: ByteArray): ByteArray = runCatching {
        val first = des(encrypted, qrcKey1, Cipher.DECRYPT_MODE)
        val second = des(first, qrcKey2, Cipher.ENCRYPT_MODE)
        des(second, qrcKey3, Cipher.DECRYPT_MODE)
    }.getOrDefault(ByteArray(0))

    private fun legacyTripleDesDecrypt(encrypted: ByteArray): ByteArray = runCatching {
        Cipher.getInstance("DESede/ECB/NoPadding").run {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(tripleDesKey, "DESede"))
            doFinal(encrypted)
        }
    }.getOrDefault(ByteArray(0))

    private fun des(input: ByteArray, key: ByteArray, mode: Int): ByteArray =
        Cipher.getInstance("DES/ECB/NoPadding").run {
            init(mode, SecretKeySpec(key, "DES"))
            doFinal(input)
        }

    private fun decodeCompressed(compressed: ByteArray): String {
        if (compressed.isEmpty()) return ""
        return runCatching {
            InflaterInputStream(ByteArrayInputStream(compressed)).use { stream ->
                stream.readBytes().toString(Charsets.UTF_8)
            }.trimEnd('\u0000')
        }.getOrDefault("")
    }

    fun parseEncrypted(
        qrcHex: String,
        translationHex: String = "",
        romanizationHex: String = "",
    ): LyricsDocument = parse(
        primary = decodePayload(qrcHex),
        translation = decodePayload(translationHex),
        romanization = decodePayload(romanizationHex),
    )

    fun parse(
        primary: String,
        translation: String = "",
        romanization: String = "",
    ): LyricsDocument {
        val primaryLines = parseQrcLines(extractLyricText(primary))
        if (primaryLines.isEmpty()) {
            return LrcLyricsParser.parse(
                lrc = extractLyricText(primary),
                translation = extractLyricText(translation),
                romanization = extractLyricText(romanization),
            )
        }
        val translated = parseQrcLines(extractLyricText(translation))
            .ifEmpty { NeteaseLyricParser.parseLrc(extractLyricText(translation)) }
        val romanized = parseQrcLines(extractLyricText(romanization))
            .ifEmpty { NeteaseLyricParser.parseLrc(extractLyricText(romanization)) }
        val alignedTranslations = NeteaseLyricParser.alignSecondary(primaryLines, translated)
        val alignedRomanizations = NeteaseLyricParser.alignSecondary(primaryLines, romanized)

        return LyricsDocument(
            lines = primaryLines.mapIndexed { index, line ->
                val translationLine = alignedTranslations.getOrNull(index)
                val romanizationLine = alignedRomanizations.getOrNull(index)
                line.copy(
                    translation = annotationText(line, translationLine),
                    romanization = annotationText(line, romanizationLine),
                    romanizationSyllables = romanizationLine?.syllables.orEmpty(),
                )
            },
            source = LyricSource.QQMusic,
            quality = LyricQuality.WordSynchronized,
        )
    }

    private fun decodePayload(value: String): String {
        val normalized = value.trim().trimEnd('\u0000')
        if (normalized.isBlank()) return ""
        // QQ changed translation delivery on some endpoints in 2026: the QRC
        // original can still be encrypted while translation is already plain LRC.
        if (normalized.startsWith('<') || normalized.startsWith('[')) return normalized
        return decryptHex(normalized)
    }

    private fun parseQrcLines(source: String): List<LyricLine> {
        val lines = mutableListOf<LyricLine>()
        var alignment = LyricAgentAlignment.Normal
        for (raw in source.lineSequence()) {
            val trimmed = raw.trim()
            val background = backgroundTiming.find(trimmed)
            if (background != null) {
                val start = background.groupValues[1].toLongOrNull() ?: continue
                val duration = background.groupValues[2].toLongOrNull()?.coerceAtLeast(1L) ?: continue
                val content = background.groupValues[3]
                val syllables = parseQrcSyllables(content, start)
                val text = syllables.joinToString("") { it.text }.ifBlank { content.trim() }
                if (text.isNotBlank() && lines.isNotEmpty()) {
                    val parent = lines.last()
                    lines[lines.lastIndex] = parent.copy(
                        accompaniment = parent.accompaniment + LyricAccompaniment(
                            timeMs = start,
                            durationMs = duration,
                            text = text,
                            syllables = syllables,
                            agent = parent.agent,
                        ),
                    )
                }
                continue
            }
            val match = lineTiming.find(trimmed) ?: continue
            val lineStart = match.groupValues[1].toLongOrNull() ?: continue
            val lineDuration = match.groupValues[2].toLongOrNull()?.coerceAtLeast(1L) ?: continue
            val content = match.groupValues[3]
            val syllables = parseQrcSyllables(content, lineStart)
            val text = syllables.joinToString("") { it.text }.ifBlank { content.trim() }.trim()
            if (text.isNotBlank()) {
                if (text.startsWith(":") || text.startsWith("：") || text.endsWith(":") || text.endsWith("：")) {
                    alignment = if (alignment == LyricAgentAlignment.Normal) LyricAgentAlignment.Flipped else LyricAgentAlignment.Normal
                }
                lines +=
                    LyricLine(
                        timeMs = lineStart,
                        durationMs = lineDuration,
                        text = text,
                        syllables = syllables,
                        agent = LyricAgent("qrc-${alignment.name}", alignment.name, alignment),
                    )
            }
        }
        return lines.sortedBy(LyricLine::timeMs)
    }

    private fun parseQrcSyllables(content: String, lineStart: Long): List<LyricSyllable> = buildList {
        val matches = wordTiming.findAll(content).toList()
        var textStart = 0
        for (timing in matches) {
            val textEnd = timing.range.first
            if (textEnd < textStart) continue
            val text = content.substring(textStart, textEnd)
            textStart = timing.range.last + 1
            if (text.isEmpty()) continue
            val rawStart = timing.groupValues[1].toLongOrNull() ?: continue
            val duration = timing.groupValues[2].toLongOrNull()?.coerceAtLeast(1L) ?: continue
            val start = if (rawStart < lineStart && lineStart > 0L) lineStart + rawStart else rawStart
            add(LyricSyllable(text, start, start + duration))
        }
    }

    private fun extractLyricText(source: String): String {
        val value = source.trim().trimEnd('\u0000')
        if (value.isBlank()) return ""
        val match = lyricContent.find(value)
        val content = match?.groupValues?.getOrNull(1) ?: value
        return unescapeXml(content)
            .replace("\\n", "\n")
            .replace("\\r", "\r")
    }

    private fun unescapeXml(value: String): String = value
        .replace("&#10;", "\n")
        .replace("&#13;", "\r")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&")

    private fun estimateGlobalOffset(
        primary: List<LyricLine>,
        candidates: List<LyricLine>,
    ): Long? {
        if (primary.isEmpty() || candidates.isEmpty() || kotlin.math.abs(primary.size - candidates.size) > 2) return null
        val count = minOf(primary.size, candidates.size, 6)
        if (count <= 0) return null
        val offsets = (0 until count)
            .map { index -> candidates[index].timeMs - primary[index].timeMs }
            .sorted()
        val median = offsets[offsets.size / 2]
        return median.takeIf { center ->
            offsets.all { offset -> kotlin.math.abs(offset - center) <= OffsetConsistencyMs }
        }
    }

    private fun alignedAnnotation(
        target: LyricLine,
        index: Int,
        primarySize: Int,
        candidates: List<LyricLine>,
        globalOffsetMs: Long?,
    ): LyricLine? {
        if (globalOffsetMs != null) {
            val expectedTime = target.timeMs + globalOffsetMs
            val shifted = candidates.minByOrNull { kotlin.math.abs(it.timeMs - expectedTime) }
            if (shifted != null && kotlin.math.abs(shifted.timeMs - expectedTime) <= AnnotationToleranceMs) {
                return shifted
            }
        }
        nearest(target, candidates)?.let { return it }
        if (candidates.isEmpty() || kotlin.math.abs(candidates.size - primarySize) > 2) return null
        return candidates.getOrNull(index)
    }

    private fun nearest(target: LyricLine, candidates: List<LyricLine>): LyricLine? {
        val candidate = candidates.minByOrNull { kotlin.math.abs(it.timeMs - target.timeMs) }
            ?: return null
        return candidate.takeIf { kotlin.math.abs(candidate.timeMs - target.timeMs) <= AnnotationToleranceMs }
    }

    private fun annotationText(target: LyricLine, candidate: LyricLine?): String? {
        val text = candidate?.text?.trim().orEmpty()
        return text.takeIf { it.isNotBlank() && it != target.text.trim() }
    }

    private fun Char.isHexDigit(): Boolean =
        this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'
}
