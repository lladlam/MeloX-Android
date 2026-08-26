package com.lladlam.melox.core.provider.kuwo

/**
 * Kuwo's legacy mobile search endpoint (`search.kuwo.cn/r.s`) returns data in a
 * single-quoted, JSON-like shape. This normalizer turns those payloads into
 * regular JSON so they can be parsed by [org.json.JSONObject].
 *
 * It is intentionally conservative: it only swaps `'` delimiters for `"`,
 * preserves backslash escapes, and leaves the content untouched. Kuwo escapes
 * literal apostrophes in strings as `&apos;`, so unescaped `'` characters
 * inside string values are not expected in practice.
 */
internal object KuwoJsonNormalizer {
    fun normalize(input: String): String = buildString(input.length) {
        var inString = false
        var escape = false
        for (char in input) {
            when {
                escape -> {
                    append(char)
                    escape = false
                }
                char == '\\' -> {
                    append(char)
                    escape = true
                }
                char == '\'' -> {
                    append('"')
                    inString = !inString
                }
                else -> append(char)
            }
        }
    }
}
