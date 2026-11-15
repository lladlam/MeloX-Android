package com.lladlam.melox.core.lyrics

/**
 * Decides whether a lyric document needs a translation, and combines a
 * translation track from another source when the timelines line up.
 *
 * Rules (2026-11-15):
 *  - Main lyric language == the requested lyric language → no translation.
 *  - Chinese Simplified / Traditional are the *same* language: only the script
 *    conversion runs, a translation would be redundant.
 *  - Any other mismatch → a translation in the requested language is required.
 *  - A translation whose detected language differs from the request is dropped
 *    instead of being shown as if it were the requested language.
 */
internal enum class LyricTranslationDecision {
    /** Main lyric already is in the requested language: keep it, drop translation. */
    KeepMainOnly,

    /** Same language, different Chinese script: convert glyphs, drop translation. */
    ConvertScriptOnly,

    /** Different language: a translation matching the request may be used. */
    NeedTranslation,
}

internal object LyricTranslationPolicy {
    /** Line-start tolerance when merging a translation track from another source. */
    const val COMBINE_TOLERANCE_MS = 120L

    /** At least this share of the main lines must find a partner line. */
    const val COMBINE_MIN_MATCH_RATIO = 0.6

    /**
     * Translation-only merges are allowed to fill a smaller share of the
     * document: a word-timed main lyric and a line-timed translation track
     * routinely split lines differently, so demanding 60% would discard a
     * perfectly aligned first verse (the real AMLL/Netease case). Every line
     * that is filled still has to pass the ±120 ms (after offset) or exact-text
     * guard.
     */
    private const val COMBINE_MIN_TRANSLATION_MATCH_RATIO = 0.3

    /**
     * Maximum constant lead-in offset estimated between the main lyric and a
     * translation track. Different masters routinely differ by 100–300 ms;
     * anything beyond this is treated as a different timeline.
     */
    private const val MAX_TRANSLATION_OFFSET_MS = 1_500L

    private val kana = Regex("[\\u3040-\\u30FF]")
    private val hangul = Regex("[\\uAC00-\\uD7AF\\u1100-\\u11FF]")
    private val han = Regex("[\\u4E00-\\u9FFF\\u3400-\\u4DBF]")
    private val latin = Regex("[A-Za-z]")

    /**
     * Very small script-based language detector. Lyrics are short and code-mixed,
     * so this intentionally answers only the languages the app can act on and
     * returns null when the signal is weak.
     */
    fun detectLanguage(texts: List<String>): String? {
        val joined = texts.joinToString(" ")
        if (joined.isBlank()) return null
        val kanaCount = kana.findAll(joined).count()
        val hangulCount = hangul.findAll(joined).count()
        val hanCount = han.findAll(joined).count()
        val latinCount = latin.findAll(joined).count()
        val cjkTotal = kanaCount + hanCount
        // Japanese almost always carries kana; a kanji-only line is treated as
        // Chinese unless kana is meaningfully present.
        if (kanaCount >= 2 && kanaCount * 4 >= cjkTotal) return "ja"
        if (hangulCount >= 2 && hangulCount >= latinCount) return "ko"
        if (hanCount >= 2) return "zh"
        if (latinCount >= 3) return "en"
        return null
    }

    fun detectLanguage(document: LyricsDocument): String? =
        detectLanguage(document.lines.mapNotNull { it.text.takeIf(String::isNotBlank) })

    /** Language the requested preference points at, as a base tag. */
    fun targetLanguage(preference: MeloXLyricPreference): String =
        preference.languageTag ?: "zh"

    fun decide(mainLanguage: String?, target: MeloXLyricPreference): LyricTranslationDecision {
        val wanted = targetLanguage(target)
        // Unknown source language: do not guess. Keep whatever translation the
        // source supplied and let [translationMatches] validate it.
        if (mainLanguage == null) return LyricTranslationDecision.NeedTranslation
        if (wanted == "zh" && mainLanguage == "zh") {
            return LyricTranslationDecision.ConvertScriptOnly
        }
        return if (mainLanguage == wanted) {
            LyricTranslationDecision.KeepMainOnly
        } else {
            LyricTranslationDecision.NeedTranslation
        }
    }

    /**
     * True when a translation is usable for [target]. Unknown languages are
     * accepted (best effort); a confidently different language is rejected.
     */
    fun translationMatches(translations: List<String>, target: MeloXLyricPreference): Boolean {
        val texts = translations.filter(String::isNotBlank)
        if (texts.isEmpty()) return false
        val detected = detectLanguage(texts) ?: return true
        return detected == targetLanguage(target)
    }

