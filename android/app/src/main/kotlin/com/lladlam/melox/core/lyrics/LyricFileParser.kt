package com.lladlam.melox.core.lyrics

import java.util.Base64

/** Shared entry point for local sidecars and custom-source lyric payloads. */
object LyricFileParser {
    fun parse(bytes: ByteArray): LyricsDocument {
        if (bytes.size >= 4 && bytes.take(4) == listOf(107.toByte(), 114.toByte(), 99.toByte(), 49.toByte())) {
            return KugouKrcLyricsParser.decodeAndParse(Base64.getEncoder().encodeToString(bytes))
                .copy(source = LyricSource.Local).withoutPlaceholderLyrics()
        }
        return parse(bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF"))
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
