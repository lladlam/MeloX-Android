package com.lladlam.melox.core.lyrics

/** Provider status messages are not lyrics and must not stop cross-source fallback. */
fun LyricsDocument.withoutPlaceholderLyrics(): LyricsDocument {
    val usable = lines.filterNot { line ->
        val normalized = line.text.filter { it.isLetterOrDigit() }.lowercase()
        normalized in lyricPlaceholderTexts
    }
    return if (usable.size == lines.size) this else copy(lines = usable)
}

private val lyricPlaceholderTexts = setOf(
    "此歌曲为没有填词的纯音乐请您欣赏",
    "此歌曲为没有填词的纯音乐请欣赏",
    "此歌曲為沒有填詞的純音樂請您欣賞",
    "此歌曲為沒有填詞的純音樂請欣賞",
    "此歌曲为dj舞曲请您欣赏",
    "此歌曲为没有填词的dj舞曲请您欣赏",
    "纯音乐请欣赏", "纯音乐请您欣赏", "纯音乐",
    "純音樂請欣賞", "純音樂請您欣賞", "純音樂",
    "暂无歌词", "暫無歌詞", "此歌曲暂无歌词", "此歌曲暫無歌詞",
)
