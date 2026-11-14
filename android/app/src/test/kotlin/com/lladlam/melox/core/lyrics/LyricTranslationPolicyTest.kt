package com.lladlam.melox.core.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricTranslationPolicyTest {
    private fun doc(
        vararg lines: Triple<String, Long, String?>,
        source: LyricSource = LyricSource.Netease,
    ) = LyricsDocument(
        lines = lines.map { (text, time, translation) ->
            LyricLine(timeMs = time, text = text, translation = translation)
        },
        source = source,
    )

    // ------------------------------------------------------------------ detect

    @Test
    fun detectsChineseFromHanCharacters() {
        assertEquals("zh", LyricTranslationPolicy.detectLanguage(listOf("夜空中最亮的星")))
    }

    @Test
    fun detectsJapaneseFromKana() {
        assertEquals("ja", LyricTranslationPolicy.detectLanguage(listOf("君の名前を呼んでいる")))
    }

    @Test
    fun detectsKoreanFromHangul() {
        assertEquals("ko", LyricTranslationPolicy.detectLanguage(listOf("너의 이름을 부르고 있어")))
    }

    @Test
    fun detectsEnglishFromLatin() {
        assertEquals("en", LyricTranslationPolicy.detectLanguage(listOf("We were both young")))
    }

    @Test
    fun returnsNullWhenSignalIsWeak() {
        assertNull(LyricTranslationPolicy.detectLanguage(listOf("♪ ♪ ♪")))
    }

    // ------------------------------------------------------------------ decide

    @Test
    fun sameLanguageNeedsNoTranslation() {
        assertEquals(
            LyricTranslationDecision.KeepMainOnly,
            LyricTranslationPolicy.decide("en", MeloXLyricPreference("en", MeloXLyricScript.Original)),
        )
    }

    @Test
    fun chineseScriptsAreOnlyAScriptConversion() {
        assertEquals(
            LyricTranslationDecision.ConvertScriptOnly,
            LyricTranslationPolicy.decide("zh", MeloXLyricPreference(null, MeloXLyricScript.Traditional)),
        )
    }

    @Test
    fun differentLanguageNeedsTranslation() {
        assertEquals(
            LyricTranslationDecision.NeedTranslation,
            LyricTranslationPolicy.decide("ja", MeloXLyricPreference("zh-Hans", MeloXLyricScript.Simplified)),
        )
    }

    @Test
    fun chineseTargetIsTreatedAsChinese() {
        // A Chinese UI listens to a Japanese song → translation required.
        assertEquals(
            LyricTranslationDecision.NeedTranslation,
            LyricTranslationPolicy.decide("ja", MeloXLyricPreference(null, MeloXLyricScript.Simplified)),
        )
    }

    // --------------------------------------------------------- translation match

    @Test
    fun englishTranslationMatchesEnglishTarget() {
        assertTrue(
            LyricTranslationPolicy.translationMatches(
                listOf("We were both young when I first saw you"),
                MeloXLyricPreference("en", MeloXLyricScript.Original),
            ),
        )
    }

    @Test
    fun chineseTranslationDoesNotMatchEnglishTarget() {
        assertFalse(
            LyricTranslationPolicy.translationMatches(
                listOf("当我第一次看见你的时候"),
                MeloXLyricPreference("en", MeloXLyricScript.Original),
            ),
        )
    }

    @Test
    fun emptyTranslationNeverMatches() {
        assertFalse(
            LyricTranslationPolicy.translationMatches(
                listOf("", "   "),
                MeloXLyricPreference("en", MeloXLyricScript.Original),
            ),
        )
    }

    // ---------------------------------------------------------------- combine

    @Test
    fun combineFillsOnlyMissingTranslationsOnAlignedTimeline() {
        val main = doc(
            Triple("line one", 1_000L, null),
            Triple("line two", 2_000L, "already here"),
        )
        val other = doc(
            Triple("one", 1_010L, "译文一"),
            Triple("two", 2_010L, "译文二"),
        )
        val merged = LyricTranslationPolicy.combineTranslations(main, other)
        assertEquals("译文一", merged?.lines?.get(0)?.translation)
        assertEquals("already here", merged?.lines?.get(1)?.translation)
    }

    @Test
    fun combineRejectsTimelinesThatDisagreeOnLineCount() {
        val main = doc(
            Triple("a", 1_000L, null),
            Triple("b", 2_000L, null),
            Triple("c", 3_000L, null),
        )
        val other = doc(Triple("x", 1_000L, "译"))
        assertNull(LyricTranslationPolicy.combineTranslations(main, other))
    }

    @Test
    fun combineRejectsWhenTooFewLinesMatch() {
        val main = doc(
            Triple("a", 1_000L, null),
            Triple("b", 5_000L, null),
            Triple("c", 9_000L, null),
        )
        val other = doc(
            Triple("x", 1_000L, "译"),
            Triple("y", 5_000L, "译"),
            Triple("z", 9_000L, "译"),
        )
        // Only one of three lines is inside the ±120ms tolerance.
        val shifted = other.copy(
            lines = other.lines.mapIndexed { index, line ->
                if (index == 0) line else line.copy(timeMs = line.timeMs + 2_000L)
            },
        )
        assertNull(LyricTranslationPolicy.combineTranslations(main, shifted))
    }

    // ------------------------------------------------- unified post-processing

    @Test
    fun sameLanguageDocumentLosesItsTranslation() {
        val document = doc(
            Triple("hello world", 1_000L, "你好世界"),
        )
        val result = com.lladlam.melox.ui.player.applyLyricTranslationPreference(
            document,
            MeloXLyricPreference("en", MeloXLyricScript.Original),
        )
        assertNull(result.lines.single().translation)
    }

    @Test
    fun mismatchedTranslationIsDropped() {
        val document = doc(
            Triple("夜空中最亮的星", 1_000L, "The brightest star"),
        )
        val result = com.lladlam.melox.ui.player.applyLyricTranslationPreference(
            document,
            MeloXLyricPreference("en", MeloXLyricScript.Original),
        )
        // Main is Chinese, listener wants English. The bundled translation is
        // Chinese→English, which is correct here, so it must survive.
        assertEquals("The brightest star", result.lines.single().translation)

        val wrong = doc(Triple("夜空中最亮的星", 1_000L, "夜空中最亮的星（译）"))
        val dropped = com.lladlam.melox.ui.player.applyLyricTranslationPreference(
            wrong,
            MeloXLyricPreference("en", MeloXLyricScript.Original),
        )
        assertNull(dropped.lines.single().translation)
    }
}
