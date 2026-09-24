package com.lladlam.melox.core.lyrics

import android.icu.text.Transliterator
import android.os.Build
import java.util.Locale

enum class MeloXLyricScript { Original, Simplified, Traditional;

    companion object {
        fun fromSystem(locale: Locale = Locale.getDefault()): MeloXLyricScript {
            if (!locale.language.equals("zh", ignoreCase = true)) return Original
            val script = if (Build.VERSION.SDK_INT >= 21) locale.script else ""
            val region = locale.country
            return if (
                script.equals("Hant", ignoreCase = true) ||
                region.equals("TW", ignoreCase = true) ||
                region.equals("HK", ignoreCase = true) ||
                region.equals("MO", ignoreCase = true)
            ) Traditional else Simplified
        }
    }
}

object MeloXLyricScriptConverter {
    private val toSimplified by lazy {
        runCatching { Transliterator.getInstance("Traditional-Simplified") }.getOrNull()
    }
    private val toTraditional by lazy {
        runCatching { Transliterator.getInstance("Simplified-Traditional") }.getOrNull()
    }

    fun convert(document: LyricsDocument, script: MeloXLyricScript): LyricsDocument {
        if (script == MeloXLyricScript.Original || Build.VERSION.SDK_INT < 29) return document
        val converter = (if (script == MeloXLyricScript.Simplified) toSimplified else toTraditional)
            ?: return document
        return document.copy(lines = document.lines.map { it.convert(converter, document.source) })
    }

    private fun LyricLine.convert(converter: Transliterator, source: LyricSource): LyricLine = copy(
        text = converter.transliterate(text),
        translation = translation
            ?.takeIf { source != LyricSource.AmlL || adaptTranslation }
            ?.let(converter::transliterate)
            ?: translation,
        adaptTranslation = false,
        syllables = syllables.map { it.copy(text = converter.transliterate(it.text)) },
        accompaniment = accompaniment.map { vocal ->
            vocal.copy(
                text = converter.transliterate(vocal.text),
                translation = vocal.translation?.let(converter::transliterate),
                syllables = vocal.syllables.map { it.copy(text = converter.transliterate(it.text)) },
            )
        },
    )
}