    /**
     * Merges the translation track of [alternative] into [main] when both
     * documents describe the same timeline.
     *
     * The main lyric may be word-timed while the translation track is
     * line-timed, so their line counts can differ substantially — for example
     * an AMLL word-timed lyric with 48 lines and a NetEase LRC translation
     * with 62. Line-count equality is therefore not a valid guard here. A
     * constant lead-in offset between the two masters is estimated first (AMLL
     * and NetEase commonly differ by 100–300 ms), then matching uses the
     * normal ±120 ms tolerance and the 60% match ratio.
     *
     * Only lines that currently have no translation are filled, and the whole
     * result is discarded when the timelines disagree — a mis-timed translation
     * is worse than none. Main text, timing and syllables are never replaced.
     */
    fun combineTranslations(
        main: LyricsDocument,
        alternative: LyricsDocument,
        toleranceMs: Long = COMBINE_TOLERANCE_MS,
    ): LyricsDocument? {
        if (main.lines.isEmpty() || alternative.lines.isEmpty()) return null
        val translated = alternative.lines.filter { !it.translation.isNullOrBlank() }
        if (translated.isEmpty()) return null
        val offsetMs = estimateTranslationOffset(main.lines, translated)
        val adjusted = translated.map { it.copy(timeMs = it.timeMs - offsetMs) }
        val partners = adjusted.associateBy { it.timeMs }
        // Exact lyric text is a second, timing-independent signal. It catches
        // sections where the two sources split a long line differently but the
        // Japanese text is identical.
        val textPartners = translated
            .mapNotNull { line ->
                normalizeLyricText(line.text)
                    .takeIf { it.length > 2 }
                    ?.let { text -> text to line }
            }
            .toMap()

        var filled = 0
        var matched = 0
        val merged = main.lines.map { line ->
            if (!line.translation.isNullOrBlank()) return@map line
            if (line.text.isBlank()) return@map line
            val timePartner = partners[line.timeMs]
                ?: adjusted.minByOrNull { kotlin.math.abs(it.timeMs - line.timeMs) }
                    ?.takeIf { kotlin.math.abs(it.timeMs - line.timeMs) <= toleranceMs }
            val textPartner = textPartners[normalizeLyricText(line.text)]
            val partner = timePartner ?: textPartner
            if (partner == null) {
                return@map line
            }
            matched++
            val text = partner.translation ?: return@map line
            filled++
            line.copy(translation = text)
        }
        if (filled == 0) return null
        // The ratio is measured against the lines that actually needed a
        // translation, not against every line in the document.
        val candidates = main.lines.count { it.text.isNotBlank() && it.translation.isNullOrBlank() }
        if (candidates > 0 && matched.toFloat() / candidates < COMBINE_MIN_TRANSLATION_MATCH_RATIO) {
            return null
        }
        return main.copy(lines = merged)
    }

    private fun normalizeLyricText(text: String): String = text
        .trim()
        .replace(Regex("\\s+"), "")
        .replace(Regex("[，。！？、,.!?…~～]"), "")

    /**
     * Median constant offset between the main line starts and their nearest
     * translated line starts. Returns 0 for short documents or when fewer than
     * a third of the lines agree on a plausible offset, so unrelated timelines
     * keep the strict no-offset comparison.
     */
    private fun estimateTranslationOffset(
        main: List<LyricLine>,
        translated: List<LyricLine>,
    ): Long {
        if (main.size < 4 || translated.size < 4) return 0L
        val deltas = main.mapNotNull { line ->
            val nearest = translated.minByOrNull { kotlin.math.abs(it.timeMs - line.timeMs) }
                ?: return@mapNotNull null
            val delta = nearest.timeMs - line.timeMs
            delta.takeIf { kotlin.math.abs(it) <= MAX_TRANSLATION_OFFSET_MS }
        }.sorted()
        if (deltas.size < minOf(main.size, translated.size) / 3) return 0L
        return deltas[deltas.size / 2]
    }

    /** Strips every translation, used when no usable translation remains. */
    fun withoutTranslations(document: LyricsDocument): LyricsDocument = document.copy(
        lines = document.lines.map { line ->
            line.copy(
                translation = null,
                accompaniment = line.accompaniment.map { it.copy(translation = null) },
            )
        },
    )
}
