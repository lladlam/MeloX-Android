package com.lladlam.melox.core.network

sealed interface NeteaseClipboardTarget {
    val id: Long
    data class Song(override val id: Long) : NeteaseClipboardTarget
    data class Playlist(override val id: Long) : NeteaseClipboardTarget
}

object NeteaseClipboardLink {
    private val song = Regex("(?:song(?:/|\\?id=)|songId=)(\\d+)", RegexOption.IGNORE_CASE)
    private val playlist = Regex("(?:playlist(?:/|\\?id=)|playlistId=)(\\d+)", RegexOption.IGNORE_CASE)

    fun parse(text: String): NeteaseClipboardTarget? {
        if (!text.contains("163.com", ignoreCase = true) && !text.contains("music.163", ignoreCase = true)) return null
        song.find(text)?.groupValues?.getOrNull(1)?.toLongOrNull()?.let { return NeteaseClipboardTarget.Song(it) }
        playlist.find(text)?.groupValues?.getOrNull(1)?.toLongOrNull()?.let { return NeteaseClipboardTarget.Playlist(it) }
        return null
    }
}
