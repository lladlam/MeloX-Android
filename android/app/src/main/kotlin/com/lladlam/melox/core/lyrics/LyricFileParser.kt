package com.lladlam.melox.core.lyrics

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.util.Base64

/** Shared entry point for local sidecars and custom-source lyric payloads. */
object LyricFileParser {
    fun parse(bytes: ByteArray): LyricsDocument {
        if (bytes.size >= 4 && bytes.take(4) == listOf(107.toByte(), 114.toByte(), 99.toByte(), 49.toByte())) {
            return KugouKrcLyricsParser.decodeAndParse(Base64.getEncoder().encodeToString(bytes))
                .copy(source = LyricSource.Local).withoutPlaceholderLyrics()
        }
        return parse(decodeText(bytes).removePrefix("\uFEFF"))
    }

    /** Common Chinese sidecars are GBK/GB18030; only fall back when UTF-8 is invalid. */
    private fun decodeText(bytes: ByteArray): String = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (_: CharacterCodingException) {
        String(bytes, charset("GB18030"))
    }

    fun parse(text: String): LyricsDocument {
        val value = text.trim().removePrefix("\uFEFF")
        return when {
            value.contains(Regex("<(?:\\w+:)?tt(?:\\s|>)")) -> TtmlLyricsParser.parse(value)
            LyricifyLyricsParser.isSyllable(value) -> LyricifyLyricsParser.parseSyllable(value)
            LyricifyLyricsParser.isLines(value) -> LyricifyLyricsParser.parseLines(value)
            value.contains(Regex("<\\d+,\\d+,\\d+>")) -> KugouKrcLyricsParser.parse(value)
            value.contains(Regex("\\(\\d+,\\d+\\)")) -> QQMusicQrcLyricsParser.parse(value)
            else -> LrcLyricsParser.parse(value)
        }.copy(source = LyricSource.Local).withoutPlaceholderLyrics()
    }
}
