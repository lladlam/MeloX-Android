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

    /** The two documents must not disagree by more than this many lines. */
    const val COMBINE_MAX_LINE_DELTA = 1

    /** At least this share of the main lines must find a partner line. */
    const val COMBINE_MIN_MATCH_RATIO = 0.6

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
     * Only lines that currently have no translation are filled, and the whole
     * result is discarded when the timelines disagree — a mis-timed translation
     * is worse than none.
     */
    fun combineTranslations(
        main: LyricsDocument,
        alternative: LyricsDocument,
        toleranceMs: Long = COMBINE_TOLERANCE_MS,
    ): LyricsDocument? {
        if (main.lines.isEmpty() || alternative.lines.isEmpty()) return null
        if (kotlin.math.abs(main.lines.size - alternative.lines.size) > COMBINE_MAX_LINE_DELTA) return null
        val translated = alternative.lines.filter { !it.translation.isNullOrBlank() }
        if (translated.isEmpty()) return null
        val partners = translated.associateBy { it.timeMs }

        var filled = 0
        var matched = 0
        val merged = main.lines.map { line ->
            if (!line.translation.isNullOrBlank()) return@map line
            if (line.text.isBlank()) return@map line
            val partner = partners[line.timeMs]
                ?: translated.minByOrNull { kotlin.math.abs(it.timeMs - line.timeMs) }
                    ?.takeIf { kotlin.math.abs(it.timeMs - line.timeMs) <= toleranceMs }
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
        if (candidates > 0 && matched.toFloat() / candidates < COMBINE_MIN_MATCH_RATIO) {
            return null
        }
        return main.copy(lines = merged)
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
