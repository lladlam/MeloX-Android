package com.lladlam.melox.ui.player

import com.lladlam.melox.core.lyrics.LyricLine
import com.lladlam.melox.core.lyrics.LyricSource
import com.lladlam.melox.core.lyrics.LyricsDocument
import com.lladlam.melox.core.lyrics.MeloXLyricPreference
import com.lladlam.melox.core.music.model.MusicResourceId
import com.lladlam.melox.core.music.model.MusicSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.util.Locale

class MeloXLyricLanguageRoutingTest {
    @Test
    fun changingTranslationLanguageCannotHitThePreviousLanguagesCache() {
        fun key(language: String) = lyricCacheKey(
            MusicResourceId(MusicSource.Netease, "123"), "Song", "Artist", 1000, true,
            preference = MeloXLyricPreference.fromSetting(language, Locale.ENGLISH),
        )
        assertNotEquals(key("en"), key("tr"))
        assertNotEquals(key("zh-Hans"), key("zh-Hant"))
        assertEquals(key("en"), key("en-US"))
    }

    @Test
    fun aChineseOnlyProviderDoesNotMasqueradeAsAnEnglishTranslation() {
        val line = LyricLine(0, text = "Original", translation = "中文翻译", romanization = "romanization")
        val result = applyLyricTranslationPreference(
            LyricsDocument(listOf(line), source = LyricSource.Netease),
            MeloXLyricPreference.fromSetting("en"),
        )
        assertEquals("Original", result.lines.single().text)
        assertEquals(null, result.lines.single().translation)
        assertEquals("romanization", result.lines.single().romanization)
    }

    @Test
    fun aSameLanguageTranslationIsDroppedInsteadOfDuplicatingTheLyric() {
        val document = LyricsDocument(listOf(LyricLine(0, text = "Original", translation = "Hello")), source = LyricSource.AmlL)
        val result = applyLyricTranslationPreference(document, MeloXLyricPreference.fromSetting("en"))
        // 2026-11-15: when the main lyric already is in the listener's language,
        // a translation would only repeat it, so it is not shown at all.
        assertEquals("Original", result.lines.single().text)
        assertEquals(null, result.lines.single().translation)
    }
}
