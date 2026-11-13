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

/**
 * 歌词语言偏好：既决定**挑哪条翻译轨**，也决定是否需要中文字形转换。
 *
 * - 中文界面：`languageTag = null`，按 [script] 选 hans/hant（原有逻辑）。
 * - 其它语言（en/tr/ja...）：`languageTag` = 系统语言，优先选同名 `xml:lang` 的翻译轨，
 *   没有匹配的译文时保留原文，不以其它语言冒充目标语言。
 */
data class MeloXLyricPreference(
    val languageTag: String?,
    val script: MeloXLyricScript,
) {
    fun cacheKey(): String = "${languageTag ?: "zh"}:${script.name}"

    companion object {
        /** 设置项取值：`system` / `zh-Hans` / `zh-Hant` / 语言标签（en、tr…）。 */
        fun fromSetting(value: String, locale: Locale = Locale.getDefault()): MeloXLyricPreference =
            when (val tag = value.trim()) {
                "", "system" -> system(locale)
                "zh-Hans", "zh-CN", "zh-SG" -> MeloXLyricPreference(null, MeloXLyricScript.Simplified)
                "zh-Hant", "zh-TW", "zh-HK", "zh-MO" -> MeloXLyricPreference(null, MeloXLyricScript.Traditional)
                else -> {
                    // 只按**基础语言**匹配（en-US 仍匹配 en 轨），无法识别的取值回落系统语言。
                    val language = tag.lowercase(Locale.ROOT).substringBefore('-')
                    if (language == "zh") {
                        system(locale)
                    } else if (language.length in 2..3 && language.all { it in 'a'..'z' }) {
                        MeloXLyricPreference(language, MeloXLyricScript.Original)
                    } else {
                        system(locale)
                    }
                }
            }

        fun system(locale: Locale = Locale.getDefault()): MeloXLyricPreference {
            val language = locale.language.lowercase(Locale.ROOT)
            if (language == "zh") return MeloXLyricPreference(null, MeloXLyricScript.fromSystem(locale))
            if (language.isBlank() || language == "und") return MeloXLyricPreference(null, MeloXLyricScript.Original)
            return MeloXLyricPreference(language, MeloXLyricScript.Original)
        }
    }
}

object MeloXLyricScriptConverter {
    private val toSimplified by lazy {
        if (Build.VERSION.SDK_INT >= 29) {
            runCatching { Transliterator.getInstance("Traditional-Simplified") }.getOrNull()
        } else {
            null
        }
    }
    private val toTraditional by lazy {
        if (Build.VERSION.SDK_INT >= 29) {
            runCatching { Transliterator.getInstance("Simplified-Traditional") }.getOrNull()
        } else {
            null
        }
    }

    fun convertText(value: String, script: MeloXLyricScript): String {
        if (script == MeloXLyricScript.Original || Build.VERSION.SDK_INT < 29) return value
        return transliterate(value, script)
    }

    fun convert(document: LyricsDocument, script: MeloXLyricScript): LyricsDocument {
        if (script == MeloXLyricScript.Original || Build.VERSION.SDK_INT < 29) return document
        return document.copy(lines = document.lines.map { it.convert(script, document.source) })
    }

    @androidx.annotation.RequiresApi(29)
    private fun transliterate(value: String, script: MeloXLyricScript): String {
        val converter = if (script == MeloXLyricScript.Simplified) toSimplified else toTraditional
        return converter?.transliterate(value) ?: value
    }

    @androidx.annotation.RequiresApi(29)
    private fun LyricLine.convert(script: MeloXLyricScript, source: LyricSource): LyricLine = copy(
        text = transliterate(text, script),
        translation = translation
            ?.takeIf { source != LyricSource.AmlL || adaptTranslation }
            ?.let { transliterate(it, script) }
            ?: translation,
        adaptTranslation = false,
        syllables = syllables.map { it.copy(text = transliterate(it.text, script)) },
        accompaniment = accompaniment.map { vocal ->
            vocal.copy(
                text = transliterate(vocal.text, script),
                translation = vocal.translation?.let { transliterate(it, script) },
                syllables = vocal.syllables.map { it.copy(text = transliterate(it.text, script)) },
            )
        },
    )
}
