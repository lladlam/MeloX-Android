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

    @Test
    fun japaneseAndKoreanSettingsKeepTheirMainLyrics() {
        val japanese = MeloXLyricPreference.fromSetting("ja")
        assertEquals("ja", japanese.languageTag)
        assertEquals(
            LyricTranslationDecision.KeepMainOnly,
            LyricTranslationPolicy.decide("ja", japanese),
        )
        assertEquals(
            LyricTranslationDecision.NeedTranslation,
            LyricTranslationPolicy.decide("zh", japanese),
        )

        val korean = MeloXLyricPreference.fromSetting("ko")
        assertEquals("ko", korean.languageTag)
        assertEquals(
            LyricTranslationDecision.KeepMainOnly,
            LyricTranslationPolicy.decide("ko", korean),
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
    fun wordTimedMainBorrowsTranslationsFromALongerLineTimedTrack() {
        // Mirrors the real AMLL/Netease case: 48 word-timed main lines vs a
        // 62-line translation track, with a constant ~150–230 ms lead-in.
        val main = doc(
            Triple("僕らを包んで", 500L, null),
            Triple("粉々になる前に", 3_410L, null),
            Triple("頼りなくてもいい", 6_220L, null),
            Triple("その手を", 8_130L, null),
            Triple("この手は", 10_920L, null),
            Triple("自分自身のものさ", 19_310L, null),
        )
        val other = doc(
            Triple("x0", 278L, "残存的心跳"),
            Triple("x1", 3_233L, "它在彻底粉碎前"),
            Triple("x2", 6_074L, "这只手 哪怕不可靠也没关系"),
            Triple("x3", 7_973L, "这双手就属于自己"),
            Triple("x4", 10_692L, "一切并非无法改变"),
            Triple("x5", 19_000L, "伸出手吧"),
            Triple("x6", 22_000L, "额外的翻译行"),
            Triple("x7", 25_000L, "额外的翻译行"),
        )

        val merged = LyricTranslationPolicy.combineTranslations(main, other)

        // The median offset aligns most lines; one late line may stay outside
        // the ±120 ms window, which the 60% ratio permits.
        assertTrue((merged?.lines?.count { !it.translation.isNullOrBlank() } ?: 0) >= 5)
        assertEquals("这只手 哪怕不可靠也没关系", merged?.lines?.get(2)?.translation)
    }

    @Test
    fun constantLeadInOffsetIsEstimatedBeforeMatching() {
        val main = doc(
            Triple("a", 1_000L, null),
            Triple("b", 3_000L, null),
            Triple("c", 5_000L, null),
            Triple("d", 7_000L, null),
            Triple("e", 9_000L, null),
        )
        val other = doc(
            Triple("x", 820L, "译1"),
            Triple("y", 2_820L, "译2"),
            Triple("z", 4_820L, "译3"),
            Triple("w", 6_820L, "译4"),
            Triple("v", 8_820L, "译5"),
        )

        val merged = LyricTranslationPolicy.combineTranslations(main, other)

        assertEquals(listOf("译1", "译2", "译3", "译4", "译5"), merged?.lines?.map { it.translation })
    }

    @Test
    fun combineRejectsWhenTooFewLinesMatch() {
        val main = doc(
            Triple("a", 1_000L, null),
            Triple("b", 5_000L, null),
            Triple("c", 9_000L, null),
            Triple("d", 13_000L, null),
            Triple("e", 17_000L, null),
        )
        val other = doc(
            Triple("x", 1_000L, "译"),
            Triple("y", 7_000L, "译"),
            Triple("z", 11_000L, "译"),
            Triple("w", 15_000L, "译"),
            Triple("v", 19_000L, "译"),
        )
        // Only one of five lines is inside the ±120ms tolerance.
        assertNull(LyricTranslationPolicy.combineTranslations(main, other))
    }

    @Test
    fun exactTextFillsLinesEvenWhenTimingIsFarApart() {
        val main = doc(
            Triple("頼りなくてもいい その手を", 6_220L, null),
            Triple("この手は 自分自身のものさ", 8_130L, null),
            Triple("変わらないはずはないよ 手を伸ばして", 10_920L, null),
            Triple("雑踏の中で 声無き声で泣いている", 19_310L, null),
        )
        val other = doc(
            Triple("頼りなくてもいい その手を", 60_074L, "这只手 哪怕不可靠也没关系"),
            Triple("この手は 自分自身のものさ", 60_973L, "这双手就属于自己"),
            Triple("変わらないはずはないよ 手を伸ばして", 62_000L, "一切并非无法改变 伸出手吧"),
            Triple("雑踏の中で 声無き声で泣いている", 63_000L, "我在拥挤的人群中无声地哭泣着"),
        )

        val merged = LyricTranslationPolicy.combineTranslations(main, other)

        assertEquals("这只手 哪怕不可靠也没关系", merged?.lines?.get(0)?.translation)
        assertEquals(4, merged?.lines?.count { !it.translation.isNullOrBlank() })
    }

    @Test
    fun combineRejectsUnrelatedTimelinesEvenWithoutLineCountGuard() {
        val main = doc(
            Triple("a", 1_000L, null),
            Triple("b", 3_000L, null),
            Triple("c", 5_000L, null),
            Triple("d", 7_000L, null),
            Triple("e", 9_000L, null),
        )
        val other = doc(
            Triple("x", 101_000L, "译"),
            Triple("y", 103_000L, "译"),
            Triple("z", 105_000L, "译"),
            Triple("w", 107_000L, "译"),
            Triple("v", 109_000L, "译"),
        )
        assertNull(LyricTranslationPolicy.combineTranslations(main, other))
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
