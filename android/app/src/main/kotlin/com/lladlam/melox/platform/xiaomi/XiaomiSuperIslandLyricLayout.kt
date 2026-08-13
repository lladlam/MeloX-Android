package com.lladlam.melox.platform.xiaomi

/**
 * Fits lyrics into HyperOS Super Island text slots using approximate visual width.
 * CJK/kana/hangul glyphs consume roughly twice the width of latin glyphs.
 *
 * Layout strategy adapted for MeloX from Halcyon's Apache-2.0 Super Island implementation.
 */
internal object XiaomiSuperIslandLyricLayout {
    data class Split(val left: String, val right: String)

    fun visualWeight(text: String): Int = text.sumOf(::charWeight)

    fun weightForCharacters(characters: Int): Int = characters.coerceAtLeast(1) * 2

    fun takeByWeight(text: String, maxWeight: Int): String {
        if (text.isBlank() || maxWeight <= 0) return ""
        var weight = 0
        val out = StringBuilder()
        for (char in text.trim()) {
            val next = charWeight(char)
            if (out.isNotEmpty() && weight + next > maxWeight) break
            out.append(char)
            weight += next
        }
        return out.toString().trim()
    }

    fun splitFullLyric(
        text: String,
        leftMaxWeight: Int = weightForCharacters(11),
        rightMaxWeight: Int = weightForCharacters(16),
    ): Split {
        val normalized = text.trim().replace(Regex("\\s+"), " ")
        if (normalized.isBlank()) return Split("♪", "")
        val left = takeByWeight(normalized, leftMaxWeight)
        val remaining = normalized.removePrefix(left).trimStart()
        val right = takeByWeight(remaining, rightMaxWeight)
        return Split(left.ifBlank { "♪" }, right)
    }

    private fun charWeight(char: Char): Int = when {
        char.isWhitespace() -> 0
        char.code in 0x2E80..0x9FFF -> 2
        char.code in 0x3040..0x30FF -> 2
        char.code in 0xAC00..0xD7AF -> 2
        char.code in 0xF900..0xFAFF -> 2
        else -> 1
    }
}
